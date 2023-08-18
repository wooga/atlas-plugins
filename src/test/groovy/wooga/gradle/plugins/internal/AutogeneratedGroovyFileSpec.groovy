package wooga.gradle.plugins.internal

import spock.lang.Specification

class AutogeneratedGroovyFileSpec extends Specification {

    def "generates new groovy script file with empty autogenerated block"() {
        when:
        def autogeneratedFile = AutogeneratedGroovyFile.getOrCreate(File.createTempFile("autogen", ".groovy"))

        then:
        autogeneratedFile.baseFile.file
        def text = autogeneratedFile.baseFile.text
        def (beginIndex, endIndex) = autogeneratedCodeBounds(text)
        def autogenContents = getAutogenContent(text)

        beginIndex > -1
        endIndex > -1
        beginIndex < endIndex
        autogenContents == ""
    }

    def "loads existing groovy script file with autogenerated block"() {
        given:
        def existing = File.createTempFile("existing", ".groovy")
        def existingContent = "def existingVariable"
        def existingNonAutogenerated = "def notAutoGen"
        existing << """
        $AutogeneratedGroovyFile.GENERATED_CODE_UPPER_BOUND
        $existingContent
        $AutogeneratedGroovyFile.GENERATED_CODE_LOWER_BOUND
        $existingNonAutogenerated
        """.stripIndent()
        when:
        def autogeneratedFile = AutogeneratedGroovyFile.getOrCreate(existing)

        then:
        autogeneratedFile.baseFile.file
        def text = autogeneratedFile.baseFile.text
        def (beginIndex, endIndex) = autogeneratedCodeBounds(text)
        def autogenContents = getAutogenContent(text)

        beginIndex > -1
        endIndex > -1
        beginIndex < endIndex
        autogenContents == existingContent
        text.contains(existingNonAutogenerated)
        assert !contentIsInsideAutogeneratedBounds(text, existingNonAutogenerated)
    }

    def "writes autogenerated block to script file"() {
        given:
        def autogeneratedFile = AutogeneratedGroovyFile.getOrCreate(File.createTempFile("autogen", ".groovy"))
        def previousContent = "def previous"
        autogeneratedFile.overwriteAutogenerated(previousContent)
        assert contentIsInsideAutogeneratedBounds(autogeneratedFile.baseFile.text, previousContent)

        when:
        def content = "def content = \"\""
        autogeneratedFile.writeAutogenerated(content)

        then:
        def text = autogeneratedFile.baseFile.text
        def autogenContent = getAutogenContent(text)

        assert contentIsInsideAutogeneratedBounds(text, content)
        autogenContent == [previousContent, content].join("\n")
    }

    def "overwrites autogenerated block in script file"() {
        given:
        def autogeneratedFile = AutogeneratedGroovyFile.getOrCreate(File.createTempFile("autogen", ".groovy"))
        def previousContent = "def previous"
        autogeneratedFile.writeAutogenerated(previousContent)
        assert contentIsInsideAutogeneratedBounds(autogeneratedFile.baseFile.text, previousContent)

        when:
        def content = "def content = \"\""
        autogeneratedFile.overwriteAutogenerated(content)

        then:
        def text = autogeneratedFile.baseFile.text
        def autogenContent = getAutogenContent(text)

        assert contentIsInsideAutogeneratedBounds(text, content)
        autogenContent == content
    }



    def "doesn't overwrites code outside the autogenerated block"() {
        given:
        def autogeneratedFile = AutogeneratedGroovyFile.getOrCreate(File.createTempFile("autogen", ".groovy"))
        def nonAutogenerated = "\ndef custom = null"
        autogeneratedFile.baseFile << nonAutogenerated

        when:
        def autogeneratedCode = "def autogenerated \n def otherLine"
        autogeneratedFile.overwriteAutogenerated(autogeneratedCode)

        then:
        def text = autogeneratedFile.baseFile.text

        assert !contentIsInsideAutogeneratedBounds(text, nonAutogenerated)
        assert contentIsInsideAutogeneratedBounds(text, autogeneratedCode)
        text.contains(nonAutogenerated)
    }


    private Tuple2<Integer, Integer> autogeneratedCodeBounds(String fileContents) {
        def beginIndex = fileContents.indexOf(AutogeneratedGroovyFile.GENERATED_CODE_UPPER_BOUND) + AutogeneratedGroovyFile.GENERATED_CODE_UPPER_BOUND.length()
        def endIndex = fileContents.indexOf(AutogeneratedGroovyFile.GENERATED_CODE_LOWER_BOUND)
        return [beginIndex, endIndex]
    }

    private boolean contentIsInsideAutogeneratedBounds(String text, String contents) {
        def (beginIndex, endIndex) = autogeneratedCodeBounds(text)
        def contentIndex = text.indexOf(contents)
        def saneAutoGeneratedBounds =  beginIndex < endIndex
        def contentAfterBegin = contentIndex > beginIndex
        def contentBeforeEnd = contentIndex < endIndex
        return saneAutoGeneratedBounds && contentAfterBegin && contentBeforeEnd

    }

    private def getAutogenContent(String text) {
        def (beginIndex, endIndex) = autogeneratedCodeBounds(text)
        def rawContent = text.substring(beginIndex, endIndex)
        if(rawContent == "\n") {
            return ""
        }
        return rawContent.trim()
    }

}