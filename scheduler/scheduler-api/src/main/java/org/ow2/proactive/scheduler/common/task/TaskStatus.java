/*
 * ProActive Parallel Suite(TM):
 * The Open Source library for parallel and distributed
 * Workflows & Scheduling, Orchestration, Cloud Automation
 * and Big Data Analysis on Enterprise Grids & Clouds.
 *
 * Copyright (c) 2007 - 2017 ActiveEon
 * Contact: contact@activeeon.com
 *
 * This library is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation: version 3 of
 * the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 */
package org.ow2.proactive.scheduler.common.task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.proactive.annotation.PublicAPI;

import com.google.common.collect.ImmutableSet;


/**
 * This class represents every status that a task is able to be in.<br>
 * Each status are best describe below.
 *
 * @author The ProActive Team
 * @since ProActive Scheduling 0.9
 */
@PublicAPI
public enum TaskStatus implements java.io.Serializable {

    /**
     * The task has just been submitted by the user.
     */
    SUBMITTED("Submitted", true),
    /**
     * The task is in the scheduler pending queue.
     */
    PENDING("Pending", true),
    /**
     * The task is paused.
     */
    PAUSED("Paused", true),
    /**
     * The task is executing.
     */
    RUNNING("Running", true),
    /**
     * The task is waiting for restart after an error. (ie:native code != 0 or exception)
     */
    WAITING_ON_ERROR("Faulty...", true),
    /**
     * The task is waiting for restart after a failure. (ie:node down)
     */
    WAITING_ON_FAILURE("Failed...", true),
    /**
     * The task is failed
     * (only if max execution time has been reached and the node on which it was started is down).
     */
    FAILED("Resource down", false),
    /**
     * The task could not be started.<br>
     * It means that the task will not be started due to
     * dependence's failure.
     */
    NOT_STARTED("Could not start", false),
    /**
     * The task could not be restarted.<br>
     * It means that the task could not be restarted after an error
     * during the previous execution
     */
    NOT_RESTARTED("Could not restart", false),
    /**
     * The task has been aborted by an exception on an other task while the task is running. (job is cancelOnError=true)
     * Can be also in this status if the job is killed while the concerned task was running.
     */
    ABORTED("Aborted", false),
    /**
     * The task has finished execution with error code (!=0) or exception.
     */
    FAULTY("Faulty", false),
    /**
     * The task has finished execution.
     */
    FINISHED("Finished", false),
    /**
     * The task was not executed: it was the non-selected branch of an IF/ELSE control flow action
     */
    SKIPPED("Skipped", false),
    /**
     * The task is suspended after first error and is waiting for a manual restart action.
     */
    IN_ERROR("In-Error", true);

    public static final Set<TaskStatus> ERROR_TASKS = ImmutableSet.of(IN_ERROR,
                                                                      WAITING_ON_ERROR,
                                                                      WAITING_ON_FAILURE,
                                                                      FAILED,
                                                                      FAULTY);

    public static final Set<TaskStatus> FINISHED_TASKS = ImmutableSet.of(FAILED,
                                                                         NOT_RESTARTED,
                                                                         ABORTED,
                                                                         FAULTY,
                                                                         FINISHED,
                                                                         SKIPPED,
                                                                         NOT_STARTED);

    public static final Set<TaskStatus> RUNNING_TASKS = ImmutableSet.of(PAUSED,
                                                                        IN_ERROR,
                                                                        RUNNING,
                                                                        WAITING_ON_ERROR,
                                                                        WAITING_ON_FAILURE);

    public static final Set<TaskStatus> PAUSED_AND_IN_ERROR_TASKS = ImmutableSet.of(PAUSED, IN_ERROR);

    public static final Set<TaskStatus> PENDING_TASKS = ImmutableSet.of(SUBMITTED, PENDING);

    /** The name of the current status. */
    private String name;

    private final boolean taskAlive;

    /**
     * Implicit constructor of a status.
     *
     * @param name the name of the status.
     */
    TaskStatus(String name, boolean taskAlive) {
        this.name = name;
        this.taskAlive = taskAlive;
    }

    public boolean isTaskAlive() {
        return taskAlive;
    }

    /**
     * @see java.lang.Enum#toString()
     */
    @Override
    public String toString() {
        return name;
    }

    public static List<TaskStatus> allExceptThese(TaskStatus... taskStatuses) {
        final Set<TaskStatus> statusesToAvoid = new HashSet<>(Arrays.asList(taskStatuses));
        return Arrays.stream(TaskStatus.values())
                     .filter(taskStatus -> !statusesToAvoid.contains(taskStatus))
                     .collect(Collectors.toList());
    }

    public static Set<TaskStatus> expandAggregatedStatusesToRealStatuses(List<String> aggregatedStatuses) {
        return aggregatedStatuses.stream().flatMap(aggregatedStatus -> {
            switch (aggregatedStatus.toLowerCase()) {
                case "submitted":
                    return Stream.of(TaskStatus.SUBMITTED);
                case "pending":
                    return Stream.of(TaskStatus.PENDING);
                case "current":
                case "running":
                case "active":
                    return TaskStatus.RUNNING_TASKS.stream();
                case "past":
                case "finished":
                case "terminated":
                    return TaskStatus.FINISHED_TASKS.stream();
                case "error":
                    return TaskStatus.ERROR_TASKS.stream();
                default:
                    return Stream.empty();
            }
        }).collect(Collectors.toSet());
    }

    public static List<String> wrapIntoAggregatedStatuses(Set<TaskStatus> actualStatuses) {
        return actualStatuses.stream().map(x -> {
            if (x.equals(TaskStatus.SUBMITTED)) {
                return TaskStatus.SUBMITTED.name;
            } else if (x.equals(TaskStatus.PENDING)) {
                return TaskStatus.PENDING.name;
            } else if (TaskStatus.RUNNING_TASKS.contains(x)) {
                return TaskStatus.RUNNING.name;
            } else if (TaskStatus.FINISHED_TASKS.contains(x)) {
                return TaskStatus.FINISHED.name;
            } else if (TaskStatus.ERROR_TASKS.contains(x)) {
                return "error";
            } else {
                return "";
            }
        }).distinct().collect(Collectors.toList());
    }

    public static String aggregatedStatusesToFilterString(List<String> statuses) {
        return String.join(";", statuses);
    }

    public static String statusesToString(Set<TaskStatus> actualStatuses) {
        return aggregatedStatusesToFilterString(wrapIntoAggregatedStatuses(actualStatuses));
    }

    public static Set<TaskStatus> taskStatuses(boolean pending, boolean running, boolean finished) {
        List<String> aggregatedStatuses = new LinkedList<>();

        if (pending) {
            aggregatedStatuses.add(TaskStatus.SUBMITTED.name);
            aggregatedStatuses.add(TaskStatus.PENDING.name);
        }

        if (running) {
            aggregatedStatuses.add(TaskStatus.RUNNING.name);
        }

        if (finished) {
            aggregatedStatuses.add(TaskStatus.FINISHED.name);
        }

        return TaskStatus.expandAggregatedStatusesToRealStatuses(aggregatedStatuses);
    }

    public static String statusFilterString(Set<TaskStatus> statuses) {
        return statuses.stream().map(TaskStatus::toString).collect(Collectors.joining(";"));
    }

}
