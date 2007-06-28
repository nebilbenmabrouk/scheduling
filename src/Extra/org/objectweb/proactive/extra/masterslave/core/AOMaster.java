/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed,
 *            Concurrent computing with Security and Mobility
 *
 * Copyright (C) 1997-2007 INRIA/University of Nice-Sophia Antipolis
 * Contact: proactive@objectweb.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://www.inria.fr/oasis/ProActive/contacts.html
 *  Contributor(s):
 *
 * ################################################################
 */
package org.objectweb.proactive.extra.masterslave.core;

import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.objectweb.proactive.ActiveObjectCreationException;
import org.objectweb.proactive.Body;
import org.objectweb.proactive.InitActive;
import org.objectweb.proactive.ProActive;
import org.objectweb.proactive.RunActive;
import org.objectweb.proactive.Service;
import org.objectweb.proactive.core.body.request.Request;
import org.objectweb.proactive.core.body.request.RequestFilter;
import org.objectweb.proactive.core.descriptor.data.VirtualNode;
import org.objectweb.proactive.core.exceptions.NonFunctionalException;
import org.objectweb.proactive.core.exceptions.manager.NFEListener;
import org.objectweb.proactive.core.group.Group;
import org.objectweb.proactive.core.group.ProActiveGroup;
import org.objectweb.proactive.core.mop.ClassNotReifiableException;
import org.objectweb.proactive.core.node.NodeException;
import org.objectweb.proactive.core.util.log.Loggers;
import org.objectweb.proactive.core.util.log.ProActiveLogger;
import org.objectweb.proactive.extra.masterslave.TaskException;
import org.objectweb.proactive.extra.masterslave.interfaces.Master;
import org.objectweb.proactive.extra.masterslave.interfaces.internal.ResultIntern;
import org.objectweb.proactive.extra.masterslave.interfaces.internal.Slave;
import org.objectweb.proactive.extra.masterslave.interfaces.internal.SlaveDeadListener;
import org.objectweb.proactive.extra.masterslave.interfaces.internal.SlaveManager;
import org.objectweb.proactive.extra.masterslave.interfaces.internal.SlaveManagerAdmin;
import org.objectweb.proactive.extra.masterslave.interfaces.internal.SlaveWatcher;
import org.objectweb.proactive.extra.masterslave.interfaces.internal.TaskIntern;
import org.objectweb.proactive.extra.masterslave.interfaces.internal.TaskProvider;
import org.objectweb.proactive.extra.masterslave.interfaces.internal.TaskRepository;
import org.objectweb.proactive.extra.masterslave.util.HashSetQueue;


/**
 * <i><font size="-1" color="#FF0000">**For internal use only** </font></i><br>
 * Main Active Object of the Master/Slave API <br>
 * Literally : the entity to which an user can submit tasks to be solved<br>
 * @author fviale
 */
