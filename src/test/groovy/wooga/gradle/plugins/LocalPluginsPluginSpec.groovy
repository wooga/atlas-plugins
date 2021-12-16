package wooga.gradle.plugins


import nebula.test.ProjectSpec
import org.ajoberstar.grgit.Grgit
import org.gradle.api.Plugin
import org.gradle.api.Task
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.kt3k.gradle.plugin.CoverallsPlugin
import org.kt3k.gradle.plugin.coveralls.CoverallsTask
import org.sonarqube.gradle.SonarQubeExtension
import org.sonarqube.gradle.SonarQubePlugin
import org.sonarqube.gradle.SonarQubeTask
import spock.lang.Unroll
import wooga.gradle.plugins.sonarqube.SonarQubeConfiguration

class LocalPluginsPluginSpec extends ProjectSpec {

    private static final String PLUGIN_NAME = "net.wooga.plugins-local"

    @Unroll("applies plugin #pluginName")
    def 'Applies other plugins'(String pluginName, Class<? extends Plugin> pluginType) {
        given:
        assert !project.plugins.hasPlugin(PLUGIN_NAME)
        assert !project.plugins.hasPlugin(pluginType)

        when:
        project.plugins.apply(PLUGIN_NAME)

        then:
        project.plugins.hasPlugin(pluginType)

        where:
        pluginName           | pluginType
        "groovy"             | GroovyPlugin
        "idea"               | IdeaPlugin
        "jacoco"             | JacocoPlugin
        "maven-publish"      | MavenPublishPlugin
        "java-gradle-plugin" | JavaGradlePluginPlugin
        "coveralls"          | CoverallsPlugin
        "sonarqube"          | SonarQubePlugin
    }

    @Unroll("creates the task #taskName")
    def 'Creates needed tasks'(String taskName, Class taskType) {
        given:
        assert !project.plugins.hasPlugin(PLUGIN_NAME)
        assert !project.tasks.findByName(taskName)

        when:
        project.plugins.apply(PLUGIN_NAME)

        then:
        def task = project.tasks.findByName(taskName)
        taskType.isInstance(task)

        where:
        taskName                                         | taskType
        LocalPluginsPlugin.INTEGRATION_TEST_TASK_NAME    | Test
        LocalPluginsPlugin.PUBLISH_GROOVY_DOCS_TASK_NAME | Sync
        SonarQubeConfiguration.TASK_NAME                 | SonarQubeTask
        LocalPluginsPlugin.COVERALLS_TASK_NAME           | CoverallsTask
        LifecycleBasePlugin.CHECK_TASK_NAME              | Task
        LifecycleBasePlugin.ASSEMBLE_TASK_NAME           | Task
    }

    @Unroll("task #taskName has runtime dependencies")
    def 'Task has runtime dependencies'(String taskName, String[] dependencies) {
        given:
        assert !project.plugins.hasPlugin(PLUGIN_NAME)
        assert !project.tasks.findByName(taskName)

        when:
        project.plugins.apply(PLUGIN_NAME)

        then:
        Task task = project.tasks.findByName(taskName)
        task.getTaskDependencies().getDependencies(task).findAll { t ->
            dependencies.find {
                depName -> t.name == depName
            }
        }

        where:
        taskName                                     | dependencies
        PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME | [LocalPluginsPlugin.PUBLISH_GROOVY_DOCS_TASK_NAME]
        LifecycleBasePlugin.CHECK_TASK_NAME          | [LocalPluginsPlugin.INTEGRATION_TEST_TASK_NAME]
    }

    def "configures integration test task"() {
        given: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        and: "the integration test task"
        Test integrationTestTask = project.tasks.getByName(LocalPluginsPlugin.INTEGRATION_TEST_TASK_NAME) as Test

        and: "the integration test sourceset"
        JavaPluginConvention javaConvention = project.getConvention().getPlugins().get("java") as JavaPluginConvention
        SourceSet integrationTestSourceset = javaConvention.sourceSets.getByName("integrationTest")

        expect:
        integrationTestTask.testClassesDirs == integrationTestSourceset.output.classesDirs
        integrationTestTask.classpath == integrationTestSourceset.runtimeClasspath
    }

