package com.enioka.jqm.tools;

/**
 * JMX bean for management of a {@link RunningJobInstanceManager}.
 */
public interface RunningJobInstanceManagerMBean
{
    /**
     * How many currently running job instances are managed by this manager.
     */
    public long getCurrentlyRunningJobCount();

    /**
     * The total count of job instances which have run for longer than what was expected (as specified in the job definition, field
     * maxTimeRunning).
     */
    public int getLateJobs();

    /**
     * The total number of job instances that were run by this node since it started.
     */
    long getCumulativeJobInstancesCount();
}
