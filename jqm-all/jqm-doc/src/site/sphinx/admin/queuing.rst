.. versionadded:: 3.0.0
   Before v3, there were no resource managers.


Advanced queuing
######################


How queues work
******************

JQM queues are basically FIFO queues: they are ordered by age, and JQM nodes take the head (the oldest job instances) from the queue first.
However, that's not all JQM is able to do.

Priority
++++++++++++

A first tweak to this is priority: job instances can have a priority attached to them. Priority is an integer between 1 to 10.
A job with a higher priority (closer to 10) will *always start before job instances of lower priorities*.
Basically, the queue is ordered first by priority, then by age.

Priorities can be changed through the client API. Depending on the type of job definition, they may also translate into technical characteristics
such as a thread priority for Java payloads.

Care should be taken when using these static priorities, as they may result in the starvation of lower priority job instances. This is by design,
so only use this mechanism when a "hard priority" is desired.

Resources
++++++++++++++

A second tweak is resources available to the poller. A pure FIFO queue would only start one job instance at the same time, wait for its end, rinse and repeat.
This may be useful in many situations, but most often the administrator will want to run a specified count of job instances in parallel.
Therefore we introduce the notion of resource: a job instance may need some resource in order to run.

The most common resource type is thread/processes. If we say there are 3 threads available for polling a given queue on a given node,
the node will have at a given time at most 3 running job instances. Limiting the thread resource is the default inside JQM, with
a thread count given inside the Deployment Parameter (see :doc:`queues`).

However, multiple types of resources exist. JQM therefore defines the notion of **resource managers**. A resource manager is responsible for
a single resource type: threads, ports available, memory available... On each loop, a queue poller will ask the different enabled resource
manager if the job instances it wants to launch are compatible with their resource constraints. The resource manager will answer either
"Go for it", "I'm empty" and "Not enough resource, but I'm not empty yet".

In the last case, JQM will skip the job instance and analyse the next one. This is another non-FIFO behaviour with JQM.


Using resource managers
******************************

.. warning:: in the current JQM release, resource managers are hard coded! There are only three: one thread count per deployment parameter named *thread*, one
    highlander manager per deployment parameter named *highlander*, and one item list manager per node named after a *global parameter*. Future releases will allow to specify as many managers as
    desired and fully specify their parameters.

Parameters for resource managers come from multiple sources, listed here in order of ascending priority:

* a hard-coded default
* a value given as a parameter to the resource manager itself
* a value given in the Job Definition
* a value given in the Job Instance

Each resource manager configuration is identified by a **key**.

Not all parameters can be used in job definition and job instances - the tables below show which parameters can be (column "JI").

Parameters in job definition and job instance must specify which configuration they are targeting as there can be multiple instances of the
same type of resource manager. Therefore, when the parameter
name is `com.enioka.jqm.rm.quantity` and the configuration is named `thread`, the parameter key must be `com.enioka.jqm.rm.thread.quantity`.


Available resource managers
********************************

Quantity resource manager
+++++++++++++++++++++++++++

A Resource Manager which handles a set quantity of a given resource. By default, each run requires one unit of that resource. Blocks once
the resource is exhausted.

This RM has no persistence - resources are counted in memory, and considered free on node startup.

By default it has 10 units and each JI consumes one unit.

It uses a very simple resource model, but can still model things like a thread count, a memory share... It should basically be used every time
a resource can be sliced and must be shared.


+------------------------------------------------------------+----------------------------------------------------------------------------------------------+
| **Identity**                                               |                                                                                              |
+------------------------------------------------------------+----------------------------------------------------------------------------------------------+
| Class name                                                 | com.enioka.jqm.tools.QuantityResourceManager                                                 |
+------------------------------------------------------------+----------------------------------------------------------------------------------------------+
+------------------------------------------------------------+----------------------------------------------------------------+--------+--------+-----------+
| **Parameters**                                             |                                                                | **RM** | **JI** | Default   |
+------------------------------------------------------------+----------------------------------------------------------------+--------+--------+-----------+
| com.enioka.jqm.rm.quantity.quantity                        | The amount of resource available at startup                    | X      |        | 10        |
+------------------------------------------------------------+----------------------------------------------------------------+--------+--------+-----------+
| com.enioka.jqm.rm.quantity.consumption                     | The amount of resource taken by a job instance                 | X      | X      | 1         |
+------------------------------------------------------------+----------------------------------------------------------------+--------+--------+-----------+

.. warning:: in the current release, the default com.enioka.jqm.rm.quantity.quantity comes from the deployment parameter thread count.