public class AOMaster implements Serializable, TaskProvider, InitActive,
    RunActive, Master, SlaveDeadListener {
    protected static Logger logger = ProActiveLogger.getLogger(Loggers.MASTERSLAVE);

    // stub on this active object
    protected Object stubOnThis;

    // global variables
    protected boolean terminated; // is the master terminated

    // Slave manager (deploy slaves)
    protected SlaveManager smanager;

    // Pinger (checks that slaves are alive)
    protected SlaveWatcher pinger;

    // Slaves : effective resources
    protected Slave slaveGroupStub;
    protected Group slaveGroup;

    // Slave memory
    protected Map<String, Object> initialMemory;

    // Sleeping slaves (we might want to wake them up)
    protected Slave sleepingGroupStub;
    protected Group sleepingGroup;
    protected HashMap<String, Slave> slavesByName;
    protected HashMap<Slave, String> slavesByNameRev;
    protected HashMap<String, Long> slavesActivity;

    // Task Queues :
    // tasks that wait for an available slave
    protected HashSetQueue<Long> pendingTasks;

    // tasks that are currently processing
    protected HashSetQueue<Long> launchedTasks;

    // tasks that are completed
    protected ResultQueue resultQueue;

    // the repository where to locate tasks
    protected TaskRepository repository;

    // if there is a pending request from the client
    protected Request pendingRequest;

    public AOMaster() {
        // proactive emty no arg constructor
    }

    /**
     * Creates the master with the initial memory of the slaves
     * @param initialMemory
     */
    public AOMaster(TaskRepository repository, Map<String, Object> initialMemory) {
        this.initialMemory = initialMemory;
        this.repository = repository;
        this.pendingRequest = null;
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#addResources(java.util.Collection)
     */
    public void addResources(Collection nodes) {
        ((SlaveManagerAdmin) smanager).addResources(nodes);
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#addResources(java.net.URL)
     */
    public void addResources(URL descriptorURL) {
        ((SlaveManagerAdmin) smanager).addResources(descriptorURL);
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#addResources(java.net.URL, java.lang.String[])
     */
    public void addResources(URL descriptorURL, String virtualNodeName) {
        ((SlaveManagerAdmin) smanager).addResources(descriptorURL,
            virtualNodeName);
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#addResources(org.objectweb.proactive.core.descriptor.data.VirtualNode)
     */
    public void addResources(VirtualNode virtualnode) {
        ((SlaveManagerAdmin) smanager).addResources(virtualnode);
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#countAvailableResults()
     */
    public int countAvailableResults() {
        return resultQueue.countAvailableResults();
    }

    /**
     * Tells if the master has some activity
     * @return master activity
     */
    protected boolean emptyPending() {
        return pendingTasks.isEmpty();
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.internal.TaskProvider#getTask(org.objectweb.proactive.extra.masterslave.interfaces.internal.Slave, java.lang.String)
     */
    public TaskIntern getTask(Slave slave, String slaveName) {
        // if we don't know him, we record the slave in our system
        if (!slavesByName.containsKey(slaveName)) {
            recordSlave(slave, slaveName);
        }

        if (emptyPending()) {
            slavesActivity.put(slaveName, TaskIntern.NULL_TASK_ID);
            sleepingGroup.add(slave);
            // we return the null task, this will cause the slave to sleep for a while
            return new TaskWrapperImpl();
        } else {
            if (sleepingGroup.contains(slave)) {
                sleepingGroup.remove(slave);
            }
            Iterator<Long> it = pendingTasks.iterator();
            long taskId = it.next();
            // We remove the task from the pending list
            it.remove();
            // We add the task inside the launched list
            launchedTasks.add(taskId);
            slavesActivity.put(slaveName, taskId);
            TaskIntern taskfuture = repository.getTask(taskId);
            TaskIntern realTask = (TaskIntern) ProActive.getFutureValue(taskfuture);
            repository.saveTask(taskId);

            return realTask;
        }
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.InitActive#initActivity(org.objectweb.proactive.Body)
     */
    public void initActivity(Body body) {
        stubOnThis = ProActive.getStubOnThis();
        // General initializations
        terminated = false;
        // Queues 
        pendingTasks = new HashSetQueue<Long>();
        launchedTasks = new HashSetQueue<Long>();
        resultQueue = new ResultQueue(Master.OrderingMode.CompletionOrder);

        // Ignore NFEs occuring on ourself (send reply exceptions on dead slaves)
        ProActive.getBodyOnThis().addNFEListener(new SelfNFEListener());

        // Slaves
        try {
            slaveGroupStub = (Slave) ProActiveGroup.newGroup(AOSlave.class.getName());
            slaveGroup = ProActiveGroup.getGroup(slaveGroupStub);
            sleepingGroupStub = (Slave) ProActiveGroup.newGroup(AOSlave.class.getName());
            sleepingGroup = ProActiveGroup.getGroup(sleepingGroupStub);
            slavesActivity = new HashMap<String, Long>();
            slavesByName = new HashMap<String, Slave>();
            slavesByNameRev = new HashMap<Slave, String>();
        } catch (ClassNotReifiableException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        try {
            // The resource manager
            smanager = (AOSlaveManager) ProActive.newActive(AOSlaveManager.class.getName(),
                    new Object[] { stubOnThis, initialMemory });

            // The slave pinger
            pinger = (SlaveWatcher) ProActive.newActive(AOPinger.class.getName(),
                    new Object[] { stubOnThis });
        } catch (ActiveObjectCreationException e1) {
            e1.printStackTrace();
        } catch (NodeException e1) {
            e1.printStackTrace();
        }
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.internal.SlaveDeadListener#isDead(org.objectweb.proactive.extra.masterslave.interfaces.internal.Slave)
     */
    public void isDead(Slave slave) {
        String slaveName = slavesByNameRev.get(slave);
        if (logger.isInfoEnabled()) {
            logger.info(slaveName + " reported missing... removing it");
        }

        // we remove the slave from our lists
        if (slaveGroup.contains(slave)) {
            slaveGroup.remove(slave);
            if (sleepingGroup.contains(slave)) {
                sleepingGroup.remove(slave);
            }
            slavesByNameRev.remove(slave);
            slavesByName.remove(slaveName);
            // if the slave was handling a task we put the task back to the pending queue
            Long taskId = slavesActivity.get(slaveName);
            if ((taskId != TaskIntern.NULL_TASK_ID) &&
                    launchedTasks.contains(taskId)) {
                launchedTasks.remove(taskId);
                if (pendingTasks.isEmpty()) {
                    // if the queue was empty before the task is rescheduled, we wake-up all sleeping slaves
                    if (sleepingGroup.size() > 0) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Waking up sleeping slaves...");
                        }

                        // We wake up the sleeping guys
                        sleepingGroupStub.wakeup();
                    }
                    pendingTasks.add(taskId);
                } else {
                    pendingTasks.add(taskId);
                }
            }
        }
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#isEmpty()
     */
    public boolean isEmpty() {
        return resultQueue.isEmpty();
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.internal.SlaveConsumer#receiveSlave(org.objectweb.proactive.extra.masterslave.interfaces.internal.Slave)
     */
    public boolean recordSlave(Slave slave, String slaveName) {
        // We record the slave in our system
        slavesByName.put(slaveName, slave);
        slavesByNameRev.put(slave, slaveName);
        slaveGroup.add(slave);

        // We tell the pinger to watch for this new slave
        pinger.addSlaveToWatch(slave);
        return true;
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.RunActive#runActivity(org.objectweb.proactive.Body)
     */
    public void runActivity(Body body) {
        Service service = new Service(body);
        while (!terminated) {
            service.waitForRequest();
            // We detect a waitXXX request in the request queue
            Request waitRequest = service.getOldest(new FindWaitFilter());
            if (waitRequest != null) {
                if (pendingRequest == null) {
                    // if there is one and there was none previously found we remove it and store it for later
                    pendingRequest = waitRequest;
                    service.blockingRemoveOldest(new FindWaitFilter());
                } else {
                    // if there is one and there was another one pending, we serve it immediately (it's an error)
                    service.serveOldest(new FindWaitFilter());
                }
            }
            // we serve directly every methods from the slaves
            service.serveAll("getTask");
            service.serveAll("sendResultAndGetTask");
            service.serveAll("isDead");

            // we serve everything else which is not a waitXXX method
            // Careful, the order is very important here, we need to serve the solve method before the waitXXX
            service.serveAll(new FindNotWaitFilter());

            // we maybe serve the pending waitXXX method if there is one and if the necessary results are collected
            maybeServePending();
        }
        body.terminate();
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.internal.TaskProvider#sendResultAndGetTask(org.objectweb.proactive.extra.masterslave.interfaces.Task,java.lang.String)
     */
    public TaskIntern sendResultAndGetTask(ResultIntern result,
        String originatorName) {
        long taskId = result.getId();
        if (launchedTasks.contains(taskId)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Result of task " + taskId + " received.");
            }
            launchedTasks.remove(taskId);
            // We add the result in the result queue
            resultQueue.addCompletedTask(result);
            // We remove the task from the repository (it won't be needed anymore)
            repository.removeTask(taskId);
        }

        // We assign a new task to the slave
        TaskIntern newTask = getTask(slavesByName.get(originatorName),
                originatorName);
        return newTask;
    }

    /**
     * If there is a pending waitXXX method, we serve it if the necessary results are collected
     */
    protected void maybeServePending() {
        if (pendingRequest != null) {
            if (pendingRequest.getMethodName().equals("waitOneResult") &&
                    resultQueue.isOneResultAvailable()) {
                servePending();
            } else if (pendingRequest.getMethodName().equals("waitAllResults") &&
                    resultQueue.areAllResultsAvailable()) {
                servePending();
            } else if (pendingRequest.getMethodName().equals("waitKResults")) {
                int k = (Integer) pendingRequest.getParameter(0);
                if (((resultQueue.countPendingResults() +
                        resultQueue.countAvailableResults()) < k) || (k <= 0)) {
                    servePending();
                } else if (resultQueue.countAvailableResults() >= k) {
                    servePending();
                }
            }
        }
    }

    /**
     * Serve the pending waitXXX method
     */
    protected void servePending() {
        Body body = ProActive.getBodyOnThis();
        Request req = pendingRequest;
        pendingRequest = null;
        body.serve(req);
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#setResultReceptionOrder(org.objectweb.proactive.extra.masterslave.interfaces.Master.OrderingMode)
     */
    public void setResultReceptionOrder(
        org.objectweb.proactive.extra.masterslave.interfaces.Master.OrderingMode mode) {
        resultQueue.setMode(mode);
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#slavepoolSize()
     */
    public int slavepoolSize() {
        return slaveGroup.size();
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#solve(java.util.List)
     */
    public void solve(List tasks) {
        logger.debug("Adding " + tasks.size() + " tasks...");

        for (Long taskId : (List<Long>) tasks) {
            solve(taskId);
        }
    }

    /**
     * Adds a task to solve
     * @param task
     * @throws IllegalArgumentException
     */
    protected void solve(Long taskId) {
        resultQueue.addPendingTask(taskId);

        if (emptyPending()) {
            pendingTasks.add(taskId);
            if (sleepingGroup.size() > 0) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Waking up sleeping slaves...");
                }

                // We wake up the sleeping guys
                sleepingGroupStub.wakeup();
            }
        } else {
            pendingTasks.add(taskId);
        }
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#terminate(boolean)
     */
    public void terminate(boolean freeResources) {
        terminateIntern(freeResources);
    }

    /**
     * Synchronous version of terminate
     * @param freeResources
     * @return true if completed successfully
     */
    public boolean terminateIntern(boolean freeResources) {
        terminated = true;
        if (logger.isDebugEnabled()) {
            logger.debug("Terminating Master...");
        }

        // We empty every queues
        pendingTasks.clear();
        launchedTasks.clear();

        slavesActivity.clear();
        slavesByName.clear();
        slavesByNameRev.clear();

        // We give the slaves back to the resource manager
        List<Slave> slavesToFree = new ArrayList<Slave>();
        while (slaveGroup.size() > 0) {
            Slave slaveToRemove = (Slave) slaveGroup.remove(0);
            pinger.removeSlaveToWatch(slaveToRemove);
            slavesToFree.add(slaveToRemove);
        }
        smanager.freeSlaves(slavesToFree);

        // We terminate the pinger
        ProActive.waitFor(pinger.terminate());
        // We terminate the slave manager
        ProActive.waitFor(((SlaveManagerAdmin) smanager).terminate(
                freeResources));
        if (logger.isDebugEnabled()) {
            logger.debug("Master terminated...");
        }
        return true;
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#waitAllResults()
     */
    public List<ResultIntern> waitAllResults()
        throws IllegalStateException, TaskException {
        if (pendingRequest != null) {
            throw new IllegalStateException(
                "Already waiting for a wait request");
        }
        if (logger.isDebugEnabled()) {
            logger.debug("All results received by the user.");
        }
        return resultQueue.getAll();
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#waitKResults(int)
     */
    public List<ResultIntern> waitKResults(int k)
        throws IllegalArgumentException, TaskException {
        if (pendingRequest != null) {
            throw new IllegalStateException(
                "Already waiting for a wait request");
        }
        if ((resultQueue.countPendingResults() +
                resultQueue.countAvailableResults()) < k) {
            throw new IllegalArgumentException("" + k + " is too big");
        } else if (k <= 0) {
            throw new IllegalArgumentException("Wrong value : " + k);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("" + k + " results received by the user.");
        }
        return resultQueue.getNextK(k);
    }

    /* (non-Javadoc)
     * @see org.objectweb.proactive.extra.masterslave.interfaces.Master#waitOneResult()
     */
    public ResultIntern waitOneResult()
        throws IllegalStateException, TaskException {
        if (pendingRequest != null) {
            throw new IllegalStateException(
                "Already waiting for a wait request");
        }
        ResultIntern task = resultQueue.getNext();

        if (logger.isDebugEnabled()) {
            logger.debug("Result of task " + task.getId() +
                " received by the user.");
        }
        return task;
    }

    /**
     * @author fviale
     * Internal class for filtering requests in the queue
     */
    protected class FindWaitFilter implements RequestFilter {
        public FindWaitFilter() {
        }

        /* (non-Javadoc)
         * @see org.objectweb.proactive.core.body.request.RequestFilter#acceptRequest(org.objectweb.proactive.core.body.request.Request)
         */
        public boolean acceptRequest(Request request) {
            // We find all the requests that are not servable yet
            String name = request.getMethodName();
            if (name.equals("waitOneResult")) {
                return true;
            } else if (name.equals("waitAllResults")) {
                return true;
            } else if (name.equals("waitKResults")) {
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * @author fviale
     * Internal class for filtering requests in the queue
     */
    protected class FindNotWaitFilter implements RequestFilter {
        public FindNotWaitFilter() {
        }

        /* (non-Javadoc)
         * @see org.objectweb.proactive.core.body.request.RequestFilter#acceptRequest(org.objectweb.proactive.core.body.request.Request)
         */
        public boolean acceptRequest(Request request) {
            // We find all the requests that are not servable yet
            String name = request.getMethodName();
            if (name.equals("waitOneResult")) {
                return false;
            } else if (name.equals("waitAllResults")) {
                return false;
            } else if (name.equals("waitKResults")) {
                return false;
            } else {
                return true;
            }
        }
    }

    /**
     * Handles Non Functional Exceptions(NFE) detection
     * @author fviale
     */
    public class SelfNFEListener implements NFEListener {

        /* (non-Javadoc)
         * @see org.objectweb.proactive.core.exceptions.manager.NFEListener#handleNFE(org.objectweb.proactive.core.exceptions.NonFunctionalException)
         */
        public boolean handleNFE(NonFunctionalException nfe) {
            // do nothing : not harmful exceptions
            return true;
        }
    }
}
