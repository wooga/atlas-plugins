package wooga.gradle.plugins

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.javadoc.Groovydoc
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoReportsContainer
import org.kt3k.gradle.plugin.CoverallsPlugin
import org.sonarqube.gradle.SonarQubeExtension
import wooga.gradle.plugins.sonarqube.SonarQubeConfiguration

import java.util.concurrent.Callable

class LocalPluginsPlugin implements Plugin<Project> {

    static final String INTEGRATION_TEST_TASK_NAME = "integrationTest"
    static final String COVERALLS_TASK_NAME = "coveralls"
    static final String JACOCO_TASK_NAME = "jacoco"
    private static final String INTEGRATION_TEST_SOURCE = "src/integrationTest/groovy"
    static final String DOC_EXPORT_DIR = "docs/api"
    static final String PUBLISH_GROOVY_DOCS_TASK_NAME = "publishGroovydocs"
    @Override
    void apply(Project project) {
        project.pluginManager.with {
            apply(GroovyPlugin)
            apply(IdeaPlugin)
            apply(JacocoPlugin)
            apply(MavenPublishPlugin)
            apply(JavaGradlePluginPlugin)
            apply(CoverallsPlugin)
            apply(SonarQubeConfiguration.PLUGIN_CLASS)
        }

        def integrationTestTask = setupIntegrationTestTask(project, project.tasks)
        Task testTask = project.tasks.getByName(JavaPlugin.TEST_TASK_NAME)

        configureSourceCompatibility(project)
        configureGroovyDocsTask(project)
        configureJacocoTestReport(project, integrationTestTask, testTask)
        configureSonarQubeExtension(project, SonarQubeConfiguration.withEnvVarPropertyFallback(project))
        configureTestReportOutput(project)
        configureCoverallsTask(project)

        setupRepositories(project)
        setupDependencies(project)
        forceGroovyVersion(project, GroovySystem.getVersion())

        project.publishing {
            publications {
                mavenJava(MavenPublication) {
                    from project.components.java
                }
            }
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
        dependencies.add("implementation", 'commons-io:commons-io:[2,3)')
        dependencies.add("testImplementation", 'junit:junit:[4,5)')
        dependencies.add("testImplementation", 'org.spockframework:spock-core:1.3-groovy-2.5', {
            exclude module: 'groovy-all'
        })
        dependencies.add("testImplementation", 'com.netflix.nebula:nebula-test:[8,9)')
        dependencies.add("testImplementation", 'com.github.stefanbirkner:system-rules:[1,2)')
        dependencies.add("integrationTestImplementation", javaConvention.sourceSets.getByName("test").output)
    }

    private static void configureGroovyDocsTask(final Project project) {
        TaskContainer tasks = project.tasks
        Groovydoc groovyDocTask = tasks.getByName(GroovyPlugin.GROOVYDOC_TASK_NAME) as Groovydoc
        tasks.withType(Groovydoc, { Groovydoc task ->
            if (task.name == GroovyPlugin.GROOVYDOC_TASK_NAME) {
                GradlePluginDevelopmentExtension extension = project.getExtensions().getByType(GradlePluginDevelopmentExtension)
                Callable<String> docTitle = {
                    if (extension.plugins[0]) {
                        return "${extension.plugins.first().displayName} API".toString()
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
        })

        Sync publishGroovydocTask = tasks.create(PUBLISH_GROOVY_DOCS_TASK_NAME, Sync)
        publishGroovydocTask.description = "Publish groovy docs to output directory ${DOC_EXPORT_DIR}"
        publishGroovydocTask.group = PublishingPlugin.PUBLISH_TASK_GROUP
        publishGroovydocTask.from(groovyDocTask.outputs.files)
        publishGroovydocTask.destinationDir = project.file(DOC_EXPORT_DIR)

        def publishTask = tasks.getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME)
        publishTask.dependsOn(publishGroovydocTask)
    }

    private static configureSonarQubeExtension(final Project project, SonarQubeConfiguration sonarConfig) {
        project.afterEvaluate { //TODO check the need for the 'afterEvaluate' after nebula test plugin update
            SonarQubeExtension sonarExt = project.rootProject.extensions.getByType(SonarQubeExtension)

            JavaPluginConvention javaConvention = project.getConvention().getPlugins().get("java") as JavaPluginConvention

            sonarExt.properties(sonarConfig.generateSonarProperties(project.name, project.name, javaConvention))

            Task sonarTask = project.rootProject.tasks.getByName(SonarQubeConfiguration.TASK_NAME)
            sonarTask.onlyIf { System.getenv('CI') }
        }
    }

    private static void configureCoverallsTask(final Project project) {
        def coverallsTask = project.tasks.getByName(COVERALLS_TASK_NAME)
        coverallsTask.onlyIf { System.getenv('CI') }
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
        def reporting = project.extensions.getByName(ReportingExtension.NAME) as ReportingExtension
        project.tasks.withType(Test) { task ->
            task.reports.html.setDestination(project.file("${reporting.baseDir}/${task.name}"))
        }
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

    private static void forceGroovyVersion(Project project, String version) {
        project.configurations.all({ Configuration configuration ->
            configuration.resolutionStrategy({ ResolutionStrategy strategy ->
                strategy.force("org.codehaus.groovy:groovy-all:${version}")
                strategy.force("org.codehaus.groovy:groovy-macro:${version}")
                strategy.force("org.codehaus.groovy:groovy-nio:${version}")
                strategy.force("org.codehaus.groovy:groovy-sql:${version}")
                strategy.force("org.codehaus.groovy:groovy-xml:${version}")
            })
        })
    }

    static void configureSourceCompatibility(Project project) {
        JavaPluginExtension javaExtension = project.extensions.getByType(JavaPluginExtension)
        javaExtension.sourceCompatibility = JavaVersion.VERSION_1_8
    }
}
