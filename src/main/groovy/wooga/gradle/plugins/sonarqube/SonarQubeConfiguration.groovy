package wooga.gradle.plugins.sonarqube

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
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

    Action<? extends SonarQubeProperties> generateSonarProperties(
            Provider<String> repositoryName,
            Provider<String> branchName,
            JavaPluginExtension javaExt) {

        def repoNameProvider = repositoryName.map { String fullRepoName ->
            fullRepoName.contains("/")? fullRepoName.split("/")[1] : fullRepoName
        }
        def keyProvider = repositoryName.map {repoName ->
            if(repoName.contains("/")) {
                def repoNameParts = repoName.split("/")
                return "${repoNameParts[0]}_${repoNameParts[1]}"
            }
            return repoName
        }
        branchName = branchName.map{it -> it.empty? null : it}
        //sonar.branch.name cant be present on PRs
        def nonPRBranchName = branchName.map{it -> it.empty? null : it}.map{isPR(it)? null : it}
        return { sonarProps ->
            sonarProps.with {
                property "sonar.login",
                        propertyFactory.create("sonar.login", "SONAR_LOGIN", null)
                property "sonar.host.url",
                        propertyFactory.create("sonar.host.url", "SONAR_HOST", null)
                property "sonar.projectName",
                        propertyFactory.create("sonar.projectName", "SONAR_PROJECT_NAME",
                                repoNameProvider.getOrNull())
                property "sonar.branch.name", propertyFactory.create("sonar.branch.name", "SONAR_BRANCH_NAME",
                                nonPRBranchName.getOrNull())
                property "sonar.projectKey",
                        propertyFactory.create("sonar.projectKey", "SONAR_PROJECT_KEY", keyProvider.getOrNull())
                //plugin default is sourceSets.main.allJava.srcDirs (with only existing dirs)
                property "sonar.sources",
                        propertyFactory.create("sonar.sources", "SONAR_SOURCES",
                                srcDirMatching(javaExt) { !it.name.toLowerCase().contains("test") }.join(","))
                property "sonar.tests",
                        propertyFactory.create("sonar.tests", "SONAR_TESTS",
                                srcDirMatching(javaExt) { it.name.toLowerCase().contains("test") }.join(","))
                property "sonar.jacoco.reportPaths",
                        propertyFactory.create("sonar.jacoco.reportPaths", "SONAR_JACOCO_REPORT_PATHS",
                                "build/jacoco/integrationTest.exec,build/jacoco/test.exec")
            }
        }
    }

    private static List<String> srcDirMatching(JavaPluginExtension javaExt, Closure closure) {
        return javaExt.sourceSets.findAll(closure).
                collect { SourceSet sourceSet ->
                    sourceSet.allJava.sourceDirectories.findAll { it.exists() }.collect { it.absolutePath }
                }.flatten() as List<String>
    }

    static boolean isPR(String currentBranch) {
        def maybePrNumber = currentBranch.replace("PR-", "").trim()
        return currentBranch.toUpperCase().startsWith("PR-") && maybePrNumber.isNumber()
    }
}
