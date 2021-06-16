package wooga.gradle.plugins.sonarqube


import wooga.gradle.github.base.GithubPluginExtension

class RepositoryInfo {
    private String companyName
    private String repositoryName

    public static final RepositoryInfo empty = new RepositoryInfo("", "")

    static Optional<RepositoryInfo> fromGithubExtension(GithubPluginExtension githubExt) {
        String fullRepoName = githubExt.repositoryName.getOrNull()
        return Optional.ofNullable(fullRepoName).map {
            String[] nameParts = fullRepoName.split("/")
            def repoName = nameParts[1]
            def companyName = nameParts[0]
            return new RepositoryInfo(companyName, repoName)
        }
    }

    private RepositoryInfo(String companyName, String repositoryName) {
        this.companyName = companyName
        this.repositoryName = repositoryName
    }

    String getCompanyName() {
        return companyName
    }

    String getRepositoryName() {
        return repositoryName
    }
}
