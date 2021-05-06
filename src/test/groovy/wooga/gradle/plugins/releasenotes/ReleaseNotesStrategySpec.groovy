package wooga.gradle.plugins.releasenotes

import com.wooga.github.changelog.changeSet.BaseChangeSet
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHPullRequest
import org.kohsuke.github.GHUser
import spock.lang.Shared
import spock.lang.Specification;
import spock.lang.Subject;


class ReleaseNotesStrategySpec extends Specification {

    @Subject
    ReleaseNotesStrategy strategy

    @Shared
    BaseChangeSet testChangeset

    @Shared
    List<String> commitMessages

    @Shared
    List<String> pullRequestTitles

    @Shared
    List<String> commitSha

    @Shared
    List<Integer> pullRequestNumbers

    private String repoBaseUrl = "https://github.com/test/"

    def setup() {
        strategy = new  ReleaseNotesStrategy()
        commitMessages = [
                "Setup custom test runner",
                "Improve overall Architecture",
                "Fix new awesome test",
                "Add awesome test",
                "Fix tests in integration spec"
        ]

        pullRequestTitles = [
                "Add custom integration tests",
                "Bugfix in test runner"
        ]
        pullRequestNumbers = [1, 2]
        commitSha = (0..10).collect { "123456${it.toString().padLeft(2, "0")}".padRight(40, "28791583").toString() }

        List<GHCommit> logs = generateMockLog(commitMessages.size())
        List<GHPullRequest> pullRequests = generateMockPR(pullRequestTitles.size())
        testChangeset = new BaseChangeSet("test", null, null, logs, pullRequests)
    }

    GHCommit mockCommit(String commitMessage, String sha1, boolean isGithubUser = true) {
        def user = null
        if (isGithubUser) {
            user = Mock(GHUser)
            user.getLogin() >> "TestUser"
            user.getEmail() >> "test@user.com"
        }

        def gitUser = Mock(GHCommit.GHAuthor)
        gitUser.email >> "test@user.com"
        gitUser.name >> "GitTestUser"

        return mockCommit(commitMessage, sha1, user, gitUser)
    }

    GHCommit mockCommit(String commitMessage, String sha1, GHUser user, GHCommit.GHAuthor gitUser) {
        def shortInfo = Mock(GHCommit.ShortInfo)
        shortInfo.getMessage() >> commitMessage
        shortInfo.committer >> gitUser
        shortInfo.author >> gitUser

        def commit = Mock(GHCommit)

        commit.getSHA1() >> sha1
        commit.getAuthor() >> user
        commit.getCommitter() >> user
        commit.getCommitShortInfo() >> shortInfo
        commit.getHtmlUrl() >> new URL(repoBaseUrl + "commit/" + sha1)
        return commit
    }

    GHPullRequest mockPr(String title, int number) {
        def user = Mock(GHUser)
        user.getLogin() >> "TestUser"
        user.getEmail() >> "test@user.com"

        return mockPr(title, number, user)
    }

    GHPullRequest mockPr(String title, int number, GHUser user) {
        def pr = Mock(GHPullRequest)
        pr.getNumber() >> number
        pr.getTitle() >> title
        pr.getHtmlUrl() >> new URL(repoBaseUrl + "issue/" + number)
        pr.getUser() >> user
        return pr
    }

    List<GHCommit> generateMockLog(int count) {
        def list = []
        for (int i = 0; i < count; i++) {
            list.add(mockCommit(commitMessages[i], commitSha[i]))
        }
        return list
    }

    List<GHPullRequest> generateMockPR(int count) {
        def list = []
        for (int i = 0; i < count; i++) {
            def pr = mockPr(pullRequestTitles[i], i)
            list.add(pr)
        }
        return list
    }

