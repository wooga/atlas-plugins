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

import com.gradle.publish.PluginBundleExtension
import com.gradle.publish.PublishPlugin
import org.ajoberstar.grgit.gradle.GrgitPlugin
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.javadoc.Groovydoc
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoReportsContainer
import org.kt3k.gradle.plugin.CoverallsPlugin
import org.sonarqube.gradle.SonarQubeExtension
import org.sonarqube.gradle.SonarQubePlugin
import wooga.gradle.github.GithubPlugin
import wooga.gradle.github.publish.GithubPublishPlugin
import wooga.gradle.github.publish.tasks.GithubPublish
import wooga.gradle.githubReleaseNotes.GithubReleaseNotesPlugin
import wooga.gradle.githubReleaseNotes.tasks.GenerateReleaseNotes
import wooga.gradle.plugins.releasenotes.ReleaseNotesStrategy
import wooga.gradle.version.VersionCodeScheme
import wooga.gradle.version.VersionPlugin
import wooga.gradle.version.VersionPluginExtension
import wooga.gradle.version.VersionScheme

import java.util.concurrent.Callable

/**
 * This plugin is a convenient gradle plugin which which acts as a base for all atlas gradle plugins
 *
 * It will set repositories and dependencies to other gradle plugins that are needed for
 * the development of other atlas gradle plugins
 *
 * - com.netflix.nebula:nebula-test
 * - org.spockframework:spock-core
 * - org.kt3k.gradle.plugin:coveralls-gradle-plugin
 * - com.netflix.nebula:nebula-release-plugin
 * - commons-io:commons-io
 * - com.gradle.publish:plugin-publish-plugin
 *
 * The plugin will also hock up a complete build/publish lifecycle.
 */
class PluginsPlugin implements Plugin<Project> {

    static final String INTEGRATION_TEST_TASK_NAME = "integrationTest"
    private static final String INTEGRATION_TEST_SOURCE = "src/integrationTest/groovy"
    static final String DOC_EXPORT_DIR = "docs/api"
    static final String PUBLISH_GROOVY_DOCS_TASK_NAME = "publishGroovydocs"
    public static final String RELEASE_NOTES_TASK_NAME = "releaseNotes"


    @Override
    void apply(Project project) {

        project.pluginManager.with {
            apply(GroovyPlugin)
            apply(IdeaPlugin)
            apply(JacocoPlugin)
            apply(MavenPublishPlugin)
            apply(PublishPlugin)
            apply(CoverallsPlugin)
            apply(VersionPlugin)
            apply(GithubPlugin)
            apply(GrgitPlugin)
            apply(GithubReleaseNotesPlugin)
            apply(SonarQubePlugin)
        }

        Task integrationTestTask = setupIntegrationTestTask(project, project.tasks)
        Task testTask = project.tasks.getByName(JavaPlugin.TEST_TASK_NAME)

        configureVersionPluginExtension(project)
        configureSonarQubeExtension(project)
        configureTestReportOutput(project)
        configureJacocoTestReport(project, integrationTestTask, testTask)
        configureCoverallsTask(project)
        configureReleaseNotes(project)
        configureGithubPublishTask(project)
        configureTaskRuntimeDependencies(project)
        configureGradleDocsTask(project)

        setupRepositories(project)
        setupDependencies(project)

        project.publishing {
            publications {
                mavenJava(MavenPublication) {
                    from project.components.java
                }
            }
        }
    }

    private static void configureReleaseNotes(Project project) {
        def releaseNotesProvider = project.tasks.register(RELEASE_NOTES_TASK_NAME, GenerateReleaseNotes)
        releaseNotesProvider.configure { task ->
            def versionExt = project.extensions.findByType(VersionPluginExtension)
            if (versionExt) {
                task.from.set(versionExt.version.map { version ->
                    if(version.previousVersion) {
                        return "v${version.previousVersion}"
                    } else {
                        return null
                    }
                })
                task.branch.set(project.extensions.grgit.branch.current.name as String)
                task.output.set(new File("${project.buildDir}/outputs/release-notes.md"))
                task.strategy.set(new ReleaseNotesStrategy())
            }
        }
    }


    private static void configureVersionPluginExtension(Project project) {
        def versionExt = project.extensions.findByType(VersionPluginExtension)
        if(versionExt) {
            versionExt.versionScheme.convention(VersionScheme.semver2)
            versionExt.versionCodeScheme.convention(VersionCodeScheme.releaseCount)
        }
    }

