package wooga.gradle.plugins

import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.gradle.GrgitPlugin
import org.gradle.api.Task
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.sonarqube.gradle.SonarQubeExtension
import org.sonarqube.gradle.SonarQubeTask
import spock.lang.Unroll
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

class PrivatePluginsPluginSpec extends LocalPluginsPluginSpec {

    public static final String PLUGIN_NAME = 'net.wooga.plugins-private'

    Grgit git

    def setup() {
        git = Grgit.init(dir: projectDir)
        git.commit(message: 'initial commit')
        git.tag.add(name: "v0.0.1")
    }

    @Unroll("applies plugin #pluginName")
    def 'Applies other plugins'() {
        given:
        assert !project.plugins.hasPlugin(PLUGIN_NAME)
        assert !project.plugins.hasPlugin(pluginType)

        when:
        project.plugins.apply(PLUGIN_NAME)

        then:
        project.plugins.hasPlugin(pluginType)

        where:
        pluginName             | pluginType
        "local "               | LocalPluginsPlugin
        "version"              | VersionPlugin
        "grgit"                | GrgitPlugin
        "net.wooga.github"     | GithubPlugin
        "github-release-notes" | GithubReleaseNotesPlugin
    }

    @Unroll("creates the task #taskName")
    def 'Creates needed tasks'() {
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
        PrivatePluginsPlugin.RELEASE_NOTES_TASK_NAME | GenerateReleaseNotes
        LifecycleBasePlugin.CHECK_TASK_NAME          | Task
        PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME | Task
        GithubPublishPlugin.PUBLISH_TASK_NAME        | GithubPublish
    }

    @Unroll("task #taskName has runtime dependencies")
    def 'Task has runtime dependencies'() {
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
        taskName                                        | dependencies
        GithubPublishPlugin.PUBLISH_TASK_NAME           | [PrivatePluginsPlugin.RELEASE_NOTES_TASK_NAME]
        PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME    | [LifecycleBasePlugin.CHECK_TASK_NAME,
                                                           GithubPublishPlugin.PUBLISH_TASK_NAME]
        PrivatePluginsPlugin.FINAL_PUBLISH_TASK_NAME    | [PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME]
        PrivatePluginsPlugin.RC_PUBLISH_TASK_NAME       | [PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME]
        PrivatePluginsPlugin.SNAPSHOT_PUBLISH_TASK_NAME | [LifecycleBasePlugin.CHECK_TASK_NAME,
                                                           MavenPublishPlugin.PUBLISH_LOCAL_LIFECYCLE_TASK_NAME]
        PrivatePluginsPlugin.RC_PUBLISH_TASK_NAME       | [PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME]
    }

    def "configure version plugin with default values"() {
        given: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

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

        and: "existing version extension in the plugin"
        VersionPluginExtension versionExt = project.extensions.getByType(VersionPluginExtension)

        when: "setting extension properties to distinct values"
        versionExt.versionScheme("staticMarker")
        versionExt.versionCodeScheme("releaseCountBasic")

        then: "values should be the ones that has been set"
        versionExt.versionScheme.get() == VersionScheme.staticMarker
        versionExt.versionCodeScheme.get() == VersionCodeScheme.releaseCountBasic
    }

    def "configures release notes plugin"() {
        given: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        expect:
        def releaseNotesTask = project.tasks.getByName(PrivatePluginsPlugin.RELEASE_NOTES_TASK_NAME) as GenerateReleaseNotes
        releaseNotesTask.from.get() == "v0.0.1"
        releaseNotesTask.branch.get() == git.branch.current().name
        releaseNotesTask.output.asFile.get() == new File(project.buildDir, "outputs/release-notes.md")
        releaseNotesTask.strategy.get() instanceof ReleaseNotesStrategy
    }

    def "overrides sonarqube extension local property values with remote if none provided"() {
        given: "configured github plugin"
        def ghCompany = "company"
        def ghRepoName = "repoName"
        if (!ghCompany.empty && !ghRepoName.empty) {
            project.ext["github.repositoryName"] = "${ghCompany}/${ghRepoName}"
        }

        and: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        expect:
        def sonarTask = project.tasks.getByName(SonarQubeExtension.SONARQUBE_TASK_NAME) as SonarQubeTask
        def properties = sonarTask.getProperties()

        properties["sonar.projectKey"] == "${ghCompany}_${ghRepoName}"
        properties["sonar.projectName"] == ghRepoName
        properties["sonar.branch.name"] == "master"
    }

    @Unroll
    def "sets sonarqube branch name from github extension, except if branch is from PR"() {
        given: "set git branch name"
        project.ext["github.branch.name"] = branchName

        and: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        expect:
        def sonarTask = project.tasks.getByName(SonarQubeExtension.SONARQUBE_TASK_NAME) as SonarQubeTask
        def properties = sonarTask.getProperties()

        properties["sonar.branch.name"] == expected

        where:
        branchName | expected
        "PR-123"   | null
        "PR-abc"   | "PR-abc"
        "branch"   | "branch"
        ""         | null
    }

    def "configures github publish task"() {
        given: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        and: "switch current branch"
        getGit().checkout(branch: 'test_branch', createBranch: true, startPoint: getGit().resolve.toRevisionString(getGit().branch.current().fullName))

        when: "evaulating github publish task"
        def ghPublishTask = project.tasks.getByName(GithubPublishPlugin.PUBLISH_TASK_NAME) as GithubPublish

        then: "github publish task should be configured"
        ghPublishTask.targetCommitish.get() == 'test_branch'
        ghPublishTask.releaseName.get() == project.version.toString()
        ghPublishTask.tagName.get() == "v${project.version}"
        ghPublishTask.targetCommitish.get() == project.extensions.grgit.branch.current.name as String
        ghPublishTask.prerelease.get() == (project.properties['release.stage'] != 'final')
    }

    def "configures github release notes task"() {
        given: "project with plugins plugin applied"
        project.plugins.apply(PLUGIN_NAME)

        and: "switch current branch"
        getGit().checkout(branch: 'test_branch', createBranch: true, startPoint: getGit().resolve.toRevisionString(getGit().branch.current().fullName))

        when: "evaluating github release notes task"
        def releaseNotes = project.tasks.getByName(PrivatePluginsPlugin.RELEASE_NOTES_TASK_NAME) as GenerateReleaseNotes

        then: "github publish task should be configured"
        releaseNotes.branch.get() == 'test_branch'
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
