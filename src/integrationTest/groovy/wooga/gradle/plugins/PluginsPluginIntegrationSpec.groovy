/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package wooga.gradle.plugins

import nebula.plugin.release.ReleasePlugin
import nebula.test.IntegrationSpec
import org.ajoberstar.grgit.Grgit
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import spock.lang.Unroll

class PluginsPluginIntegrationSpec extends IntegrationSpec {

    Grgit git

    def setup() {
        git = Grgit.init(dir: projectDir)
        git.commit(message: 'initial commit')
        git.tag.add(name: "v0.0.1")

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

        dependencies {
            testCompile('junit:junit:4.11')
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
    def "task :#lifecycleTask runs :#taskToRun"() {
        given: "some dummy test"
        writeTest('src/integrationTest/java/', "wooga.integration", false)
        writeTest('src/test/java/', "wooga.test", false)

        when:
        def result = runTasks(lifecycleTask, "--dry-run")

        then:
        result.standardOutput.contains(":$taskToRun ")

        where:
        taskToRun             | lifecycleTask
        "integrationTest"     | "check"
        "test"                | "check"
        "check"               | "releaseCheck"
        "assemble"            | "release"
        "publishPlugins"      | "final"
        "publishToMavenLocal" | "snapshot"
    }

    @Unroll
    def "verify :#taskA runs after :#taskB when execute '#execute'"() {
        given: "some dummy test"
        writeTest('src/integrationTest/java/', "wooga.integration", false)
        writeTest('src/test/java/', "wooga.test", false)

        when:
        def result = runTasksSuccessfully(*(execute << "--dry-run"))

        then:
        result.standardOutput.indexOf(":$taskA ") > result.standardOutput.indexOf(":$taskB ")

        where:
        taskA                 | taskB         | execute
        "integrationTest"     | "test"        | ["check"]
        "integrationTest"     | "test"        | ["integrationTest", "test"]
        "integrationTest"     | "test"        | ["check", "integrationTest", "test",]
        "publishPlugins"      | "postRelease" | ["publishPlugins", "postRelease"]
        "publishToMavenLocal" | "postRelease" | ["publishToMavenLocal", "postRelease"]
    }

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
    def "task :#taskToRun #message with System.getenv('#env')"() {
        given:
        environmentVariables.set(env, "TRUE")
        fork = true

        when:
        def result = runTasksSuccessfully(taskToRun)

        then:
        result.wasSkipped(taskToRun) == skipped

        where:
        taskToRun   | skipped | env
        "coveralls" | false   | 'CI'
        "coveralls" | true    | 'some value'

        message = skipped ? "should skip" : "shouldn't skip"
    }
}
