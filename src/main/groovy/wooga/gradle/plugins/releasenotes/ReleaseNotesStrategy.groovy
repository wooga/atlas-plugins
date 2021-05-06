package wooga.gradle.plugins.releasenotes

import com.wooga.github.changelog.AbstractGeneratorStrategy
import com.wooga.github.changelog.changeSet.BaseChangeSet
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHPullRequest

class ReleaseNotesStrategy extends AbstractGeneratorStrategy<ChangeSet, BaseChangeSet<GHCommit, GHPullRequest>> {


	ReleaseNotesStrategy() {
		super(new ChangeSetRenderer())
	}

	@Override
	ChangeSet mapChangeSet(BaseChangeSet<GHCommit, GHPullRequest> changes) {
		def changesMap = changes.pullRequests.inject(new HashMap<ChangeNote, GHPullRequest>()) { m, pr ->
			def body = pr.body
			def c = body.readLines().findAll { it.trim().startsWith("* ![") }
			def changeNotes = c.collect {
				def match = (it =~ /!\[(.*?)] (.+)/)
				if (match) {
					String category = match[0][1].toString()
					String text = match[0][2].toString()
					return new ChangeNote(category, text)
				}
				return null
			} - null
			changeNotes.each { changeNote -> m[changeNote] = pr }
			return m
		}

		return new ChangeSet(changes, changesMap)
	}
}
