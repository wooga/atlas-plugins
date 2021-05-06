package wooga.gradle.plugins.releasenotes

class ChangeNote {

	ChangeNote(String category, String text) {
		this.category = category
		this.text = text
	}

	String category
	String text

	@Override
	boolean equals(Object obj) {
		if(!obj || obj.class != this.class) {
			return false
		}

		this.category == (obj as ChangeNote).category && this.text == (obj as ChangeNote).text
	}

	@Override
	int hashCode() {
		"${category}-${text}".hashCode()
	}
}
