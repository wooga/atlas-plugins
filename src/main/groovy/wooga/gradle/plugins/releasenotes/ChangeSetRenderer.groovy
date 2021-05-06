package wooga.gradle.plugins.releasenotes

import com.wooga.github.changelog.render.ChangeRenderer
import com.wooga.github.changelog.render.MarkdownRenderer
import com.wooga.github.changelog.render.markdown.Headline
import com.wooga.github.changelog.render.markdown.HeadlineType
import com.wooga.github.changelog.render.markdown.Link
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHPullRequest

class ChangeSetRenderer implements ChangeRenderer<ChangeSet>, MarkdownRenderer {

	boolean generateInlineLinks = true
	HeadlineType headlineType = HeadlineType.atx

	static Link getUserLink(GHPullRequest pr) {
		new Link("@" + pr.user.login, "https://github.com/" + pr.user.login)
	}

	static Link getUserLink(GHCommit commit) {
		if (commit.author) {
			new Link("@" + commit.author.login, "https://github.com/" + commit.author.login)
		} else {
			new Link(commit.commitShortInfo.author.name, "mailto://" + commit.commitShortInfo.author.email)
		}
	}

	@Override
	String render(ChangeSet changeSet) {
		Set<Link> links = new HashSet<Link>()

		new StringBuilder().with {
			if(!changeSet.changes.isEmpty()) {
				append(new Headline("Changes", 2, headlineType))
				changeSet.changes.keySet().sort {a,b -> a.category <=> b.category}.each { changeNote ->
					def pr = changeSet.changes[changeNote]
					def prLink = new Link("#" + pr.number, pr.getHtmlUrl())
					def userLink = getUserLink(pr)
					def iconLink = new Link(changeNote.category, "https://resources.atlas.wooga.com/icons/icon_${changeNote.category.toLowerCase()}.svg", changeNote.category)

					if (!generateInlineLinks) {
						links.add(prLink)
						links.add(userLink)
					}
					links.add(iconLink)

					append("* !${iconLink.referenceLink()} ${changeNote.text} [${prLink.link(generateInlineLinks)}] ${userLink.link(generateInlineLinks)}\n")
				}
			} else {
				if (!changeSet.pullRequests.empty) {
					append(new Headline("Pull Requests", 2, headlineType))
					changeSet.pullRequests.each { pr ->
						def prLink = new Link("#" + pr.number, pr.getHtmlUrl())
						def userLink = getUserLink(pr)

						if (!generateInlineLinks) {
							links.add(prLink)
							links.add(userLink)
						}

						append("* [${prLink.link(generateInlineLinks)}] ${pr.title} ${userLink.link(generateInlineLinks)}\n")


					}
					append("\n")
				}

				if (!changeSet.logs.empty) {
					append(new Headline("Commits", 2, headlineType))
					changeSet.logs.each { commit ->
						def shaShort = commit.getSHA1().substring(0, 8)
						def commitLink = new Link(shaShort, commit.htmlUrl)
						def authorLink = getUserLink(commit)

						if (!generateInlineLinks) {
							links.add(commitLink)
							links.add(authorLink)
						}

						append("* [${commitLink.link(generateInlineLinks)}] ${commit.commitShortInfo.message.readLines().first()} ${authorLink.link(generateInlineLinks)}\n")
					}
					append("\n")
				}

			}
			append("\n")
			append(links.sort({ a, b -> a.text <=> b.text }).collect({ it.reference() }).join("\n"))
			return it
		}.toString()
	}
}
