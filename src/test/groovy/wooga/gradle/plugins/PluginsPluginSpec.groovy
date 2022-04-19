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
import com.wooga.spock.extensions.github.GithubRepository
import com.wooga.spock.extensions.github.Repository
import com.wooga.spock.extensions.github.api.RateLimitHandlerWait
import com.wooga.spock.extensions.github.api.TravisBuildNumberPostFix
import nebula.test.ProjectSpec
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.gradle.GrgitPlugin
import org.gradle.api.Plugin
import org.gradle.api.Task
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.kt3k.gradle.plugin.CoverallsPlugin
import org.sonarqube.gradle.SonarQubeExtension
import org.sonarqube.gradle.SonarQubePlugin
import org.sonarqube.gradle.SonarQubeTask
import spock.lang.Shared
import spock.lang.Unroll
import wooga.gradle.github.GithubPlugin
import wooga.gradle.github.publish.GithubPublishPlugin
import wooga.gradle.github.publish.tasks.GithubPublish
import wooga.gradle.githubReleaseNotes.GithubReleaseNotesPlugin
import wooga.gradle.githubReleaseNotes.tasks.GenerateReleaseNotes
import wooga.gradle.version.VersionCodeScheme
import wooga.gradle.version.VersionPlugin
import wooga.gradle.version.VersionPluginExtension
import wooga.gradle.version.VersionScheme

class PluginsPluginSpec extends ProjectSpec {
    public static final String PLUGIN_NAME = 'net.wooga.plugins'

    @Shared
    @GithubRepository(
            usernameEnv = "ATLAS_GITHUB_INTEGRATION_USER",
            tokenEnv = "ATLAS_GITHUB_INTEGRATION_PASSWORD",
            resetAfterTestCase = false,
            repositoryNamePrefix = "atlas-github-plugins",
            repositoryPostFixProvider = TravisBuildNumberPostFix.class,
            rateLimitHandler = RateLimitHandlerWait
    )
    Repository repo
    Grgit git

