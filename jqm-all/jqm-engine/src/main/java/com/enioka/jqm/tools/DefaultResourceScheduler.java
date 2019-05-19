package com.enioka.jqm.tools;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.enioka.admin.MetaService;
import com.enioka.api.admin.ResourceManagerDto;
import com.enioka.api.admin.ResourceManagerNodeMappingDto;
import com.enioka.api.admin.ResourceManagerPollerMappingDto;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.jdbc.QueryResult;
import com.enioka.jqm.model.DeploymentParameter;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.JobInstance;
import com.enioka.jqm.model.Node;
import com.enioka.jqm.model.Queue;
import com.enioka.jqm.model.State;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default scheduler aims first at fairness, then wait time, and only then throughput. It is fully described inside the documentation.
 */
class DefaultResourceScheduler implements Runnable, IScheduler, DefaultResourceSchedulerMBean
{
    private static Logger jqmlogger = LoggerFactory.getLogger(DefaultResourceScheduler.class);

    /////////////////////////////////////////
    // Helper context classes
    private class ResourceManagerUsage
    {
        ResourceManagerBase resourceManagerInstance;
        ResourceManagerDto resourceManagerConfiguration;
        Map<String, String> contextualParameters; // TODO: use this in meta parameters before launch.
        Queue queue;
    }

    private class PollingContext
    {
        List<QueuePollingContext> queues = new ArrayList<>();
        HashSet<ResourceManagerBase> failedResourceManagers = new HashSet<>();
        HashSet<QueuePollingContext> failedQueues = new HashSet<>();
    }

    private class QueuePollingContext
    {
        DeploymentParameter poller;
        List<ResourceManagerUsage> rms;
        List<JobInstance> newInstances;
        long msHeadWaited = 0;
    }

    /////////////////////////////////////////
    // Fields

    // Transactional data
    private Map<Integer, Date> peremption = new ConcurrentHashMap<>();
    private AtomicInteger actualNbThread = new AtomicInteger(0);
    private boolean strictPollingPeriod = false;

    // Queue & resource configuration
    private Map<DeploymentParameter, List<ResourceManagerUsage>> rmPerDp = new HashMap<>();
    private Map<DeploymentParameter, Queue> queuePerDp = new HashMap<>();
    private HashSet<ResourceManagerBase> usedResourceManagers = new HashSet<>();

    // Other configuration
    private Node localNode;

    // Required JQM services
    private RunningJobInstanceManager runningJobInstanceManager;
    private ResourceManagerManager resourceManagerManager;
    private JqmEngine engine;

    // Thread and loop stuff
    private int pollingInterval;
    private boolean run = true;
    private boolean hasStopped = true;
    private Thread localThread = null;
    private Semaphore loop = new Semaphore(0);
    private Calendar lastLoop = null;

    // JMX
    private ObjectName name = null;

    DefaultResourceScheduler(DbConn cnx, Node localNode, JqmEngine engine, ResourceManagerManager rmm)
    {
        this.localNode = localNode;
        this.engine = engine;
        this.runningJobInstanceManager = engine.getRunningJobInstanceManager();
        this.resourceManagerManager = rmm;

        setUp(cnx, localNode);

        this.logConfiguration();
    }

