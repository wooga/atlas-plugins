package wooga.gradle.plugins

import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.gradle.GrgitPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.tasks.TaskContainer
import org.gradle.language.base.plugins.LifecycleBasePlugin
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

class PrivatePluginsPlugin implements Plugin<Project> {

    static final String RELEASE_NOTES_TASK_NAME = "releaseNotes"
    static final String FINAL_PUBLISH_TASK_NAME = "final"
    static final String RC_PUBLISH_TASK_NAME = "rc"
    static final String SNAPSHOT_PUBLISH_TASK_NAME = "snapshot"

    @Override
    void apply(Project project) {
        project.pluginManager.with {
            apply(LocalPluginsPlugin)
            apply(VersionPlugin)
            apply(GithubPlugin)
            apply(GrgitPlugin)
            apply(GithubReleaseNotesPlugin)
        }
        configureVersionPlugin(project)
        configureReleaseNotes(project)
        configureGithubPublish(project)
        configureSonarQube(project, SonarQubeConfiguration.withEnvVarPropertyFallback(project))

        configurePublishTasksRuntimeDependencies(project)
    }

    private static void configureVersionPlugin(Project project) {
        def versionExt = project.extensions.findByType(VersionPluginExtension)
        if (versionExt) {
            versionExt.versionScheme.set(VersionScheme.semver2)
            versionExt.versionCodeScheme.set(VersionCodeScheme.releaseCount)
        }
    }

    private static void configureReleaseNotes(Project project) {
        def grgit = project.extensions.getByName('grgit') as Grgit
        project.tasks.register(RELEASE_NOTES_TASK_NAME, GenerateReleaseNotes) { task ->
            task.onlyIf(new ProjectStatusTaskSpec("rc", "final"))
            def versionExt = project.extensions.findByType(VersionPluginExtension)
            if (versionExt) {
                task.from.set(versionExt.version.map { version ->
                    if (version.previousVersion) {
                        return "v${version.previousVersion}"
                    } else {
                        return null
                    }
                })
                task.branch.set(project.provider({
                    grgit.branch.current().name
                }))
                task.output.set(new File(project.buildDir, "/outputs/release-notes.md"))
                task.strategy.set(new ReleaseNotesStrategy())
            }
        }
    }

    private static void configureGithubPublish(Project project) {
        def tasks = project.tasks
        def releaseNotesTask = tasks.getByName(RELEASE_NOTES_TASK_NAME) as GenerateReleaseNotes
        def publishTaskProvider = tasks.named(GithubPublishPlugin.PUBLISH_TASK_NAME)
        def grgit = project.extensions.getByName('grgit') as Grgit
        publishTaskProvider.configure { GithubPublish githubPublishTask ->
            githubPublishTask.onlyIf(new ProjectStatusTaskSpec("rc", "final"))
            githubPublishTask.with {
                releaseName.set(project.provider { project.version.toString() })
                tagName.set(project.provider { "v${project.version}" })
                targetCommitish.set(project.provider({grgit.branch.current().name}))
                prerelease.set(project.properties['release.stage'] != 'final')
                body.set(releaseNotesTask.output.map { it.asFile.text })
            }
        }
    }

    private static configureSonarQube(final Project project,
                                      SonarQubeConfiguration sonarConfig) {
        def githubExt = project.extensions.getByType(GithubPluginExtension)
        def sonarExt = project.rootProject.extensions.getByType(SonarQubeExtension)
        def javaConvention = project.extensions.findByType(JavaPluginExtension)

        sonarExt.properties(sonarConfig.generateSonarProperties(githubExt.repositoryName,
                                                                githubExt.branchName, javaConvention))
        project.rootProject.tasks.named(SonarQubeConfiguration.TASK_NAME) {sonarTask ->
            sonarTask.onlyIf {
                System.getenv('CI')
            }
        }
    }

    private static configurePublishTasksRuntimeDependencies(final Project project) {
        TaskContainer tasks = project.tasks

        Task finalTask = project.tasks.create(FINAL_PUBLISH_TASK_NAME)
        Task rcTask = project.tasks.create(RC_PUBLISH_TASK_NAME)
        Task snapshotTask = project.tasks.create(SNAPSHOT_PUBLISH_TASK_NAME)

        Task publishToLocalMavenTask = tasks.getByName(MavenPublishPlugin.PUBLISH_LOCAL_LIFECYCLE_TASK_NAME)
        Task checkTask = tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME)
        Task publishTask = tasks.getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME)
        Task releaseNotesTask = tasks.getByName(RELEASE_NOTES_TASK_NAME)
        Task githubPublishTask = tasks.getByName(GithubPublishPlugin.PUBLISH_TASK_NAME)


        finalTask.dependsOn publishTask
        rcTask.dependsOn publishTask
        snapshotTask.dependsOn checkTask, publishToLocalMavenTask
        publishToLocalMavenTask.mustRunAfter checkTask
        publishTask.dependsOn checkTask, githubPublishTask

        githubPublishTask.dependsOn releaseNotesTask
    }
}