    def setup() {
        git = Grgit.init(dir: projectDir)
        initGitRepository(repo)
    }

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
        pluginName             | pluginType
        "groovy"               | GroovyPlugin
        "idea"                 | IdeaPlugin
        "maven-publish"        | MavenPublishPlugin
        "plugin-publish"       | PublishPlugin
        "jacoco"               | JacocoPlugin
        "coveralls"            | CoverallsPlugin
        "version"              | VersionPlugin
        "grgit"                | GrgitPlugin
        "net.wooga.github"     | GithubPlugin
        "github-release-notes" | GithubReleaseNotesPlugin
        "sonarqube"            | SonarQubePlugin
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
        taskName                                     | taskType
        PluginsPlugin.INTEGRATION_TEST_TASK_NAME     | Test
        PluginsPlugin.PUBLISH_GROOVY_DOCS_TASK_NAME  | Sync
        PluginsPlugin.RELEASE_NOTES_TASK_NAME        | GenerateReleaseNotes
        LifecycleBasePlugin.CHECK_TASK_NAME          | Task
        LifecycleBasePlugin.ASSEMBLE_TASK_NAME       | Task
        PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME | Task
        GithubPublishPlugin.PUBLISH_TASK_NAME        | GithubPublish
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
        taskName                              | dependencies
        GithubPublishPlugin.PUBLISH_TASK_NAME | [PluginsPlugin.RELEASE_NOTES_TASK_NAME]
    }

    def "configures integration test task"() {
        given: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        and: "the integration test task"
        Test integrationTestTask = project.tasks.getByName(PluginsPlugin.INTEGRATION_TEST_TASK_NAME) as Test

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

    def "configures sonarqube extension with default property values if none provided"() {
        given: "configured github plugin with repository property"
        project.ext["github.repositoryName"] = repo.fullName

        and: "sample src and test folders"
        def srcFolder = createSrcFile("src/main/groovy/", "Hello.groovy")
        def testFolder = createSrcFile("src/test/groovy/", "HelloTest.groovy")
        def intTestFolder = createSrcFile("src/integrationTest/groovy/", "HelloIntegration.groovy")

        and: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        when: "after project evaluate"
        project.evaluate()

        then:
        SonarQubeTask sonarTask = project.tasks.getByName(SonarQubeExtension.SONARQUBE_TASK_NAME) as SonarQubeTask
        def properties = sonarTask.properties

        //sonar.host.url is not here as CI always will have SONAR_HOST set, so it is never null there.
        properties["sonar.login"] == null
        properties["sonar.projectKey"] == "${repo.ownerName}_${repo.name}"
        properties["sonar.projectName"] == repo.name
        properties["sonar.branch.name"] == git.branch.current().trackingBranch.name
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
        project.ext["github.repositoryName"] = repo.fullName

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
        "sonar.branch.name"        | "branchname"
        "sonar.host.url"           | "https://sonar.host.tld"
        "sonar.login"              | "<<login_token>>"
        "sonar.sources"            | "source/folder"
        "sonar.tests"              | "test/folder"
        "sonar.jacoco.reportPaths" | "jacoco/report.exec"
    }

    def "configure version extension with default values"() {
        given: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)
        project.evaluate()

        expect: "version extension to exist"
        VersionPluginExtension versionExt = project.extensions.getByType(VersionPluginExtension)
        versionExt != null
        and: "Version scheme to be semver2"
        versionExt.versionScheme.get() == VersionScheme.semver2
        and: "Version code scheme to be releaseCount"
        versionExt.versionCodeScheme.get() == VersionCodeScheme.releaseCount
    }

    def "override version extension default values with custom ones"() {
        given: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)
        project.evaluate()

        and: "existing version extension in the plugin"
        VersionPluginExtension versionExt = project.extensions.getByType(VersionPluginExtension)

        when: "setting extension properties to distinct values"
        versionExt.versionScheme("staticMarker")
        versionExt.versionCodeScheme("releaseCountBasic")

        then: "values should be the ones that has been set"
        versionExt.versionScheme.get() == VersionScheme.staticMarker
        versionExt.versionCodeScheme.get() == VersionCodeScheme.releaseCountBasic
    }

    def "configures github publish task"() {
        given: "configured github plugin with branch name property"
        project.ext["github.repositoryName"] = repo.fullName

        and: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)
        project.evaluate()

        when: "evaulating github publish task"
        def ghPublishTask = project.tasks.getByName(GithubPublishPlugin.PUBLISH_TASK_NAME) as GithubPublish

        then: "github publish task should be configured"
        ghPublishTask.releaseName.get() == project.version.toString()
        ghPublishTask.targetCommitish.get() == getGit().branch.current().name
        ghPublishTask.tagName.get() == "v${project.version}"
        ghPublishTask.prerelease.get() == (project.properties['release.stage'] != 'final')
    }

    @Unroll("configures github release notes task for git repo #description")
    def "configures github release notes task for git repo"() {
        given: "configured github plugin with branch name property"
        project.ext["github.repositoryName"] = repo.fullName

        and: "switch current branch"
        git.checkout(branch: 'test_branch', createBranch: true, startPoint: getGit().resolve.toRevisionString(getGit().branch.current().fullName))

        and: "adds release tag if needed"
        if (existingReleaseTag != null) {
            git.tag.add(name: existingReleaseTag)
        }

        and: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)
        project.evaluate()

        when: "evaluating github release notes task"
        def releaseNotes = project.tasks.getByName(PluginsPlugin.RELEASE_NOTES_TASK_NAME) as GenerateReleaseNotes

        then: "github publish task should be configured"
        releaseNotes.branch.get() == 'test_branch'
        !releaseNotes.to.present
        releaseNotes.from.orNull == expectedFrom
        where:
        existingReleaseTag | expectedFrom | description
        "v0.0.1"           | "v0.0.1"     | "with existing release"
        null               | null         | "without existing release"
    }

    def "will force groovy modules to local groovy version"() {
        given: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        expect:
        def localGroovy = GroovySystem.getVersion()
        project.configurations.every {
            //we turn the list of force modules to string to not test against gradle internals
            def forcedModules = it.resolutionStrategy.forcedModules.toList().collect { it.toString() }
            forcedModules.containsAll(
                    [
                            "org.codehaus.groovy:groovy-all:${localGroovy}".toString(),
                            "org.codehaus.groovy:groovy-macro:${localGroovy}".toString(),
                            "org.codehaus.groovy:groovy-nio:${localGroovy}".toString(),
                            "org.codehaus.groovy:groovy-sql:${localGroovy}".toString(),
                            "org.codehaus.groovy:groovy-xml:${localGroovy}".toString()
                    ]
            )
        }
    }

    def initGitRepository(Repository repo) {
        def remote = "origin"
        git.remote.add(name: remote, url: repo.httpTransportUrl)
        git.fetch(remote: remote)
        if (!git.branch.list().find({ it.name == repo.defaultBranch.name })) {
            git.branch.add(name: repo.defaultBranch.name, startPoint: "${remote}/${repo.defaultBranch.name}")
        }
        git.checkout(branch: repo.defaultBranch.name)
        git.pull(remote: "${remote}", branch: repo.defaultBranch.name)
        git.commit(message: 'initial commit')
    }

    def createSrcFile(String folderStr, String filename) {
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
