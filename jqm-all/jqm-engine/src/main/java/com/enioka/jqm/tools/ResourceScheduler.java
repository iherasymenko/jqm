package com.enioka.jqm.tools;

import com.enioka.jqm.model.JobInstance;

/**
 * A resource scheduler is responsible for the queuing algorithm.
 */
interface ResourceScheduler extends Runnable, DefaultResourceSchedulerMBean
{
    /**
     * Called when a payload thread has ended. This also notifies the poller to poll once again.
     */
    void releaseResources(JobInstance ji);

    /**
     * Called just before the thread needs to be restarted.
     */
    void reset();

    /**
     * Send a stop request to the scheduler. Only returns <when the scheduler is down.
     */
    void stop();
}
