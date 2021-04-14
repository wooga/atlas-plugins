/*
 * Copyright 2018-2021 Wooga GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package wooga.gradle.plugins

import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.specs.Spec

/**
 * A generic <code>Spec&lt;Task&gt;</code> object which can be used to set task execution <code>onlyIf</code>
 * property when <code>project.status</code> equals one of the <code>validStatusValues</code> provided.
 */
class ProjectStatusTaskSpec implements Spec<Task> {

    private static final Logger logger = Logging.getLogger(ProjectStatusTaskSpec)

    private final List<Object> validStatusValues = new ArrayList<>()

    /**
     * Creates a new <code>ProjectStatusTaskSpec</code> object with given <code>validStatusValues</code>
     * @param validStatusValues <code>List&lt;Object&gt;</code> with valid status values to check
     */
    ProjectStatusTaskSpec(List<Object> validStatusValues) {
        this.validStatusValues.addAll(validStatusValues)
    }

    /**
     * Creates a new <code>ProjectStatusTaskSpec</code> object with given <code>validStatusValues</code>
     * @param validStatusValues one or more valid status value to check
     */
    ProjectStatusTaskSpec(Object... validStatusValues) {
        this.validStatusValues.addAll(validStatusValues)
    }

    /**
     * Checks if task execution is satisfied. Check current <code>project.status</code> property and checks if the value
     * is equal to one of <code>validStatusValues</code> provided.
     * @param task the task to check if execution is valid
     * @return boolean if <code>project.status</code> is equal to one of <code>validStatusValues</code> provided.
     */

    @Override
    boolean isSatisfiedBy(Task task) {
        Boolean satisfied = validStatusValues.contains(task.project.status)
        logger.info("'project.status' check satisfied $satisfied")
        return satisfied
    }
}
