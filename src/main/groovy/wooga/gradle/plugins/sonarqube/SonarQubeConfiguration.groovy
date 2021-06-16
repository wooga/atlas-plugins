package wooga.gradle.plugins.sonarqube

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.sonarqube.gradle.SonarQubeExtension
import org.sonarqube.gradle.SonarQubePlugin
import org.sonarqube.gradle.SonarQubeProperties

class SonarQubeConfiguration {

    public static final String TASK_NAME = SonarQubeExtension.SONARQUBE_TASK_NAME
    public static final Class<Plugin> PLUGIN_CLASS = SonarQubePlugin.class

    private PropertyFactory propertyFactory

    static SonarQubeConfiguration withEnvVarPropertyFallback(Project project) {
        return new SonarQubeConfiguration(PropertyFactories.withEnvVarFallback(project))
    }

    public SonarQubeConfiguration(PropertyFactory propertyFactory) {
        this.propertyFactory = propertyFactory
    }

    private static String defaultProjectName(RepositoryInfo repoInfo) {
        return "${repoInfo.companyName}_${repoInfo.repositoryName}"
    }

    Action<? extends SonarQubeProperties> generateSonarProperties(RepositoryInfo repoInfo, JavaPluginConvention javaConvention) {
        return {sonarProps ->
            sonarProps.with {
                property "sonar.projectName",
                        propertyFactory.create("sonar.projectName", "SONAR_PROJECT_NAME",
                                repoInfo.repositoryName)
                property "sonar.projectKey",
                        propertyFactory.create("sonar.projectKey", "SONAR_PROJECT_KEY",
                                defaultProjectName(repoInfo))
                property "sonar.login",
                        propertyFactory.create("sonar.login", "SONAR_LOGIN", "")
                property "sonar.host.url",
                        propertyFactory.create("sonar.host.url", "SONAR_HOST", "")
                property "sonar.sources",
                        propertyFactory.create("sonar.sources", "SONAR_SOURCES",
                                srcDirMatching(javaConvention){ !it.name.toLowerCase().contains("test") }.join(","))
                property "sonar.tests",
                        propertyFactory.create("sonar.tests", "SONAR_TESTS",
                                srcDirMatching(javaConvention) { it.name.toLowerCase().contains("test") }.join(","))
                property "sonar.jacoco.reportPaths",
                        propertyFactory.create("sonar.jacoco.reportPaths", "SONAR_JACOCO_REPORT_PATHS",
                     "build/jacoco/integrationTest.exec,build/jacoco/test.exec")
            }
        }
    }

    private static List<String> srcDirMatching(JavaPluginConvention javaConvention, Closure closure) {
        return javaConvention.sourceSets.findAll(closure).
                collect { SourceSet sourceSet ->
                    sourceSet.allJava.sourceDirectories.collect {it.absolutePath}
                }.flatten() as List<String>
    }
}