    def "configures integration test source set"() {
        given: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        and: "a java plugin convention"
        JavaPluginConvention javaConvention = project.getConvention().getPlugins().get("java") as JavaPluginConvention
        def main = javaConvention.sourceSets.getByName("main")
        def test = javaConvention.sourceSets.getByName("test")

        expect: "plugin to setup integration test source set"
        def integrationTestSourceSet = javaConvention.getSourceSets().getByName("integrationTest")

        def compileClassPath = integrationTestSourceSet.compileClasspath
        compileClassPath.getFiles().containsAll(main.compileClasspath.getFiles())
        compileClassPath.getFiles().containsAll(test.compileClasspath.getFiles())

        def runtimeClassPath = integrationTestSourceSet.runtimeClasspath
        runtimeClassPath.getFiles().containsAll(main.compileClasspath.getFiles())
        runtimeClassPath.getFiles().containsAll(test.compileClasspath.getFiles())

        def srcDirs = integrationTestSourceSet.allSource.getSrcDirs()
        srcDirs.contains(new File(projectDir, 'src/integrationTest/groovy'))
    }

    def "configures integration test configruation"() {
        given: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        and: "a configuration container"
        def configurations = project.configurations
        def testIntegration = configurations.getByName("testImplementation")
        def testRuntimeOnly = configurations.getByName("testRuntimeOnly")

        expect:
        def integrationTestImplementation = configurations.getByName("integrationTestImplementation")
        def integrationTestRuntimeOnly = configurations.getByName("integrationTestRuntimeOnly")

        integrationTestImplementation.extendsFrom.contains(testIntegration)
        integrationTestRuntimeOnly.extendsFrom.contains(testRuntimeOnly)
    }

    def "configures idea project modules"() {
        given: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        expect:
        def integrationTestCompileConfiguration = project.configurations.getByName("integrationTestCompile")
        def ideaModel = project.extensions.getByType(IdeaModel.class)
        ideaModel.module.testSourceDirs.contains(new File(projectDir, "src/integrationTest/groovy"))
        ideaModel.module.scopes["TEST"]["plus"].contains(integrationTestCompileConfiguration)
    }

    def "configures jacoco task to enable xml/html reports"() {
        given: "project with local plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        expect:
        def jacocoTask = project.tasks.getByName("jacoco" + JavaPlugin.TEST_TASK_NAME.capitalize() + "Report") as JacocoReport
        jacocoTask.reports.xml.enabled
        jacocoTask.reports.html.enabled
    }

    def "configure every jacoco task to run with unit and integration tests data"() {
        given: "project with local plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        expect:
        def integrationTestTask = project.tasks.getByName(LocalPluginsPlugin.INTEGRATION_TEST_TASK_NAME)
        def testTask = project.tasks.getByName(JavaPlugin.TEST_TASK_NAME)
        project.tasks.withType(JacocoReport).each { jacocoTask ->
            def runAfterTasks = jacocoTask.getMustRunAfter().getDependencies(jacocoTask)
            return runAfterTasks.contains(integrationTestTask) && runAfterTasks.contains(testTask)
        }
    }


    def "configures sonarqube extension with default property values if none provided"() {
        given: "sample src and test folders"
        def srcFolder = createSrcFile("src/main/groovy/", "Hello.groovy")
        def testFolder = createSrcFile("src/test/groovy/", "HelloTest.groovy")
        def intTestFolder = createSrcFile("src/integrationTest/groovy/", "HelloIntegration.groovy")

        and: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)
        project.evaluate()

        expect:
        def sonarTask = project.tasks.getByName(SonarQubeExtension.SONARQUBE_TASK_NAME) as SonarQubeTask
        def properties = sonarTask.getProperties()

