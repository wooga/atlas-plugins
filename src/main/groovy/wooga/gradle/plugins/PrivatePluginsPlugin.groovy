package wooga.gradle.plugins


import org.ajoberstar.grgit.gradle.GrgitPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.tasks.TaskContainer
import org.gradle.language.base.plugins.LifecycleBasePlugin
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

class PrivatePluginsPlugin implements Plugin<Project> {

    static final String RELEASE_NOTES_TASK_NAME = "releaseNotes"

    @Override
    void apply(Project project) {
        project.pluginManager.with {
            apply(LocalPluginsPlugin)
            apply(VersionPlugin)
            apply(GithubPlugin)
            apply(GrgitPlugin)
            apply(GithubReleaseNotesPlugin)
        }
        configureVersionPluginExtension(project)
        configureReleaseNotes(project)
        configureGithubPublishTask(project)
        configureTaskRuntimeDependencies(project)
    }

    private static void configureReleaseNotes(Project project) {
        def releaseNotesProvider = project.tasks.register(RELEASE_NOTES_TASK_NAME, GenerateReleaseNotes)
        releaseNotesProvider.configure { task ->
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
                task.branch.set(project.extensions.grgit.branch.current.name as String)
                task.output.set(new File("${project.buildDir}/outputs/release-notes.md"))
                task.strategy.set(new ReleaseNotesStrategy())
            }
        }
    }

    private static void configureVersionPluginExtension(Project project) {
        def versionExt = project.extensions.findByType(VersionPluginExtension)
        if (versionExt) {
            versionExt.versionScheme.set(VersionScheme.semver2)
            versionExt.versionCodeScheme.set(VersionCodeScheme.releaseCount)
        }
    }


    private static configureTaskRuntimeDependencies(final Project project) {
        TaskContainer tasks = project.tasks

        Task finalTask = project.tasks.create("final")
        Task rcTask = project.tasks.create("rc")
        Task snapshotTask = project.tasks.create("snapshot")

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

    private static void configureGithubPublishTask(Project project) {
        def tasks = project.tasks
        def releaseNotesTask = tasks.getByName(RELEASE_NOTES_TASK_NAME) as GenerateReleaseNotes
        def publishTaskProvider = tasks.named(GithubPublishPlugin.PUBLISH_TASK_NAME)
        publishTaskProvider.configure { GithubPublish githubPublishTask ->
            githubPublishTask.onlyIf(new ProjectStatusTaskSpec("rc", "final"))
            githubPublishTask.with {
                releaseName.set(project.provider { project.version.toString() })
                tagName.set(project.provider { "v${project.version}" })
                targetCommitish.set(project.extensions.grgit.branch.current.name as String)
                prerelease.set(project.properties['release.stage'] != 'final')
                body.set(releaseNotesTask.output.map { it.asFile.text })
            }
        }

    }
}
