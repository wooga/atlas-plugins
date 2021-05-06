package wooga.gradle.plugins.releasenotes

import com.wooga.github.changelog.changeSet.ChangeSet
import com.wooga.github.changelog.changeSet.Compound
import com.wooga.github.changelog.changeSet.Logs
import com.wooga.github.changelog.changeSet.PullRequests
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHPullRequest

class ChangeSet<B extends com.wooga.github.changelog.changeSet.ChangeSet<GHCommit, GHPullRequest>> implements com.wooga.github.changelog.changeSet.ChangeSet, Logs<GHCommit>, PullRequests<GHPullRequest>, Compound<B> {

	Map<ChangeNote, GHPullRequest> changes

	ChangeSet(B base, Map<ChangeNote, GHPullRequest> changes) {
		this.inner = base
		this.changes = changes
	}

	@Delegate
	final B inner

	@Override
	void mutate(Closure mutator) {
		this.with mutator
	}

	@Override
	<M> M map(Closure<M> map) {
		map.delegate = this
		map.resolveStrategy = Closure.DELEGATE_ONLY
		map.call(this)
	}
}
