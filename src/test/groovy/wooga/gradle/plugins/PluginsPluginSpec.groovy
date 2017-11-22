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

import com.gradle.publish.PublishPlugin
import nebula.plugin.release.ReleasePlugin
import nebula.test.ProjectSpec
import org.ajoberstar.grgit.Grgit
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.kt3k.gradle.plugin.CoverallsPlugin
import spock.lang.Unroll

class PluginsPluginSpec extends ProjectSpec {
    public static final String PLUGIN_NAME = 'net.wooga.plugins'

    Grgit git

    def setup() {
        git = Grgit.init(dir: projectDir)
        git.commit(message: 'initial commit')
        git.tag.add(name: "v0.0.1")
    }

    @Unroll("applies plugin #pluginName")
    def 'Applies other plugins'(String pluginName, Class pluginType) {
        given:
        assert !project.plugins.hasPlugin(PLUGIN_NAME)
        assert !project.plugins.hasPlugin(pluginType)

        when:
        project.plugins.apply(PLUGIN_NAME)

        then:
        project.plugins.hasPlugin(pluginType)

        where:
        pluginName       | pluginType
        "groovy"         | GroovyPlugin
        "idea"           | IdeaPlugin
        "maven-publish"  | MavenPublishPlugin
        "plugin-publish" | PublishPlugin
        "nebula.release" | ReleasePlugin
        "jacoco"         | JacocoPlugin
        "coveralls"      | CoverallsPlugin
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
        taskName                                 | taskType
        PluginsPlugin.INTEGRATION_TEST_TASK_NAME | Test
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
        def testCompile = configurations.getByName("testCompile")
        def testRuntime = configurations.getByName("testRuntime")


        expect:
        def integrationTestCompile = configurations.getByName("integrationTestCompile")
        def integrationTestRuntime = configurations.getByName("integrationTestRuntime")

        integrationTestCompile.extendsFrom.contains(testCompile)
        integrationTestRuntime.extendsFrom.contains(testRuntime)
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
}