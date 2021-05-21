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
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Unroll

class PluginsPluginIntegrationSpec extends IntegrationSpec {

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
        ${applyPlugin(PluginsPlugin)}

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
    def "never be [UP-TO-DATE] for task :#taskToRun"(String taskToRun) {
        given: "a dummy test"
        writeTest('src/integrationTest/java/', "wooga", false)

        when: "running task 2x times"
        runTasksSuccessfully(taskToRun)
        def result = runTasksSuccessfully(taskToRun)

        then: "should never be [UP-TO-DATE]"
        !result.wasUpToDate(taskToRun)

        where:
        taskToRun << ["integrationTest"]
    }

    @Unroll
    def "task :#lifecycleTask runs '#tasksToRun'"() {
        given: "some dummy test"
        writeTest('src/integrationTest/java/', "wooga.integration", false)
        writeTest('src/test/java/', "wooga.test", false)

        when:
        def result = runTasks(lifecycleTask, "--dry-run")

        then:
        tasksToRun.every {taskToRun ->
            result.standardOutput.contains(":$taskToRun")
        }

        where:
        tasksToRun                                                           | lifecycleTask
        ["integrationTest", "test"]                                          | "check"
        ["releaseNotes"]                                                     | "githubPublish"
        ["check", "publishPlugins", "githubPublish", "publishGroovydocs"]    | "publish"
        ["publish"]                                                          | "final"
        ["publish"]                                                          | "rc"
        ["check", "publishToMavenLocal"]                                     | "snapshot"
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
        taskAfter             | task              | execute
        "integrationTest"     | "test"            | ["check"]
        "integrationTest"     | "test"            | ["integrationTest", "test"]
        "integrationTest"     | "test"            | ["check", "integrationTest", "test"]
        "githubPublish"       | "publishPlugins"  | ["publishPlugins", "publish", "final", "rc"]
        "publishPlugins"      | "check"           | ["publishPlugins", "publish", "final", "rc"]
        "publishToMavenLocal" | "check"           | ["publishToMavenLocal", "snapshot"]
    }

    //Test tasks hangs on windows systems
    //Ignore for now
    @IgnoreIf({ System.getProperty("os.name").toLowerCase().contains("windows") })
    @Unroll
    def "task :#taskToRun saves reports to #expectedOutput"() {
        given: "some dummy test"
        writeTest('src/integrationTest/java/', "wooga.integration", false)
        writeTest('src/test/java/', "wooga.test", false)

        and: "future reports dir"
        def reportDir = new File(projectDir, expectedOutput)
        assert !reportDir.exists()

        when:
        runTasksSuccessfully(taskToRun)

        then:
        reportDir.exists()

        where:
        taskToRun         | expectedOutput
        "test"            | "build/reports/test"
        "integrationTest" | "build/reports/integrationTest"
    }

    //Test tasks hangs on windows systems
    //Ignore for now
    @IgnoreIf({ System.getProperty("os.name").toLowerCase().contains("windows") })
    @Unroll
    def "task :#taskToRun saves jococo exec binaries to #expectedOutput"() {
        given: "some dummy test"
        fork = true
        writeTest('src/integrationTest/java/', "wooga.integration", false)
        writeTest('src/test/java/', "wooga.test", false)

        and: "future exec file"
        def execFile = new File(projectDir, expectedOutput)
        assert !execFile.exists()

        when:
        runTasksSuccessfully(taskToRun)

        then:
        execFile.exists()

        where:
        taskToRun         | expectedOutput
        "test"            | "build/jacoco/test.exec"
        "integrationTest" | "build/jacoco/integrationTest.exec"
    }

    @Unroll
    def "task :#taskToRun generates #output_type reports"() {
        given: "some dummy test"
        fork = true

        writeTest('src/integrationTest/java/', "wooga.integration", false)
        writeTest('src/test/java/', "wooga.test", false)

        and: "future exec file"
        def outputFile = new File(projectDir, expectedOutput)
        assert !outputFile.exists()

        and: "a test run"
        runTasksSuccessfully("check")

        when:
        runTasksSuccessfully(taskToRun)

        then:
        outputFile.exists()

        where:
        taskToRun          | output_type | expectedOutput
        "jacocoTestReport" | "xml"       | "build/reports/jacoco/test/jacocoTestReport.xml"
        "jacocoTestReport" | "html"      | "build/reports/jacoco/test/html"

    }

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

    @Unroll
    def "task :#taskToRun #message with System.getenv('#env') #value"() {
        given:
        environmentVariables.set(env, value)
        environmentVariables

        when:
        def result = runTasksSuccessfully(taskToRun)

        then:
        result.wasSkipped(taskToRun) == skipped

        where:
        taskToRun   | skipped | env  | value
        "coveralls" | false   | 'CI' | "TRUE"
        "coveralls" | false   | 'CI' | "1"
        "coveralls" | false   | 'CI' | "0"
        "coveralls" | false   | 'CI' | "some value"
        "coveralls" | true    | 'CI' | null

        message = skipped ? "should skip" : "shouldn't skip"
    }

    @Unroll
    def "task publishGroovydocs publish API docs with correct settings"() {
        given: "a future output directory"
        fork = true
        def docsExportDir = new File(projectDir, PluginsPlugin.DOC_EXPORT_DIR)
        def classDoc = new File(docsExportDir, "net/wooga/plugins/test/HelloWorld.html")

        assert !docsExportDir.exists()

        and: "a temp java file"
        writeHelloWorldGroovy("net.wooga.plugins.test")

        and: "a gradle plugin bundle configured"
        buildFile << """
        pluginBundle {
            website = 'https://plugins.com/wooga/atlas-test'
            vcsUrl = 'https://github.com/wooga/atlas-test'
            tags = ['plugins', 'test', 'internal']
        
            plugins {
                plugins {
                    id = 'net.wooga.test'
                    displayName = 'Integration Test'
                    description = 'This plugin provides no value'
                }
            }
        }
        """.stripIndent()

        when:
        runTasksSuccessfully(taskToRun)

        then:
        classDoc.text.contains "Integration Test API"
        classDoc.text.contains "<!-- Generated by groovydoc -->"

        where:
        taskToRun           | _
        "publishGroovydocs" | _
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
