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
