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

import com.gradle.publish.PublishPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.tasks.TaskContainer
import org.gradle.language.base.plugins.LifecycleBasePlugin
import wooga.gradle.github.publish.GithubPublishPlugin

/**
 * This plugin is a convenient gradle plugin which which acts as a base for all atlas gradle plugins
 *
 * It will set repositories and dependencies to other gradle plugins that are needed for
 * the development of other atlas gradle plugins
 *
 * - com.netflix.nebula:nebula-test
 * - org.spockframework:spock-core
 * - commons-io:commons-io
 * - com.gradle.publish:plugin-publish-plugin
 *
 * The plugin will also hock up a complete build/publish lifecycle.
 */
class PluginsPlugin implements Plugin<Project> {

    static final String PUBLISH_PLUGIN_TASK_NAME = "publishPlugins"

    @Override
    void apply(Project project) {
        project.pluginManager.with {
            apply(PrivatePluginsPlugin)
            apply(PublishPlugin)
        }
        configureTaskRuntimeDependencies(project)
    }

    private static void configureTaskRuntimeDependencies(final Project project) {
        TaskContainer tasks = project.tasks

        Task publishPluginsTask = tasks.getByName(PUBLISH_PLUGIN_TASK_NAME) //from gradle PublishPlugin
        Task checkTask = tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME)
        Task publishTask = tasks.getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME)
        Task githubPublishTask = tasks.getByName(GithubPublishPlugin.PUBLISH_TASK_NAME)

        publishTask.dependsOn checkTask, publishPluginsTask, githubPublishTask
        publishPluginsTask.mustRunAfter checkTask
        githubPublishTask.mustRunAfter publishPluginsTask
    }
}
