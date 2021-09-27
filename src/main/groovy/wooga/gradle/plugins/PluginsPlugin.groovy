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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.UncheckedIOException
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
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
import wooga.gradle.github.GithubPlugin
import wooga.gradle.github.base.GithubPluginExtension
import wooga.gradle.github.publish.GithubPublishPlugin
import wooga.gradle.github.publish.tasks.GithubPublish
import wooga.gradle.githubReleaseNotes.GithubReleaseNotesPlugin
import wooga.gradle.githubReleaseNotes.tasks.GenerateReleaseNotes
import wooga.gradle.plugins.releasenotes.ReleaseNotesStrategy
import wooga.gradle.plugins.sonarqube.SonarQubeConfiguration
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
            apply(SonarQubeConfiguration.PLUGIN_CLASS)
        }

        def integrationTestTask = setupIntegrationTestTask(project, project.tasks)
        def testTask = project.tasks.named(JavaPlugin.TEST_TASK_NAME)
        configureVersionPluginExtension(project)
        configureSonarQubeExtension(project, SonarQubeConfiguration.withEnvVarFallback(project))
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

        project.configurations.all({ Configuration configuration ->
            configuration.resolutionStrategy({ ResolutionStrategy strategy ->
                def localGroovy = GroovySystem.getVersion()
                strategy.force("org.codehaus.groovy:groovy-all:${localGroovy}")
                strategy.force("org.codehaus.groovy:groovy-macro:${localGroovy}")
                strategy.force("org.codehaus.groovy:groovy-nio:${localGroovy}")
                strategy.force("org.codehaus.groovy:groovy-sql:${localGroovy}")
                strategy.force("org.codehaus.groovy:groovy-xml:${localGroovy}")
            })
        })
    }

    private static void configureReleaseNotes(Project project) {
        def githubExt = project.extensions.getByType(GithubPluginExtension)
        def releaseNotesProvider = project.tasks.register(RELEASE_NOTES_TASK_NAME, GenerateReleaseNotes)
        releaseNotesProvider.configure { task ->
            task.onlyIf(new ProjectStatusTaskSpec("rc", "final"))
            def versionExt = project.extensions.findByType(VersionPluginExtension)
            if (versionExt) {
                task.from.set(versionExt.version.map { version ->
                    if(version.previousVersion) {
                        return "v${version.previousVersion}"
                    } else {
                        return null
                    }
                })
                task.output.set(new File("${project.buildDir}/outputs/release-notes.md"))
                task.strategy.set(new ReleaseNotesStrategy())
            }
        }
    }


    private static void configureVersionPluginExtension(Project project) {
        def versionExt = project.extensions.findByType(VersionPluginExtension)
        if(versionExt) {
            versionExt.versionScheme.set(VersionScheme.semver2)
            versionExt.versionCodeScheme.set(VersionCodeScheme.releaseCount)
        }
    }

    private static void setupRepositories(Project project) {
        def repositories = project.repositories
        repositories.add(repositories.mavenCentral())
        repositories.add(repositories.gradlePluginPortal())
    }

    private static void setupDependencies(Project project) {
        JavaPluginConvention javaConvention = project.getConvention().getPlugins().get("java") as JavaPluginConvention
        DependencyHandler dependencies = project.getDependencies();
        dependencies.add("api", dependencies.gradleApi())
        dependencies.add("testImplementation", 'junit:junit:[4,5)')
        dependencies.add("testImplementation", 'org.spockframework:spock-core:1.3-groovy-2.5', {
            exclude module: 'groovy-all'
        })
        dependencies.add("testImplementation", 'com.netflix.nebula:nebula-test:[8,9)')
        dependencies.add("testImplementation", 'com.github.stefanbirkner:system-rules:[1,2)')
        dependencies.add("implementation", 'commons-io:commons-io:[2,3)')
        dependencies.add("integrationTestImplementation", javaConvention.sourceSets.getByName("test").output)
    }

    private static def configureGradleDocsTask(final Project project) {
        TaskContainer tasks = project.tasks
        def groovyDocTask = tasks.named(GroovyPlugin.GROOVYDOC_TASK_NAME, Groovydoc)
        tasks.withType(Groovydoc).configureEach {task ->
            if (task.name == GroovyPlugin.GROOVYDOC_TASK_NAME) {
                PluginBundleExtension extension = project.getExtensions().getByType(PluginBundleExtension)
                Callable<String> docTitle = {
                    if (extension.plugins[0]) {
                        return "${extension.plugins[0].displayName} API".toString()
                    }
                    return null
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

        def publishGroovydocTask = tasks.register(PUBLISH_GROOVY_DOCS_TASK_NAME, Sync) {publishGroovydocTask ->
            publishGroovydocTask.description = "Publish groovy docs to output directory ${DOC_EXPORT_DIR}"
            publishGroovydocTask.group = PublishingPlugin.PUBLISH_TASK_GROUP
            publishGroovydocTask.from(groovyDocTask.map({it.outputs.files}))
            publishGroovydocTask.destinationDir = project.file(DOC_EXPORT_DIR)

        }

        def publishTask = tasks.named(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME)
        publishTask.configure {dependsOn(publishGroovydocTask)}
    }

    private static configureTaskRuntimeDependencies(final Project project) {
        TaskContainer tasks = project.tasks

        def finalTask = tasks.register("final")
        def rcTask = tasks.register("rc")
        def snapshotTask = tasks.register("snapshot")

        def publishPluginsTask = tasks.named("publishPlugins") //from gradle PublishPlugin
        def publishToLocalMavenTask = tasks.named(MavenPublishPlugin.PUBLISH_LOCAL_LIFECYCLE_TASK_NAME)
        def checkTask = tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME)
        def publishTask = tasks.named(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME)
        def releaseNotesTask = tasks.named(RELEASE_NOTES_TASK_NAME)
        def githubPublishTask = tasks.named(GithubPublishPlugin.PUBLISH_TASK_NAME)


        finalTask.configure { dependsOn publishTask }
        rcTask.configure { dependsOn publishTask }
        snapshotTask.configure { dependsOn checkTask, publishToLocalMavenTask }
        publishToLocalMavenTask.configure { mustRunAfter checkTask }

        publishTask.configure { dependsOn checkTask, publishPluginsTask, githubPublishTask }
        publishPluginsTask.configure { mustRunAfter checkTask }
        githubPublishTask.configure { mustRunAfter publishPluginsTask }

        githubPublishTask.configure { dependsOn releaseNotesTask }
    }

    private static void configureGithubPublishTask(Project project) {
        def tasks = project.tasks
        def githubExt = project.extensions.getByType(GithubPluginExtension)
        def releaseNotesTask = tasks.named(RELEASE_NOTES_TASK_NAME, GenerateReleaseNotes).forUseAtConfigurationTime()
        def publishTaskProvider = tasks.named(GithubPublishPlugin.PUBLISH_TASK_NAME)
        publishTaskProvider.configure {GithubPublish githubPublishTask ->
            githubPublishTask.onlyIf(new ProjectStatusTaskSpec("rc", "final"))
            githubPublishTask.with {
                releaseName.set(project.provider {project.version.toString()})
                tagName.set(project.provider {"v${project.version}"})
                prerelease.set(project.properties['release.stage']!='final')
                body.set(releaseNotesTask.flatMap({it.output.map({it.asFile.text })}))
            }
        }
    }

    private static configureSonarQubeExtension(final Project project,
                                               SonarQubeConfiguration sonarConfig) {
        def githubExt = project.extensions.getByType(GithubPluginExtension)
        SonarQubeExtension sonarExt = project.rootProject.extensions.getByType(SonarQubeExtension)
        JavaPluginConvention javaConvention = project.getConvention().getPlugins().get("java") as JavaPluginConvention

        def branchName = localBranchProviderWithPR(project, project.extensions.getByType(GithubPluginExtension)).
        map {it.trim().isEmpty()? null : it }.
        map {
            project.logger.info("Using ${it} as sonarqube branch")
            return it
        }.orElse(project.provider {
            project.logger.info("Not setting branch information on sonarqube")
            return null as String
        })
        project.rootProject.tasks.named(SonarQubeConfiguration.TASK_NAME) {sonarTask ->
            sonarExt.properties(sonarConfig.generateSonarProperties(githubExt.repositoryName, branchName, javaConvention))
            sonarTask.onlyIf { System.getenv('CI') }
        }
    }

    private static configureCoverallsTask(final Project project) {
        project.tasks.named("coveralls") {
            it.onlyIf { System.getenv('CI') }
        }
    }

    private static configureJacocoTestReport(final Project project,
                                             final TaskProvider<? extends Task> integrationTestTask,
                                             final TaskProvider<Task> testTask) {
        project.tasks.withType(JacocoReport).configureEach { JacocoReport jacocoReport ->
            if (jacocoReport.name == "jacoco" + JavaPlugin.TEST_TASK_NAME.capitalize() + "Report") {
                jacocoReport.reports{ JacocoReportsContainer configurableReports ->
                    configurableReports.xml.enabled = true
                    configurableReports.html.enabled = true
                }
                jacocoReport.executionData(integrationTestTask.get(), testTask.get())
            }
        }
    }

    private static void configureTestReportOutput(final Project project) {
        ReportingExtension reporting = project.extensions.getByName(ReportingExtension.NAME) as ReportingExtension

        project.tasks.withType(Test).configureEach {task ->
                task.reports.html.setDestination(project.file("${reporting.baseDir}/${task.name}"))
        }
    }

    private static TaskProvider<Test> setupIntegrationTestTask(final Project project, final TaskContainer tasks) {
        JavaPluginConvention javaConvention = project.getConvention().getPlugins().get("java") as JavaPluginConvention

        def integrationTestSourceSet = setupIntegrationTestSourceSet(project, javaConvention)
        setupIntegrationTestConfiguration(project, javaConvention)
        setupIntegrationTestIdeaModule(project)

        def testTask = tasks.named(JavaPlugin.TEST_TASK_NAME)
        def integrationTestTask = tasks.register(INTEGRATION_TEST_TASK_NAME, Test) {
            setTestClassesDirs(integrationTestSourceSet.output.classesDirs)
            classpath = integrationTestSourceSet.runtimeClasspath
            outputs.upToDateWhen { false }
            mustRunAfter(testTask)
        }

        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
            dependsOn integrationTestTask
        }

        return integrationTestTask
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
        return sourceSet
    }


    static Provider<String> localBranchProviderWithPR(Project project, GithubPluginExtension githubExt) {
        def clientProvider = emptyProviderForException(project, githubExt.clientProvider, UncheckedIOException)

        return githubExt.branchName.map({ String currentBranch ->
            return githubExt.repositoryName.map { repositoryName ->
                return clientProvider.map{ client ->
                    def repository = client.getRepository(repositoryName)
                    if (currentBranch.toUpperCase().startsWith("PR-")) {
                        def maybePrNumber = currentBranch.replace("PR-", "").trim()
                        if (maybePrNumber.isNumber()) {
                            def prNumber = Integer.valueOf(maybePrNumber)
                            return repository.getPullRequest(prNumber).head.ref
                        }
                        return null
                    }
                }.getOrElse(currentBranch)
            }.getOrElse(currentBranch)
        }.memoize())
    }

    protected static <T> Provider<T> emptyProviderForException(Project project,
                                                               Provider<T> provider,
                                                               Class<? extends Throwable> exceptionClass) {
        return project.provider {
            try {
                return provider.get()
            }catch(Throwable e) {
                if(exceptionClass.isInstance(e)) {
                    return null
                }
                throw e
            }
        }
    }
}
