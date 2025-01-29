package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static dev.langchain4j.data.document.splitter.MarkdownSectionSplitter.SECTION_HEADER;
import static dev.langchain4j.data.document.splitter.MarkdownSectionSplitter.SECTION_INDEX_WITHIN_PARENT;
import static dev.langchain4j.data.document.splitter.MarkdownSectionSplitter.SECTION_LEVEL;
import static dev.langchain4j.data.document.splitter.MarkdownSectionSplitter.SECTION_PARENT_HEADER;

public class MarkdownSectionSplitterTest implements WithAssertions {

    // TODO Remove this
    // Lots of the examples come from https://github.com/chrisalley/markdown-garden/tree/master/source/guides/quotations

    @Test
    public void testNoSubSplitter() {
        String text = "# Title\n"
                + "## Section 1\n"
                + "section 1\n"
                + "## Section 2\n"
                + "section 2\n"
                + "### Section 2.1\n"
                + "section 2.1\n"
                + "#### Section 2.1.1\n"
                + "section 2.1.1\n"
                + "#### Section 2.1.2\n"
                + "section 2.1.2\nmore\n"
                + "### Section 2.2\n"
                + "#### Section 2.2.1\n"
                + "## Section 3\n"
                + "section 3\n"
                // Jump in section levels. There is no intermediate '###' on purpose
                + "#### Section 3.1.1\n"
                + "section 3.1.1\n"
                + "## Section 4\n"
                + "section 4 \n"
                // Add another level 1 section to make sure we can support more than one
                + "# Header 1\n"
                + "header\n";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .setEmptySectionPlaceholderText(".")
                .build();
        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        assertThat(segments.size()).isEqualTo(12);
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, ".");
        checkTextSegment(source, segments.get(1), "Section 1", "Title", 1, 0, "section 1");
        checkTextSegment(source, segments.get(2), "Section 2", "Title", 1, 1, "section 2");
        checkTextSegment(source, segments.get(3), "Section 2.1", "Section 2", 2, 0, "section 2.1");
        checkTextSegment(source, segments.get(4), "Section 2.1.1", "Section 2.1", 3, 0, "section 2.1.1");
        checkTextSegment(source, segments.get(5), "Section 2.1.2", "Section 2.1", 3, 1, "section 2.1.2\nmore");
        checkTextSegment(source, segments.get(6), "Section 2.2", "Section 2", 2, 1, ".");
        checkTextSegment(source, segments.get(7), "Section 2.2.1", "Section 2.2", 3, 0, ".");
        checkTextSegment(source, segments.get(8), "Section 3", "Title", 1, 2, "section 3");
        checkTextSegment(source, segments.get(9), "Section 3.1.1", "Section 3", 3, 0, "section 3.1.1");
        checkTextSegment(source, segments.get(10), "Section 4", "Title", 1, 3, "section 4");
        checkTextSegment(source, segments.get(11), "Header 1", null, 0, 1, "header");
    }

    @Test
    public void testIntroductoryTextNoHeaderNoDocumentTitle() {
        String text = "Intro text\n" + "## Section 1\n" + "section 1\n";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        assertThat(segments.size()).isEqualTo(2);
        checkTextSegment(source, segments.get(0), null, null, 0, 0, "Intro text");
        checkTextSegment(source, segments.get(1), "Section 1", null, 1, 0, "section 1");
    }

    @Test
    public void testIntroductoryTextNoHeaderWithDocumentTitle() {
        String text = "Intro text\n" + "## Section 1\n" + "section 1\n";

        DocumentSplitter splitter =
                MarkdownSectionSplitter.builder().setDocumentTitle("Doc Title").build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        assertThat(segments.size()).isEqualTo(2);
        checkTextSegment(source, segments.get(0), "Doc Title", null, 0, 0, "Intro text");
        checkTextSegment(source, segments.get(1), "Section 1", "Doc Title", 1, 0, "section 1");
    }

    @Test
    public void testSectionSplitter() {
        String text = "# Title\n"
                + "## Section 1\n"
                + "section 1\n"
                + "## Section 2\n"
                + "section 2 split\n"
                + "### Section 2.1\n"
                + "section 2.1\n"
                + "### Section 2.2\n"
                + "section 2.2 split\n";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .setEmptySectionPlaceholderText(".")
                .setDocumentTitle("Doc Title")
                .setSectionSplitter(DocumentSplitters.recursive(11, 0))
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        assertThat(segments.size()).isEqualTo(7);
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, ".");
        assertThat(segments.get(0).metadata().getInteger("index")).isEqualTo(0);

        checkTextSegment(source, segments.get(1), "Section 1", "Title", 1, 0, "section 1");
        assertThat(segments.get(1).metadata().getInteger("index")).isEqualTo(0);

        checkTextSegment(source, segments.get(2), "Section 2", "Title", 1, 1, "section 2");
        assertThat(segments.get(2).metadata().getInteger("index")).isEqualTo(0);

        checkTextSegment(source, segments.get(3), "Section 2", "Title", 1, 1, "split");
        assertThat(segments.get(3).metadata().getInteger("index")).isEqualTo(1);

        checkTextSegment(source, segments.get(4), "Section 2.1", "Section 2", 2, 0, "section 2.1");
        assertThat(segments.get(4).metadata().getInteger("index")).isEqualTo(0);

        checkTextSegment(source, segments.get(5), "Section 2.2", "Section 2", 2, 1, "section 2.2");
        assertThat(segments.get(5).metadata().getInteger("index")).isEqualTo(0);

        checkTextSegment(source, segments.get(6), "Section 2.2", "Section 2", 2, 1, "split");
        assertThat(segments.get(6).metadata().getInteger("index")).isEqualTo(1);
    }

    @Test
    public void testHeaderInFencedCodeBlock() {
        // The parser adds a blank line between the paragraph and the code block.
        // Test both cases
        String text = "# Title\n"
                + "## Section 1\n"
                + "section 1\n"
                + "```\n"
                + "# In Code\n"
                + "```\n"
                + "## Section 2\n"
                + "section 2\n"
                + "\n"
                + "```\n"
                + "# In Code\n"
                + "```\n";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .setEmptySectionPlaceholderText(".")
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        assertThat(segments.size()).isEqualTo(3);

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, ".");
        checkTextSegment(source, segments.get(1), "Section 1", "Title", 1, 0, "section 1\n\n```\n# In Code\n```");
        checkTextSegment(source, segments.get(2), "Section 2", "Title", 1, 1, "section 2\n\n```\n# In Code\n```");
    }

    @Test
    public void testCodeSpan() {
        String text = "# Title\n"
                + "## Section 1\n"
                + "section 1 is `the best` ever\n"
;

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .setEmptySectionPlaceholderText(".")
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        assertThat(segments.size()).isEqualTo(2);

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, ".");
        checkTextSegment(source, segments.get(1), "Section 1", "Title", 1, 0, "section 1 is `the best` ever");
    }

    @Test
    public void testOverrideConvertSectionToDocument() {
        String text = "# Title\n" + "intro\n" + "## Section 1\n" + "section 1\n";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .setConstructor(TestMarkdownSplitter::new)
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(2, segments.size());

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, "intro");
        assertThat(segments.get(0).metadata().getInteger("test-counter")).isEqualTo(0);

        checkTextSegment(source, segments.get(1), "Section 1", "Title", 1, 0, "section 1");
        assertThat(segments.get(1).metadata().getInteger("test-counter")).isEqualTo(1);
    }

    @Test
    public void testFencedCodeBlock() {
        // The parser adds an empty line between text and a code block. After the code block, there is no
        // space before any following text.
        // Test some variations of the input.
        String text = "# Title\n" +
                "Some text\n" +
                "```\n" +
                "    function(){\n" +
                "       this.i++;\n" +
                "\t}\n" +
                "```\n" +
                "More text\n\n" +
                "```\n" +
                "    print(x);\n" +
                "```\n\n" +
                "Final text";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                "Some text\n\n```\n    function(){\n       this.i++;\n\t}\n```\n" +
                        "More text\n\n```\n    print(x);\n```\nFinal text");

    }

    @Test
    public void testIndentedCodeBlock() {
        // The parser adds an empty line between text and a code block. After the code block, there is no
        // space before any following text.
        // Test some variations of the input.
        String text = "# Title\n" +
                "Some text\n\n" +
                "        function(){\n" +
                "           this.i++;\n" +
                "\t    }\n" +
                "More text\n\n" +
                "        print(x);\n" +
                "Final text";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        // IndentedCodeBlock.literal does not include the leading tabs/spaces
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                "Some text\n\n```\n    function(){\n       this.i++;\n    }\n```\n" +
                        "More text\n\n```\n    print(x);\n```\nFinal text");

    }

    @Test
    public void testParagraphs() {
        String text = "# Title\n" +
                "Paragraph 1\n\nParagraph2\n\n\nParagraph3";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        // Here
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                "Paragraph 1\n\nParagraph2\n\nParagraph3");
    }

    @Test
    public void testEmphasis() {
        String text = "# Title\n" +
                "The *quick* brown _fox_ jumped **over** the __lazy__ dog";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder().build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        // IndentedCodeBlock.literal does not include the leading tabs/spaces
        // I don't think the emphasis delimiters are important for an LLM?
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0,
                "The quick brown fox jumped over the lazy dog");
    }

    @Test
    public void testSetextHeaders() {
        // We are testing ATX Headers elsewhere (they are of the format "# Header 1", "## Header 2" etc.)
        // Setext uses equals under a line for H1, and hyphens for H2
        String text = "Title\n" +
                "=====\n" +
                // We need an extra newline at the end of the section for the next line to be recognised as a header
                // This is also the case when trying it out in a Markdown editor
                "intro\n\n" +
                "Section 1\n" +
                "----\n" +
                "section 1\n";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(2, segments.size());

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, "intro");
        checkTextSegment(source, segments.get(1), "Section 1", "Title", 1, 0, "section 1");
    }

    @Test
    public void testBulletList() {
        String text = "# Title\n" +
                "intro\n" +
                "* One\n" +
                "* Two `test` two\n";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, "intro\n* One\n* Two `test` two");
    }

    @Test
    public void testOrderedList() {
        String text = "# Title\n" +
                "intro\n" +
                "1. One\n" +
                "2. Two `test` two\n";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, "intro\n1. One\n2. Two `test` two");
    }

    @Test
    public void testNestedLists() {
        String body = "intro\n" +
//                "* One\n" +
                "* Two\n" +
//                "  * 2-1\n" +
                "  * 2-2\n" +
                "    1. 2-2-4\n" +
                "      * 2-2-4-1\n" +
//                "    2. 2-2-5\n" +
//                "* Three\n" +
//                "  1. 3-1\n" +
                "  2. 3.2\n";



        String text = "# Title\n" +
                body;


        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assertions.assertEquals(1, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, body.trim());
    }


    private Document createDocument(String text) {
        DocumentSource loader = new StringDocumentSource(text);
        Document doc = DocumentLoader.load(loader, new TextDocumentParser());
        doc.metadata().put("doc-a", "DOC-A");
        doc.metadata().put("doc-b", "DOC-B");

        return doc;
    }

    private void checkTextSegment(
            Document source,
            TextSegment ts,
            String header,
            String parentHeader,
            int level,
            int indexInParent,
            String text) {
        assertThat(ts.metadata().getString(SECTION_HEADER)).isEqualTo(header);
        assertThat(ts.metadata().getString(SECTION_PARENT_HEADER)).isEqualTo(parentHeader);
        assertThat(ts.metadata().getInteger(SECTION_LEVEL).intValue()).isEqualTo(level);
        assertThat(ts.metadata().getInteger(SECTION_INDEX_WITHIN_PARENT)).isEqualTo(indexInParent);
        assertThat(ts.text().trim()).isEqualTo(text);

        for (String key : source.metadata().toMap().keySet()) {
            assertThat(ts.metadata().getString(key)).isEqualTo(source.metadata().getString(key));
        }
    }

    private record StringDocumentSource(String text) implements DocumentSource {

        @Override
        public InputStream inputStream() throws IOException {
            return new ByteArrayInputStream(text.getBytes("UTF-8"));
        }

        @Override
        public Metadata metadata() {
            return new Metadata();
        }
    }

    private static class TestMarkdownSplitter extends MarkdownSectionSplitter {
        static int counter = 0;

        public TestMarkdownSplitter(Builder builder) {
            super(builder);
        }

        @Override
        protected Document adjustDocument(final Document document) {
            document.metadata().put("test-counter", counter++);
            return document;
        }
    }
}
