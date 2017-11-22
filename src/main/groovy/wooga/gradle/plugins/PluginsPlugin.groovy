/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package wooga.gradle.plugins

import com.gradle.publish.PublishPlugin
import nebula.plugin.release.ReleasePlugin
import org.apache.commons.lang.StringUtils
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoReportsContainer
import org.kt3k.gradle.plugin.CoverallsPlugin

class PluginsPlugin implements Plugin<Project> {

    static final String INTEGRATION_TEST_TASK_NAME = "integrationTest"
    private static final String INTEGRATION_TEST_SOURCE = "src/integrationTest/groovy"

    @Override
    void apply(Project project) {

        project.pluginManager.with {
            apply(GroovyPlugin)
            apply(IdeaPlugin)
            apply(MavenPublishPlugin)
            apply(PublishPlugin)
            apply(ReleasePlugin)
            apply(JacocoPlugin)
            apply(CoverallsPlugin)
        }

        Task integrationTestTask = setupIntegrationTestTask(project, project.tasks)
        Task testTask = project.tasks.getByName(JavaPlugin.TEST_TASK_NAME)

        configureTestReportOutput(project)
        configureJacocoTestReport(project, integrationTestTask, testTask)
        configureCoverallsTask(project)
        configureTaskRuntimeDependencies(project)

        project.repositories {
            jcenter()
            mavenCentral()
            maven {
                url "https://plugins.gradle.org/m2/"
            }
        }

        project.dependencies {
            testCompile('junit:junit:4.11')
            testCompile('org.spockframework:spock-core:1.0-groovy-2.4') {
                exclude module: 'groovy-all'
            }

            testCompile 'com.netflix.nebula:nebula-test:latest.release'
            testCompile 'com.github.stefanbirkner:system-rules:1.16.0'

            compile 'org.kt3k.gradle.plugin:coveralls-gradle-plugin:2.8.2'
            compile 'com.gradle.publish:plugin-publish-plugin:0.9.9'
            compile 'com.netflix.nebula:nebula-release-plugin:5.0.0'
            compile 'commons-io:commons-io:2.5'

            compile gradleApi()
            compile localGroovy()
        }

        project.publishing {
            publications {
                mavenJava(MavenPublication) {
                    from project.components.java
                }
            }
        }
    }

    private static configureTaskRuntimeDependencies(final Project project) {
        TaskContainer tasks = project.tasks
        TaskContainer rootTasks = project.rootProject.tasks

        Task releaseCheckTask = rootTasks.getByName(ReleasePlugin.RELEASE_CHECK_TASK_NAME)
        Task postReleaseTask = rootTasks.getByName(ReleasePlugin.POST_RELEASE_TASK_NAME)
        Task snapshotTask = rootTasks.getByName(ReleasePlugin.SNAPSHOT_TASK_NAME)
        Task finalTask = rootTasks.getByName(ReleasePlugin.FINAL_TASK_NAME)
        Task releaseTask = rootTasks.getByName("release")
        Task publishPluginsTask = tasks.getByName("publishPlugins")
        Task publishToLocalMavenTask = tasks.getByName(MavenPublishPlugin.PUBLISH_LOCAL_LIFECYCLE_TASK_NAME)
        Task checkTask = tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME)
        Task assembleTask = tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME)

        releaseCheckTask.dependsOn checkTask
        releaseTask.dependsOn assembleTask
        finalTask.dependsOn publishPluginsTask
        snapshotTask.dependsOn publishToLocalMavenTask

        publishToLocalMavenTask.mustRunAfter postReleaseTask
        publishPluginsTask.mustRunAfter postReleaseTask
    }

    private static configureCoverallsTask(final Project project) {
        def coverallsTask = project.tasks.getByName("coveralls")
        coverallsTask.onlyIf(new Spec<Task>() {
            @Override
            boolean isSatisfiedBy(Task element) {
                return System.getenv('CI')
            }
        })
    }

    private static configureJacocoTestReport(final Project project, final Task integrationTestTask, Task testTask) {
        project.tasks.withType(JacocoReport, new Action<JacocoReport>() {
            @Override
            void execute(JacocoReport jacocoReport) {
                if (jacocoReport.name == "jacoco" + StringUtils.capitalize(JavaPlugin.TEST_TASK_NAME) + "Report") {
                    jacocoReport.reports(new Action<JacocoReportsContainer>() {
                        @Override
                        void execute(JacocoReportsContainer configurableReports) {
                            configurableReports.xml.enabled = true
                            configurableReports.html.enabled = true
                        }
                    })

                    jacocoReport.executionData(integrationTestTask, testTask)
                }
            }
        })
    }

    private static void configureTestReportOutput(final Project project) {
        ReportingExtension reporting = project.extensions.getByName(ReportingExtension.NAME) as ReportingExtension

        project.tasks.withType(Test, new Action<Test>() {
            @Override
            void execute(Test task) {
                task.reports.html.setDestination(project.file("${reporting.baseDir}/${task.name}"))
            }
        })
    }

    private static Test setupIntegrationTestTask(final Project project, final TaskContainer tasks) {
        JavaPluginConvention javaConvention = project.getConvention().getPlugins().get("java") as JavaPluginConvention

        def integrationTestSourceSet = setupIntegrationTestSourceSet(project, javaConvention)
        setupIntegrationTestConfiguration(project)
        setupIntegrationTestIdeaModule(project)


        Test integrationTestTask = tasks.create(name: INTEGRATION_TEST_TASK_NAME, type: Test) as Test

        integrationTestTask.with {
            setTestClassesDirs(integrationTestSourceSet.output.classesDirs)
            classpath = integrationTestSourceSet.runtimeClasspath
            outputs.upToDateWhen { false }
        }

        def checkLifeCycleTask = tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME)
        checkLifeCycleTask.dependsOn integrationTestTask

        def testTask = tasks.getByName(JavaPlugin.TEST_TASK_NAME)
        integrationTestTask.mustRunAfter testTask

        integrationTestTask
    }

    private static setupIntegrationTestIdeaModule(final Project project) {
        def ideaModel = project.extensions.getByType(IdeaModel.class)
        ideaModel.module.testSourceDirs += project.file(INTEGRATION_TEST_SOURCE)
        ideaModel.module.scopes["TEST"]["plus"] += [project.configurations.getByName("integrationTestCompile")]
    }

    private static void setupIntegrationTestConfiguration(Project project) {
        def configurations = project.configurations
        def testCompile = configurations.getByName("testCompile")
        def testRuntime = configurations.getByName("testRuntime")

        def integrationTestCompile = configurations.getByName("integrationTestCompile")
        integrationTestCompile.extendsFrom(testCompile)

        def integrationTestRuntime = configurations.getByName("integrationTestRuntime")
        integrationTestRuntime.extendsFrom(testRuntime)
    }

    private static SourceSet setupIntegrationTestSourceSet(
            final Project project, final JavaPluginConvention javaConvention) {
        def main = javaConvention.sourceSets.getByName("main")
        def test = javaConvention.sourceSets.getByName("test")

        javaConvention.sourceSets {
            integrationTest {
                groovy {
                    compileClasspath += main.output + test.output
                    runtimeClasspath += main.output + test.output
                    srcDir project.file(INTEGRATION_TEST_SOURCE)
                }
                resources.srcDir 'src/integrationTest/resources'
            }
        }

        javaConvention.getSourceSets().getByName("integrationTest")
    }
}