    private static void setupRepositories(Project project) {
        def repositories = project.repositories
        repositories.add(repositories.mavenCentral())
        repositories.add(repositories.gradlePluginPortal())
    }

    private static void setupDependencies(Project project) {
        DependencyHandler dependencies = project.getDependencies();
        dependencies.add("api", dependencies.gradleApi())
        dependencies.add("testImplementation", 'junit:junit:[4,5)')
        dependencies.add("testImplementation", 'org.spockframework:spock-core:1.3-groovy-2.5', {
            exclude module: 'groovy-all'
        })
        dependencies.add("testImplementation", 'com.netflix.nebula:nebula-test:[8,9)')
        dependencies.add("testImplementation", 'com.github.stefanbirkner:system-rules:[1,2)')
        dependencies.add("implementation", 'commons-io:commons-io:[2,3)')
    }

    private static def configureGradleDocsTask(final Project project) {
        TaskContainer tasks = project.tasks
        Groovydoc groovyDocTask = tasks.getByName(GroovyPlugin.GROOVYDOC_TASK_NAME) as Groovydoc
        tasks.withType(Groovydoc, new Action<Groovydoc>() {
            @Override
            void execute(Groovydoc task) {
                if (task.name == GroovyPlugin.GROOVYDOC_TASK_NAME) {
                    PluginBundleExtension extension = project.getExtensions().getByType(PluginBundleExtension)

                    Callable<String> docTitle = {
                        if (extension.plugins[0]) {
                            return "${extension.plugins[0].displayName} API".toString()
                        }
                        null
                    }

                    def conventionMapping = task.getConventionMapping()
                    conventionMapping.use = { true }
                    conventionMapping.footer = docTitle
                    conventionMapping.windowTitle = docTitle
                    conventionMapping.docTitle = docTitle
                    conventionMapping.noVersionStamp = { true }
                    conventionMapping.noTimestamp = { true }
                }
            }
        })

        Sync publishGroovydocTask = tasks.create(PUBLISH_GROOVY_DOCS_TASK_NAME, Sync)
        publishGroovydocTask.description = "Publish groovy docs to output directory ${DOC_EXPORT_DIR}"
        publishGroovydocTask.group = PublishingPlugin.PUBLISH_TASK_GROUP
        publishGroovydocTask.from(groovyDocTask.outputs.files)
        publishGroovydocTask.destinationDir = project.file(DOC_EXPORT_DIR)

        def publishTask = tasks.getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME)
        publishTask.dependsOn(publishGroovydocTask)
    }

    private static configureTaskRuntimeDependencies(final Project project) {
        TaskContainer tasks = project.tasks

        Task finalTask = project.tasks.create("final")
        Task rcTask = project.tasks.create("rc")
        Task snapshotTask = project.tasks.create("snapshot")

        Task publishPluginsTask = tasks.getByName("publishPlugins") //from gradle PublishPlugin
        Task publishToLocalMavenTask = tasks.getByName(MavenPublishPlugin.PUBLISH_LOCAL_LIFECYCLE_TASK_NAME)
        Task checkTask = tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME)
        Task publishTask = tasks.getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME)
        Task releaseNotesTask = tasks.getByName(RELEASE_NOTES_TASK_NAME)
        Task githubPublishTask = tasks.getByName(GithubPublishPlugin.PUBLISH_TASK_NAME)


        finalTask.dependsOn publishTask
        rcTask.dependsOn publishTask
        snapshotTask.dependsOn checkTask, publishToLocalMavenTask
        publishToLocalMavenTask.mustRunAfter checkTask

        publishTask.dependsOn checkTask, publishPluginsTask, githubPublishTask
        publishPluginsTask.mustRunAfter checkTask
        githubPublishTask.mustRunAfter publishPluginsTask

        githubPublishTask.dependsOn releaseNotesTask
    }

    private static void configureGithubPublishTask(Project project) {
        TaskContainer tasks = project.tasks
        GenerateReleaseNotes releaseNotesTask = tasks.getByName(RELEASE_NOTES_TASK_NAME) as GenerateReleaseNotes
        GithubPublish githubPublishTask = (GithubPublish) tasks.getByName(GithubPublishPlugin.PUBLISH_TASK_NAME)
        githubPublishTask.onlyIf(new ProjectStatusTaskSpec('candidate', 'release'))
        githubPublishTask.tagName = "v${project.version}"
        githubPublishTask.setReleaseName(project.version.toString())
        githubPublishTask.prerelease.set(project.provider { project.status != 'release' })
        githubPublishTask.body.set(releaseNotesTask.output.map{it.asFile.text })
    }

    private static configureSonarQubeExtension(final Project project) {
        JavaPluginConvention javaConvention = project.getConvention().getPlugins().get("java") as JavaPluginConvention

        SonarQubeExtension sonarExt = project.rootProject.extensions.getByType(SonarQubeExtension)
        sonarExt.properties {
            property "sonar.projectName",
                    extProperties(project, "sonar.projectName", "SONAR_PROJECT_NAME", "")
            property "sonar.login",
                    extProperties(project, "sonar.login", "SONAR_LOGIN", "")
            property "sonar.sources",
                    extProperties(project, "sonar.sources", "SONAR_SOURCES",
                        sourceDirectoriesMatching(javaConvention){ !it.name.toLowerCase().contains("test") }.join(","))
            property "sonar.tests",
                    extProperties(project, "sonar.tests", "SONAR_TESTS",
                        sourceDirectoriesMatching(javaConvention) { it.name.toLowerCase().contains("test") }.join(","))
            property "sonar.jacoco.reportPaths",
                    extProperties(project,
                "sonar.jacoco.reportPaths", "SONAR_JACOCO_REPORT_PATHS",
                    "build/jacoco/integrationTest.exec,build/jacoco/test.exec")
        }
        def sonarTask = project.rootProject.tasks.getByName(SonarQubeExtension.SONARQUBE_TASK_NAME)
        sonarTask.onlyIf { System.getenv('CI') }
    }

    private static String extProperties(final Project project,
                                        String projectPropertyKey, String envVarKey, String defaultValue) {
        if(project.hasProperty(projectPropertyKey)) {
            return project.property(projectPropertyKey)
        } else if(System.getenv().containsKey(envVarKey)) {
            return System.getenv(envVarKey)
        } else {
            return defaultValue;
        }
    }

    private static List<String> sourceDirectoriesMatching(JavaPluginConvention javaConvention, Closure closure) {
        return javaConvention.sourceSets.findAll(closure).
                collect {SourceSet sourceSet ->
                    sourceSet.allJava.sourceDirectories.collect {it.absolutePath}
                }.flatten()
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
        project.tasks.withType(JacocoReport) { JacocoReport jacocoReport ->
            if (jacocoReport.name == "jacoco" + JavaPlugin.TEST_TASK_NAME.capitalize() + "Report") {
                jacocoReport.reports{ JacocoReportsContainer configurableReports ->
                    configurableReports.xml.enabled = true
                    configurableReports.html.enabled = true
                }
                jacocoReport.executionData(integrationTestTask, testTask)
            }
        }
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
        setupIntegrationTestConfiguration(project, javaConvention)
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

    private static void setupIntegrationTestConfiguration(Project project, final JavaPluginConvention javaConvention) {
        def test = javaConvention.sourceSets.getByName("test")
        def integrationTest = javaConvention.sourceSets.getByName("integrationTest")

        test.implementationConfigurationName
        def configurations = project.configurations
        def testImplementation = configurations.getByName(test.implementationConfigurationName)
        def testRuntimeOnly = configurations.getByName(test.runtimeOnlyConfigurationName)

        def integrationTestImplementation = configurations.getByName(integrationTest.implementationConfigurationName)
        integrationTestImplementation.extendsFrom(testImplementation)

        def integrationTestRuntimeOnly = configurations.getByName(integrationTest.runtimeOnlyConfigurationName)
        integrationTestRuntimeOnly.extendsFrom(testRuntimeOnly)
    }

    private static SourceSet setupIntegrationTestSourceSet(final Project project, final JavaPluginConvention javaConvention) {
        def main = javaConvention.sourceSets.getByName("main")
        def test = javaConvention.sourceSets.getByName("test")

        SourceSet sourceSet = javaConvention.sourceSets.maybeCreate("integrationTest")
        sourceSet.setCompileClasspath(project.files(main.compileClasspath, test.compileClasspath, sourceSet.compileClasspath))
        sourceSet.setRuntimeClasspath(project.files(main.compileClasspath, test.compileClasspath, sourceSet.runtimeClasspath))

        sourceSet.groovy.srcDir("src/" + sourceSet.getName() + "/groovy");
        sourceSet.resources.srcDir("src/" + sourceSet.getName() + "/resources");
        sourceSet
    }
}
