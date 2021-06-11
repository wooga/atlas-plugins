package wooga.gradle.plugins.sonarqube

import org.gradle.api.Project

interface PropertyFactory {
    public String create(String projectPropertyKey, String envVarKey, String defaultValue)
}

class PropertyFactories {
    static PropertyFactory withEnvVarFallback(Project project) {
        return { projectPropertyKey, envVarKey, defaultValue ->
            if(project.hasProperty(projectPropertyKey)) {
                return project.property(projectPropertyKey)
            } else if(System.getenv().containsKey(envVarKey)) {
                return System.getenv(envVarKey)
            } else {
                return defaultValue;
            }
        }
    }
}