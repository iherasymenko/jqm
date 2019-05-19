package com.enioka.jqm.tools;

import com.enioka.jqm.model.JobInstance;

public interface IScheduler extends Runnable
{
    /**
     * Called when a payload thread has ended. This also notifies the poller to poll once again.
     */
    void releaseResources(JobInstance ji);

    /**
     * Called just before the thread needs to be restarted.
     */
    void reset();
}