Discrete resource manager
+++++++++++++++++++++++++++

A Resource Manager in which the resource is a list of items without order. Each job instance may take 0 to n items. Items are attributed nominaly -
they are named. An environment variable of all attributed items is made available to the job instance for when it runs.

Blocks once the resource is exhausted.

This RM has no persistence - resources are counted in memory, and considered free on startup.

By default it has 10 items named item01 to item10, and JI do NOT use any of them - only job instances with the right parameter will take an item.

It is another very simple resource model with countless applications. This one is best suited when resources actually cannot be shared and must be used by
at most one job instance at the same time.


+------------------------------------------------------------+----------------------------------------------------------------------------------------------+
| **Identity**                                               |                                                                                              |
+------------------------------------------------------------+----------------------------------------------------------------------------------------------+
| Class name                                                 | com.enioka.jqm.tools.DiscreteResourceManager                                                 |
+------------------------------------------------------------+----------------------------------------------------------------------------------------------+
+------------------------------------------------------------+----------------------------------------------------------------+--------+--------+-----------+
| **Parameters**                                             |                                                                | **RM** | **JI** | Default   |
+------------------------------------------------------------+----------------------------------------------------------------+--------+--------+-----------+
| com.enioka.jqm.rm.discrete.list                            | The comma-separated list of items                              | X      |        | yes       |
+------------------------------------------------------------+----------------------------------------------------------------+--------+--------+-----------+
| com.enioka.jqm.rm.discrete.consumption                     | The number of items taken by a job instance                    | X      | X      | 0         |
+------------------------------------------------------------+----------------------------------------------------------------+--------+--------+-----------+
+------------------------------------------------------------+----------------------------------------------------------------------------------------------+
| **Resulting environment variables**                        |                                                                                              |
+------------------------------------------------------------+----------------------------------------------------------------------------------------------+
| JQM_RM_DISCRETE_{configuration key}_ITEMS                  | A comma-separated list of booked items                                                       |
+------------------------------------------------------------+----------------------------------------------------------------------------------------------+

.. warning:: in the current release, the com.enioka.jqm.rm.discrete.list comes from the global parameter named `discreteRmList`. The key of the manager comes
    from the global parameter `discreteRmName`.

Highlander resource manager
+++++++++++++++++++++++++++++++

This applies the highlander rules. As it has no parameters, it is not detailed further.


The default resource scheduler
***********************************

Allocating resources is a complex problem. The reader is invited to read `the wikipedia page on the subject <https://en.wikipedia.org/wiki/Scheduling_(computing)>`_ to convince themselves if needed.
The important part is that compromises must be made between different goals. In our case, JQM aims to give by default a sensible and easy to understand experience more than aiming at extreme optimizations.

Therefore, in JQM the default scheduler sets itself the following goals, in order of decreasing priority:

* fairness (understood as each job request getting appropriate resources)
* wait time (a job request should wait as little as possible in queue)
* throughput (the resources should be used as much as possible)



The default scheduler works this way: every second (by default, this is a parameter), the scheduler *polls* all the queues affected to the local node.
At the beginning of a poll, the scheduler takes the *head* of each queue. This means the **highest priority, then the oldest** job requests waiting in all the queues.
It takes more job requests than it can possibly swallow to take into account the fact that other JQM nodes may 'steal' the job requests while it is processing them.

The queues are sorted according to the time waited in queue by their "first" (the head of the head) job request. The queue with the job request having waited for the longest
becomes the highest priority queue, etc. (note that a frozen job request is still waiting in a queue, and therefore this counts for its time waited in queue).

Then, the first *scheduling tour* starts.

For each queue, according to the order set above, the scheduler takes the first job request and interrogates all resource managers associated to the queue (without order). They either answer:

* I have enough resources for this job request
* I do not have the resources for this job request

If there are not enough resources, the scheduler is done with this queue for the rest of the poll - it won't consider anymore job requests from this queue until the next poll.
Also, the resource manager will be considered full for the rest of the poll - even if it still has a few resources free. That way, the scheduler actually waits for the resource
to free up for the oldest waiting job request (at the cost of not using all the resources of the server all the time).

If there are enough resources inside all resource managers, the scheduler sets the job request for actual execution. It then goes on to the first job request of the next queue.
The scheduling tour ends when the first job request of the last queue has been analyzed.

If all the different resource managers of at least one queue report at the end of the scheduling tour that they still have free room, another tour is launched. Otherwise, the poll ends.
