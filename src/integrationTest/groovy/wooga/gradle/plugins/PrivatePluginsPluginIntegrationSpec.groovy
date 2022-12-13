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

import com.wooga.spock.extensions.github.GithubRepository
import com.wooga.spock.extensions.github.Repository
import com.wooga.spock.extensions.github.api.RateLimitHandlerWait
import com.wooga.spock.extensions.github.api.TravisBuildNumberPostFix
import org.ajoberstar.grgit.Grgit
import spock.lang.Shared
import spock.lang.Unroll

class PrivatePluginsPluginIntegrationSpec extends LocalPluginsPluginIntegrationSpec {

    @Shared
    @GithubRepository(
            usernameEnv = "ATLAS_GITHUB_INTEGRATION_USER",
            tokenEnv = "ATLAS_GITHUB_INTEGRATION_PASSWORD",
            resetAfterTestCase = false,
            repositoryNamePrefix = "atlas-github-plugins-integration",
            repositoryPostFixProvider = TravisBuildNumberPostFix.class,
            rateLimitHandler = RateLimitHandlerWait
    )
    Repository repo
    Grgit git

    def setupSpec() {
        repo.commit('initial commit')
        repo.createRelease("0.0.1", "v0.0.1")
    }

    def setup() {
        environmentVariables.set("GITHUB_LOGIN", repo.userName)
        environmentVariables.set("GITHUB_PASSWORD", repo.token)
        def remote = "origin"
        git = Grgit.init(dir: projectDir)
        git.remote.add(name: remote, url: repo.httpTransportUrl)
        git.fetch(remote: remote)
        git.branch.add(name: repo.defaultBranch.name, startPoint: "${remote}/${repo.defaultBranch.name}")
        git.checkout(branch: repo.defaultBranch.name)
        git.pull(remote: "${remote}", branch: repo.defaultBranch.name)

        createFile(".gitignore") << """
        **/*
        """.stripIndent()

        buildFile << """
        
        group = 'test'
        ${applyPlugin(PrivatePluginsPlugin)}

        repositories {
            jcenter()
            mavenCentral()
            maven {
                url "https://plugins.gradle.org/m2/"
            }
        }

        github {
            repositoryName = "${repo.fullName}"
        }

        dependencies {
            testImplementation('junit:junit:4.11')
        }

        """.stripIndent()
    }

    @Unroll
    def "task :#lifecycleTask runs '#tasksToRun'"() {
        given: "some dummy test"
        writeTest('src/integrationTest/java/', "wooga.integration", false)
        writeTest('src/test/java/', "wooga.test", false)

        when:
        def result = runTasks(lifecycleTask, "--dry-run")

        then:
        tasksToRun.every { taskToRun ->
            result.standardOutput.contains(":$taskToRun")
        }

        where:
        tasksToRun                                      | lifecycleTask
        ["releaseNotes"]                                | "githubPublish"
        ["check", "githubPublish", "publishGroovydocs"] | "publish"
        ["publish"]                                     | "final"
        ["publish"]                                     | "rc"
        ["check", "publishToMavenLocal"]                | "snapshot"
    }

    @Unroll
    def "task #lifecycleTask #skip in stage #releaseStage"() {
        given: "some dummy test"
        writeTest('src/integrationTest/java/', "wooga.integration", false)
        writeTest('src/test/java/', "wooga.test", false)

        and: "dummying the task to be executed, as we only want to know if it would be skipped or not"
        buildFile << """
        project.gradle.taskGraph.whenReady {
            gradle.taskGraph.allTasks.each {it.setActions([])}
        }
        """.stripIndent()

        when:
        def result = runTasks(lifecycleTask, "-Prelease.stage=${releaseStage}")

        then:
        skip == "skip" ?
                result.standardOutput.contains("${lifecycleTask} SKIPPED") :
                !result.wasSkipped(lifecycleTask)

        where:
        lifecycleTask    | releaseStage | skip
        "githubPublish" | "final"      | "don't skip"
        "githubPublish" | "rc"         | "don't skip"
        "githubPublish" | "snapshot"   | "skip"
        "releaseNotes"  | "final"      | "don't skip"
        "releaseNotes"  | "rc"         | "don't skip"
        "releaseNotes"  | "snapshot"   | "skip"
    }

    @Unroll
    def "verify :#taskAfter runs after :#task when execute '#execute'"() {
        given: "some dummy test"
        writeTest('src/integrationTest/java/', "wooga.integration", false)
        writeTest('src/test/java/', "wooga.test", false)

        when:
        def result = runTasksSuccessfully(*(execute << "--dry-run"))

        then:
        result.standardOutput.indexOf(":$taskAfter ") > result.standardOutput.indexOf(":$task ")

        where:
        taskAfter             | task             | execute
        "publishToMavenLocal" | "check"          | ["publishToMavenLocal", "snapshot"]
    }

    void writePluginMetaFile(String pluginID, String pluginClassname, File baseDir = getProjectDir()) {
        String path = "src/main/resources/META-INF/gradle-plugins/${pluginID}.properties"
        def javaFile = createFile(path, baseDir)
        javaFile << """implementation-class=${pluginClassname}""".stripIndent()

    }

    def writeHelloWorldGroovy(String packageDotted, File baseDir = getProjectDir()) {
        def path = 'src/main/groovy/' + packageDotted.replace('.', '/') + '/HelloWorld.groovy'
        def javaFile = createFile(path, baseDir)
        javaFile << """\
            package ${packageDotted};
        
            class HelloWorld {
            }
            """.stripIndent()
        return "${packageDotted}.HelloWorld"
    }

}