        //sonar.host.url is not here as CI always will have SONAR_HOST set, so it is never null there.
        properties["sonar.login"] == null
        properties["sonar.projectKey"] == project.name
        properties["sonar.projectName"] == project.name
        properties["sonar.sources"] == srcFolder.absolutePath
        properties["sonar.tests"].split(",").length == 2
        properties["sonar.tests"].split(",").contains(testFolder.absolutePath)
        properties["sonar.tests"].split(",").contains(intTestFolder.absolutePath)
        properties["sonar.jacoco.reportPaths"] == "build/jacoco/integrationTest.exec,build/jacoco/test.exec"
    }

    @Unroll("configures sonarqube extension with project property #propertyName if provided")
    def "configures sonarqube extension with project property values if provided"(String propertyName, String value) {
        given: "project with set sonar properties"
        project.ext[propertyName] = value

        and: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)
        project.evaluate()

        expect:
        SonarQubeTask sonarTask = project.tasks.getByName(SonarQubeExtension.SONARQUBE_TASK_NAME) as SonarQubeTask
        sonarTask.properties[propertyName] == project.property(propertyName)

        where:
        propertyName               | value
        "sonar.projectName"        | "project-name"
        "sonar.projectKey"         | "sonar_Project-name"
        "sonar.host.url"           | "https://sonar.host.tld"
        "sonar.login"              | "<<login_token>>"
        "sonar.sources"            | "source/folder"
        "sonar.tests"              | "test/folder"
        "sonar.jacoco.reportPaths" | "jacoco/report.exec"
    }

    def "configures test report html output"() {
        given: "project with local plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        expect:
        def reporting = project.extensions.getByName(ReportingExtension.NAME) as ReportingExtension
        project.tasks.withType(Test).all { Test task ->
            task.reports.html.destination == new File(projectDir, "${reporting.baseDir}/${task.name}")
        }
    }

    def "will force groovy modules to local groovy version"() {
        given: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        expect:
        def localGroovy = GroovySystem.getVersion()
        project.configurations.every {
            //we turn the list of force modules to string to not test against gradle internals
            def forcedModules = it.resolutionStrategy.forcedModules.toList().collect { it.toString() }
            forcedModules.containsAll([
                    "org.codehaus.groovy:groovy-all:${localGroovy}".toString(),
                    "org.codehaus.groovy:groovy-macro:${localGroovy}".toString(),
                    "org.codehaus.groovy:groovy-nio:${localGroovy}".toString(),
                    "org.codehaus.groovy:groovy-sql:${localGroovy}".toString(),
                    "org.codehaus.groovy:groovy-xml:${localGroovy}".toString()
            ]
            )
        }
    }

    @Unroll("setups #repoName as repository")
    def "setups #repoName as repository"() {
        given: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        expect:
        project.repositories.asMap.containsKey(repoName)

        where:
        repoName << ["MavenRepo", "Gradle Central Plugin Repository"]
    }

    @Unroll("setups #dependencyString as #scope dependency")
    def "setups #dependency as #scope dependency"() {
        given: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        and:
        def actualGroup = dependencyString.split(":")[0]
        def actualName = dependencyString.split(":")[1]
        def dependency = project.configurations[scope].dependencies.find { it.name == actualName }

        expect:
        dependency != null
        dependency.group == actualGroup
        dependency.version == version

        where:
        scope                           | dependencyString                        | version
        "implementation"                | "commons-io:commons-io"                 | "[2,3)"
        "testImplementation"            | "junit:junit"                           | "[4,5)"
        "testImplementation"            | "org.spockframework:spock-core"         | "1.3-groovy-2.5"
        "testImplementation"            | "com.netflix.nebula:nebula-test"        | "[8,9)"
        "testImplementation"            | "com.github.stefanbirkner:system-rules" | "[1,2)"
    }

    def "setups gradleAPI as API dependency"() {
        given: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        expect:
        def dependencies = project.configurations["api"].dependencies
        dependencies.contains(project.dependencies.gradleApi())
    }

    File createSrcFile(String folderStr, String filename) {
        File folder = new File(projectDir, folderStr)
        folder.mkdirs()
        File srcFile = new File(folder, filename)
        srcFile.createNewFile()
        srcFile << """\
            class ${filename.split("\\.")[0]} {
            }
            """.stripIndent()
        return folder
    }

}