    def "maps from base change set type to strive change set"() {
        given: "some basic changes"
        List<GHCommit> logs = generateMockLog(commitMessages.size())

        and: "some custom pull requests with change lists"
        GHPullRequest pr1 = mockPr("Add custom test feature", 1)
        pr1.getBody() >> """
		## Changes
		* ![ADD] new test feature
		* ![IMPROVE] test suite
		""".stripIndent()

        GHPullRequest pr2 = mockPr("Fix test setup", 2)
        pr2.getBody() >> """
		## Changes
		* ![FIX] test suite startup
		* ![FIX] runner test setup code
		""".stripIndent()

        def pullRequests = [pr1,pr2]

        and: "a changeset"
        def changes = new BaseChangeSet("test",null,null,logs,pullRequests)

        when:
        def mappedResult = strategy.mapChangeSet(changes)

        then:
        noExceptionThrown()
        mappedResult.logs == logs
        mappedResult.pullRequests == pullRequests
        mappedResult.changes.keySet().collect {it.category}.containsAll(["ADD", "IMPROVE", "FIX", "FIX"])
        mappedResult.changes.keySet().collect {it.text}.containsAll(["new test feature", "test suite", "test suite startup", "runner test setup code"])

        mappedResult.changes[new ChangeNote("ADD", "new test feature")] == pr1
        mappedResult.changes[new ChangeNote("IMPROVE", "test suite")] == pr1
        mappedResult.changes[new ChangeNote("FIX", "test suite startup")] == pr2
        mappedResult.changes[new ChangeNote("FIX", "runner test setup code")] == pr2
    }

    def "renders provided change list"() {
        given: "some basic changes"
        List<GHCommit> logs = generateMockLog(commitMessages.size())

        and: "a custom pull requests with change lists"
        GHPullRequest pr1 = mockPr("Add custom test feature", 1)
        pr1.getBody() >> """
		## Changes
		* ![ADD] new test feature
		* ![IMPROVE] test suite
		""".stripIndent()

        def pullRequests = [pr1]

        and: "a changeset"
        def changes = new BaseChangeSet("test",null,null,logs,pullRequests)

        when:
        def result = strategy.render(changes)
        println(result)
        then:
        result.stripIndent().trim() == """
		## Changes
		
		* ![ADD] new test feature [[#1](https://github.com/test/issue/1)] [@TestUser](https://github.com/TestUser)
		* ![IMPROVE] test suite [[#1](https://github.com/test/issue/1)] [@TestUser](https://github.com/TestUser)
		
		[ADD]: https://resources.atlas.wooga.com/icons/icon_add.svg
		[IMPROVE]: https://resources.atlas.wooga.com/icons/icon_improve.svg
		""".stripIndent().trim()
    }

    def "renders logs and pull requests when no change list can be generated"() {
        given: "some basic changes"
        List<GHCommit> logs = generateMockLog(commitMessages.size())

        and: "a custom pull requests with change lists"
        GHPullRequest pr1 = mockPr("Add custom test feature", 1)
        pr1.getBody() >> """
		Just a description
		""".stripIndent()

        def pullRequests = [pr1]

        and: "a changeset"
        def changes = new BaseChangeSet("test",null,null,logs,pullRequests)

        when:
        def result = strategy.render(changes)
        println(result)
        then:
        result.trim() == """
		## Pull Requests
 
		* [[#1](https://github.com/test/issue/1)] Add custom test feature [@TestUser](https://github.com/TestUser)
		 
		## Commits
		 
		* [[12345600](https://github.com/test/commit/1234560028791583287915832879158328791583)] Setup custom test runner [@TestUser](https://github.com/TestUser)
		* [[12345601](https://github.com/test/commit/1234560128791583287915832879158328791583)] Improve overall Architecture [@TestUser](https://github.com/TestUser)
		* [[12345602](https://github.com/test/commit/1234560228791583287915832879158328791583)] Fix new awesome test [@TestUser](https://github.com/TestUser)
		* [[12345603](https://github.com/test/commit/1234560328791583287915832879158328791583)] Add awesome test [@TestUser](https://github.com/TestUser)
		* [[12345604](https://github.com/test/commit/1234560428791583287915832879158328791583)] Fix tests in integration spec [@TestUser](https://github.com/TestUser)
		""".stripIndent().trim()
    }
}