    private void setUp(DbConn cnx, Node localNode)
    {
        jqmlogger.info("Starting resource and queue scheduler");

        // Global config
        this.strictPollingPeriod = Boolean.parseBoolean(GlobalParameter.getParameter(cnx, "strictPollingPeriod", "false"));
        this.pollingInterval = Integer.parseInt(GlobalParameter.getParameter(cnx, "schedulerPollingPeriodMs", "1000"));

        // Fetch queue/DP configuration from database
        List<DeploymentParameter> localNodePollers = DeploymentParameter.select(cnx, "dp_select_for_node", localNode.getId());
        Map<Integer, DeploymentParameter> localNodePollersById = new HashMap<>();

        List<ResourceManagerDto> allRmConfigurations = MetaService.getResourceManagers(cnx);
        Map<Integer, ResourceManagerDto> allRmConfigurationsById = new HashMap<>(allRmConfigurations.size());
        for (ResourceManagerDto rm : allRmConfigurations)
        {
            allRmConfigurationsById.put(rm.getId(), rm);
        }

        List<ResourceManagerNodeMappingDto> localNodeRmMappings = MetaService.getResourceManagerNodeMappings(cnx, localNode.getId());
        List<ResourceManagerPollerMappingDto> allPollerMappings = MetaService.getResourceManagerPollerMappings(cnx);

        Map<Integer, Queue> queuesById = new HashMap<>();
        for (Queue q : Queue.select(cnx, "q_select_all"))
        {
            queuesById.put(q.getId(), q);
        }

        // For each poller, prepare the required RMs.
        for (DeploymentParameter poller : localNodePollers)
        {
            localNodePollersById.put(poller.getId(), poller);
            queuePerDp.put(poller, queuesById.get(poller.getQueue()));
            List<ResourceManagerUsage> pollerResourceManagers = new ArrayList<>();
            rmPerDp.put(poller, pollerResourceManagers);

            // Add all node-level mappings for all pollers.
            for (ResourceManagerNodeMappingDto rm : localNodeRmMappings)
            {
                ResourceManagerUsage usage = new ResourceManagerUsage();
                usage.contextualParameters = rm.getParameters();
                usage.resourceManagerConfiguration = allRmConfigurationsById.get(rm.getResourceManagerId());
                usage.queue = queuesById.get(poller.getQueue());
                usage.resourceManagerInstance = this.resourceManagerManager.getResourceManager(usage.resourceManagerConfiguration);

                pollerResourceManagers.add(usage);
            }

            // Add poller-level mappings too.
            int dpLevelCount = 0;
            for (ResourceManagerPollerMappingDto mapping : allPollerMappings)
            {
                // Only for mappings about the current poller.
                if (!mapping.getPollerId().equals(poller.getId()))
                {
                    continue;
                }

                ResourceManagerUsage usage = new ResourceManagerUsage();
                usage.contextualParameters = mapping.getParameters();
                usage.resourceManagerConfiguration = allRmConfigurationsById.get(mapping.getResourceManagerId());
                usage.queue = queuesById.get(poller.getQueue());
                usage.resourceManagerInstance = this.resourceManagerManager.getResourceManager(usage.resourceManagerConfiguration);

                pollerResourceManagers.add(usage);
                dpLevelCount++;
            }

            // For ascending compatibility and easy configuration, add a FIFO single thread limitation if no RM specified at all.
            if (dpLevelCount == 0)
            {
                Queue q = queuesById.get(poller.getQueue());
                jqmlogger.info("No resource manager is specified for queue {} on node {} - single-thread FIFO queue is assumed",
                        q.getName(), localNode.getName());

                ResourceManagerDto transientRm = new ResourceManagerDto();
                transientRm.setDescription("Thread limiter automatically created for queue " + q.getName());
                transientRm.setImplementation("com.enioka.jqm.tools.QuantityResourceManager");
                transientRm.addParameter("com.enioka.jqm.rm.quantity.quantity", "1");
                transientRm.setId(poller.getId());

                ResourceManagerUsage usage = new ResourceManagerUsage();
                usage.resourceManagerConfiguration = transientRm;
                usage.queue = q;
                usage.resourceManagerInstance = this.resourceManagerManager.getResourceManager(usage.resourceManagerConfiguration);

                pollerResourceManagers.add(usage);
            }
        }

        // Check configuration and cache used resource managers
        for (DeploymentParameter poller : localNodePollers)
        {
            if (rmPerDp.get(poller).isEmpty())
            {
                throw new JqmInitError("Invalid configuration: a poller has no resource manager");
            }

            for (ResourceManagerUsage usage : rmPerDp.get(poller))
            {
                usedResourceManagers.add(usage.resourceManagerInstance);
            }
        }
    }

    private void logConfiguration()
    {
        for (Map.Entry<DeploymentParameter, List<ResourceManagerUsage>> e : rmPerDp.entrySet())
        {
            jqmlogger.info("\tScheduler will poll queue {}, with {} resource managers", queuePerDp.get(e.getKey()).getName(),
                    e.getValue().size());

            for (ResourceManagerUsage rmu : e.getValue())
            {
                jqmlogger.info("\t\t{} ({}) - {}", rmu.resourceManagerConfiguration.getDescription(),
                        rmu.resourceManagerConfiguration.getKey(), rmu.resourceManagerInstance.getClass());
                for (Map.Entry<String, String> prm : rmu.resourceManagerConfiguration.getParameters().entrySet())
                {
                    jqmlogger.info("\t\t\t{} - {}", prm.getKey(), prm.getValue());
                }
            }
        }

    }

