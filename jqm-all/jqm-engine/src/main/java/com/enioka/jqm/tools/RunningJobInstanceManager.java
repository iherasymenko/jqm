package com.enioka.jqm.tools;

import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.enioka.jqm.model.Instruction;
import com.enioka.jqm.model.JobInstance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for creating and storing references to the the {@link RunningJobInstance}.
 */
class RunningJobInstanceManager implements RunningJobInstanceManagerMBean
{
    private Logger jqmlogger = LoggerFactory.getLogger(RunningJobInstanceManager.class);

    // JMX
    private ObjectName name = null;

    // Actual registries
    private ConcurrentHashMap<RunningJobInstance, RjiRegistration> instancesByTracker = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, RjiRegistration> instancesById = new ConcurrentHashMap<>();

    // Counts
    private Map<Integer, Date> peremption = new ConcurrentHashMap<>();
    private AtomicInteger actualNbRunning = new AtomicInteger(0);
    private AtomicLong started = new AtomicLong(0);

    private class RjiRegistration
    {
        RunningJobInstance rji;
        ResourceScheduler qp;
        JobInstance ji;
    }

    ///////////////////////////////////////////////////////////////////////////
    // JI management methods
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Main method of this class: actually start the new thread for a JI. All the starting plumbing is inside {@link RunningJobInstance} -
     * this class is merely tracking all running job instances.
     *
     * @param ji
     * @param qp
     * @param engine
     */
    void startNewJobInstance(JobInstance ji, ResourceScheduler qp, JqmEngine engine)
    {
        RjiRegistration reg = new RjiRegistration();

        reg.ji = ji;
        reg.qp = qp;
        reg.rji = new RunningJobInstance(ji, engine);

        instancesByTracker.put(reg.rji, reg);
        instancesById.put(reg.ji.getId(), reg);

        actualNbRunning.incrementAndGet();
        started.incrementAndGet();
        if (ji.getJD().getMaxTimeRunning() != null)
        {
            this.peremption.put(ji.getId(), new Date((new Date()).getTime() + ji.getJD().getMaxTimeRunning() * 60 * 1000));
        }

        (new Thread(reg.rji)).start();
    }

    /**
     * This method must be called whenever a {@link RunningJobInstance} ends (whatever its ending status) to allow this manager to update
     * the status of said job instance.
     *
     * @param rji
     */
    void signalEndOfRun(RunningJobInstance rji)
    {
        if (!instancesByTracker.containsKey(rji))
        {
            jqmlogger.warn("Tried to signal the end of a job instance which was not registered inside the manager");
            return;
        }

        RjiRegistration reg = instancesByTracker.get(rji);

        // Remove from manager
        instancesByTracker.remove(rji);
        instancesById.remove(reg.ji.getId());

        actualNbRunning.decrementAndGet();
        this.peremption.remove(rji.getId());

        // Signal queue poller.
        if (reg.qp != null)
        {
            reg.qp.releaseResources(reg.ji);
        }
    }

    /**
     * Send an instruction to a specific job instance.
     *
     * @param jobInstanceId
     * @param instruction
     */
    void handleInstruction(int jobInstanceId, Instruction instruction)
    {
        if (!instancesById.containsKey(jobInstanceId))
        {
            jqmlogger.warn("Tried to send an instruction to an instance which was not registered inside the manager");
            return;
        }

        RjiRegistration reg = instancesById.get(jobInstanceId);
        reg.rji.handleInstruction(instruction);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Destruction methods
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Send a kill signal to all running instances and return as soon as the signal is sent.
     */
    void killAll()
    {
        for (RjiRegistration reg : this.instancesById.values().toArray(new RjiRegistration[] {}))
        {
            reg.rji.handleInstruction(Instruction.KILL);
        }
    }

    /**
     * Helper method which only returns when a timeout is reached or when all running job instances registered in this manager complete.
     *
     * @param timeOutMs
     *                      timeout in milliseconds.
     */
    void waitForAllThreads(long timeOutMs)
    {
        long timeWaitedMs = 0;
        long stepMs = 1000;
        while (timeWaitedMs <= timeOutMs)
        {
            jqmlogger.trace("Waiting the end of {} job(s)", actualNbRunning);

            if (actualNbRunning.get() <= 0)
            {
                break;
            }
            if (timeWaitedMs == 0)
            {
                jqmlogger.info("Waiting for the end of {} jobs on all schedulers - timeout is {} ms", actualNbRunning, timeOutMs);
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
            jqmlogger.warn("Some job instances did not finish in time - they will be killed for the engine to be able to stop");
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // JMX
    ///////////////////////////////////////////////////////////////////////////

    void registerMBean(JqmEngine engine)
    {
        if (!engine.loadJmxBeans)
        {
            return;
        }

        try
        {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            name = new ObjectName("com.enioka.jqm:type=Node.JobInstanceManager,Node=" + engine.getNode().getName() + ",name=Default");

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
        catch (Exception e)
        {
            throw new JqmInitError("Could not create JMX beans", e);
        }
    }

    void unregisterMBean()
    {
        if (name != null)
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
    }

    @Override
    public long getCurrentlyRunningJobCount()
    {
        return this.actualNbRunning.get();
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

    @Override
    public long getCumulativeJobInstancesCount()
    {
        return started.get();
    }
}