    private int potentialFreeRoom(List<ResourceManagerUsage> resourceManagers)
    {
        int room = Integer.MAX_VALUE;
        for (ResourceManagerUsage rm : resourceManagers)
        {
            room = Math.min(room, rm.resourceManagerInstance.getSlotsAvailable());
        }
        return room;
    }

    private void poll(DbConn cnx)
    {
        Calendar now = Calendar.getInstance(); // For relative comparisons only, so we do not care about TZ, being exact and the like.
        PollingContext pollingContext = new PollingContext();

        // 1: get the head of all queues associated to this scheduler.
        for (DeploymentParameter poller : rmPerDp.keySet())
        {
            List<ResourceManagerUsage> resourceManagers = rmPerDp.get(poller);
            QueuePollingContext ctx = new QueuePollingContext();

            // free * 3 because we may reject quite a few JI inside resource managers.
            int freeRoom = potentialFreeRoom(resourceManagers);
            ctx.newInstances = cnx.poll(queuePerDp.get(poller), freeRoom > 100000 ? Integer.MAX_VALUE : freeRoom * 3);
            ctx.poller = poller;
            ctx.rms = resourceManagers;

            if (!ctx.newInstances.isEmpty())
            {
                ctx.msHeadWaited = now.getTimeInMillis() - ctx.newInstances.get(0).getCreationDate().getTimeInMillis();
                pollingContext.queues.add(ctx);
            }
        }

        // 2: sort by old age.
        pollingContext.queues.sort(new Comparator<QueuePollingContext>()
        {
            @Override
            public int compare(QueuePollingContext o1, QueuePollingContext o2)
            {
                if (o1.msHeadWaited == o2.msHeadWaited)
                {
                    return 0;
                }
                return o1.msHeadWaited > o2.msHeadWaited ? 1 : -1;
            }
        });

        // 3: go for tours.
        int tour = 0;
        while (pollingContext.failedQueues.size() != pollingContext.queues.size()
                && pollingContext.failedResourceManagers.size() != this.usedResourceManagers.size())
        {
            doSchedulingTour(cnx, tour++, pollingContext);
        }
    }

    /**
     * Scheduling tours are responsible for taking a single JI from each queue and see if there are enough resources to launch them.
     *
     * @param tour
     * @param pollingContext
     */
    private void doSchedulingTour(DbConn cnx, int tour, PollingContext pollingContext)
    {
        JobInstance ji;

        queueLoop: for (QueuePollingContext queue : pollingContext.queues)
        {
            if (pollingContext.failedQueues.contains(queue))
            {
                continue;
            }
            if (tour >= queue.newInstances.size())
            {
                pollingContext.failedQueues.add(queue);
                continue;
            }

            ji = queue.newInstances.get(tour);
            ji.loadPrmCache(cnx);

            // Check if we have the resources needed to run this JI
            List<ResourceManagerBase> alreadyReserved = new ArrayList<>(queue.rms.size());
            for (ResourceManagerUsage resourceManagerUsage : queue.rms)
            {
                ResourceManagerBase rm = resourceManagerUsage.resourceManagerInstance;

                // No need to check a failed RM.
                if (pollingContext.failedResourceManagers.contains(rm))
                {
                    pollingContext.failedQueues.add(queue);
                    continue queueLoop;
                }

                switch (rm.bookResource(ji, cnx))
                {
                case BOOKED:
                    // OK, nothing to do.
                    alreadyReserved.add(rm);
                    break;
                case EXHAUSTED: // no difference between exhausted and failed in this scheduler.
                case FAILED:
                    // Stop the loop - cannot do anything anymore with these resources.
                    jqmlogger.trace("Scheduler has a full or near-full RM");
                    pollingContext.failedResourceManagers.add(rm);
                    pollingContext.failedQueues.add(queue);
                    for (ResourceManagerBase reservedRm : alreadyReserved)
                    {
                        reservedRm.releaseResource(ji);
                    }
                    break queueLoop;
                }
            }

            // If here (no break on queueLoop), all RMs are GO.
            this.launch(cnx, ji);
        }
    }

    private void launch(DbConn cnx, JobInstance ji)
    {
        actualNbThread.incrementAndGet();

        // Actually set it for running on this node and report it on the in-memory object.
        QueryResult qr = cnx.runUpdate("ji_update_status_by_id", localNode.getId(), ji.getId());
        if (qr.nbUpdated != 1)
        {
            // Means the JI was taken by another node, so simply continue.
            releaseResources(ji);
            return;
        }
        ji.setNode(localNode);
        ji.setState(State.ATTRIBUTED);

        // Commit taking possession of the JI (as well as anything which may have been done inside the RMs)
        cnx.commit();

        // We will run this JI!
        jqmlogger.trace("JI number {} will be run by this scheduler this loop", ji.getId());
        if (ji.getJD().getMaxTimeRunning() != null)
        {
            this.peremption.put(ji.getId(), new Date((new Date()).getTime() + ji.getJD().getMaxTimeRunning() * 60 * 1000));
        }

        // Run it
        if (!ji.getJD().isExternal())
        {
            this.runningJobInstanceManager.startNewJobInstance(ji, this, this.engine);
        }
        else
        {
            (new Thread(new RunningExternalJobInstance(cnx, ji, this))).start();
        }
    }

    /**
     * Called when a payload thread has ended. This also notifies the poller to poll once again.
     */
    @Override
    public void releaseResources(JobInstance ji)
    {
        this.peremption.remove(ji.getId());
        this.actualNbThread.decrementAndGet();

        for (ResourceManagerBase rm : this.usedResourceManagers)
        {
            rm.releaseResource(ji);
        }

        if (!this.strictPollingPeriod)
        {
            // Force a new loop at once. This makes queues more fluid.
            loop.release(1);
        }
        this.engine.signalEndOfRun();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Thread stuff
    ///////////////////////////////////////////////////////////////////////////

    public void stop()
    {
        jqmlogger.info("Scheduler has received a stop order");
        run = false;
        loop.release();
    }

    /**
     * Will make the thread ready to run once again after it has stopped.
     */
    @Override
    public void reset()
    {
        if (!hasStopped)
        {
            throw new IllegalStateException("cannot reset a non stopped queue poller");
        }
        hasStopped = false;
        run = true;
        lastLoop = null;
        loop = new Semaphore(0);
    }

    @Override
    public synchronized void run() // sync: avoid race condition on run when restarting after failure.
    {
        this.localThread = Thread.currentThread();
        this.localThread.setName("SCHEDULER;polling;");
        DbConn cnx = null;

        registerMBean();

        while (true)
        {
            lastLoop = Calendar.getInstance();
            jqmlogger.trace("poller loop");

            try
            {
                cnx = Helpers.getNewDbSession();
                poll(cnx);
            }
            catch (RuntimeException e)
            {
                if (!run)
                {
                    break;
                }
                if (Helpers.testDbFailure(e))
                {
                    jqmlogger.error("connection to database lost - stopping scheduler");
                    jqmlogger.trace("connection error was:", e.getCause());
                    this.hasStopped = true;
                    this.engine.schedulerRestartNeeded(this);
                    break;
                }
                else
                {
                    jqmlogger.error("Scheduler has failed! It will stop.", e);
                    this.run = false;
                    this.hasStopped = true;
                    break;
                }
            }
            catch (Exception e)
            {
                jqmlogger.error("Scheduler has failed! It will stop.", e);
                this.run = false;
                this.hasStopped = true;
                break;
            }
            finally
            {
                // Reset the connection on each loop.
                if (Thread.interrupted()) // always clear interrupted status before doing DB operations.
                {
                    run = false;
                }
                Helpers.closeQuietly(cnx);
            }

            // Wait according to the deploymentParameter
            try
            {
                loop.tryAcquire(this.pollingInterval, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e)
            {
                run = false;
                break;
            }

            // Exit if asked to
            if (!run)
            {
                break;
            }
        }

        if (!run)
        {
            // Run is true only if the loop has exited abnormally, in which case the engine should try to restart the poller
            // So only do the graceful shutdown procedure if normal shutdown.

            jqmlogger.info("Scheduler is stopping [engine " + this.localNode.getName() + "]");
            waitForAllThreads(60L * 1000);

            // JMX
            if (this.engine.loadJmxBeans)
            {
                try
                {
                    ManagementFactory.getPlatformMBeanServer().unregisterMBean(name);
                }
                catch (Exception e)
                {
                    jqmlogger.error("Could not unregister JMX beans", e);
                }
            }

            // Let the engine decide if it should stop completely
            this.hasStopped = true; // BEFORE check
            jqmlogger.info("Scheduler has ended normally");
            this.engine.checkEngineEnd();
        }
        else
        {
            // else => Abnormal stop (DB failure only). Set booleans to reflect this.
            jqmlogger.error("Scheduler has ended abnormally");
            this.run = false;
            this.hasStopped = true;
            // Do not check for engine end - we do not want to shut down the engine on a poller failure.
        }
        localThread = null;
    }

    private void waitForAllThreads(long timeOutMs)
    {
        long timeWaitedMs = 0;
        long stepMs = 1000;
        while (timeWaitedMs <= timeOutMs)
        {
            jqmlogger.trace("Waiting the end of {} job(s)", actualNbThread);

            if (actualNbThread.get() <= 0)
            {
                break;
            }
            if (timeWaitedMs == 0)
            {
                jqmlogger.info("Waiting for the end of {} jobs on scheduler - timeout is {} ms", actualNbThread, timeOutMs);
            }
            try
            {
                Thread.sleep(stepMs);
            }
            catch (InterruptedException e)
            {
                // Interruption => stop right now
                jqmlogger.warn("Some job instances did not finish in time - wait was interrupted");
                Thread.currentThread().interrupt();
                return;
            }
            timeWaitedMs += stepMs;
        }
        if (timeWaitedMs > timeOutMs)
        {
            jqmlogger.warn("Some job instances did not finish in time - they will be killed for the scheduler to be able to stop");
        }
    }

    ////////////////////////////////////////////////////////////
    // JMX
    ////////////////////////////////////////////////////////////

    private void registerMBean()
    {
        try
        {
            if (this.engine.loadJmxBeans)
            {
                MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                name = new ObjectName("com.enioka.jqm:type=Node.Scheduler,Node=" + this.engine.getNode().getName() + ",name=Default");

                // Unregister MBean if it already exists. This may happen during frequent DP modifications.
                try
                {
                    mbs.getMBeanInfo(name);
                    mbs.unregisterMBean(name);
                }
                catch (InstanceNotFoundException e)
                {
                    // Nothing to do, this should be the normal case.
                }

                mbs.registerMBean(this, name);
            }
        }
        catch (Exception e)
        {
            throw new JqmInitError("Could not create JMX beans", e);
        }
    }

    @Override
    public long getCumulativeJobInstancesCount()
    {
        DbConn em2 = Helpers.getNewDbSession();
        try
        {
            return em2.runSelectSingle("history_select_count_for_node", Long.class, this.localNode.getId());
        }
        finally
        {
            Helpers.closeQuietly(em2);
        }
    }

    @Override
    public float getJobsFinishedPerSecondLastMinute()
    {
        DbConn em2 = Helpers.getNewDbSession();
        try
        {
            return em2.runSelectSingle("history_select_count_last_mn_for_node", Float.class, this.localNode.getId());
        }
        finally
        {
            Helpers.closeQuietly(em2);
        }
    }

    @Override
    public long getCurrentlyRunningJobCount()
    {
        return this.actualNbThread.get();
    }

    @Override
    public Integer getCurrentActiveThreadCount()
    {
        return actualNbThread.get();
    }

    @Override
    public boolean isActuallyPolling()
    {
        // 1000ms is a rough estimate of the time taken to do the actual poll. If it's more, there is a huge issue elsewhere.
        return (Calendar.getInstance().getTimeInMillis() - this.lastLoop.getTimeInMillis()) <= pollingInterval + 1000;
    }

    @Override
    public int getLateJobs()
    {
        int i = 0;
        Date now = new Date();
        for (Date d : this.peremption.values())
        {
            if (now.after(d))
            {
                i++;
            }
        }
        return i;
    }
}
