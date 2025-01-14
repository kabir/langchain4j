package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

public class MarkdownSectionSplitterTest {

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

        Assert.assertEquals(12, segments.size());
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

        Assert.assertEquals(2, segments.size());
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

        Assert.assertEquals(2, segments.size());
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

        Assert.assertEquals(7, segments.size());
        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, ".");
        Assert.assertEquals(0, segments.get(0).metadata().getInteger("index").intValue());

        checkTextSegment(source, segments.get(1), "Section 1", "Title", 1, 0, "section 1");
        Assert.assertEquals(0, segments.get(1).metadata().getInteger("index").intValue());

        checkTextSegment(source, segments.get(2), "Section 2", "Title", 1, 1, "section 2");
        Assert.assertEquals(0, segments.get(2).metadata().getInteger("index").intValue());

        checkTextSegment(source, segments.get(3), "Section 2", "Title", 1, 1, "split");
        Assert.assertEquals(1, segments.get(3).metadata().getInteger("index").intValue());

        checkTextSegment(source, segments.get(4), "Section 2.1", "Section 2", 2, 0, "section 2.1");
        Assert.assertEquals(0, segments.get(4).metadata().getInteger("index").intValue());

        checkTextSegment(source, segments.get(5), "Section 2.2", "Section 2", 2, 1, "section 2.2");
        Assert.assertEquals(0, segments.get(5).metadata().getInteger("index").intValue());

        checkTextSegment(source, segments.get(6), "Section 2.2", "Section 2", 2, 1, "split");
        Assert.assertEquals(1, segments.get(6).metadata().getInteger("index").intValue());
    }

    @Test
    public void testHeaderInCodeBlock() {
        String text = "# Title\n"
                + "## Section 1\n"
                + "section 1\n"
                + "```\n"
                + "# In Code\n"
                + "```\n"
                + "## Section 2\n"
                + "section 2\n"
                + "```\n"
                + "# In Code\n"
                + "```\n";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .setEmptySectionPlaceholderText(".")
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assert.assertEquals(3, segments.size());

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, ".");
        checkTextSegment(source, segments.get(1), "Section 1", "Title", 1, 0, "section 1\n```\n# In Code\n```");
        checkTextSegment(source, segments.get(2), "Section 2", "Title", 1, 1, "section 2\n```\n# In Code\n```");
    }

    @Test
    public void testOverrideConvertSectionToDocument() {
        String text = "# Title\n" + "intro\n" + "## Section 1\n" + "section 1\n";

        DocumentSplitter splitter = MarkdownSectionSplitter.builder()
                .setConstructor(TestMarkdownSplitter::new)
                .build();

        Document source = createDocument(text);
        List<TextSegment> segments = splitter.split(source);

        Assert.assertEquals(2, segments.size());

        checkTextSegment(source, segments.get(0), "Title", null, 0, 0, "intro");
        Assert.assertEquals(
                0, segments.get(0).metadata().getInteger("test-counter").intValue());

        checkTextSegment(source, segments.get(1), "Section 1", "Title", 1, 0, "section 1");
        Assert.assertEquals(
                1, segments.get(1).metadata().getInteger("test-counter").intValue());
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
        Assert.assertEquals(header, ts.metadata().getString(MarkdownSectionSplitter.SECTION_HEADER));
        Assert.assertEquals(parentHeader, ts.metadata().getString(MarkdownSectionSplitter.SECTION_PARENT_HEADER));
        Assert.assertEquals(
                level,
                ts.metadata().getInteger(MarkdownSectionSplitter.SECTION_LEVEL).intValue());
        Assert.assertEquals(
                indexInParent,
                ts.metadata()
                        .getInteger(MarkdownSectionSplitter.SECTION_INDEX_WITHIN_PARENT)
                        .intValue());
        Assert.assertEquals(text, ts.text().trim());

        for (String key : source.metadata().toMap().keySet()) {
            Assert.assertEquals(source.metadata().getString(key), ts.metadata().getString(key));
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
        protected Document convertSectionToDocument(final Document source, final Section section) {
            Document doc = super.convertSectionToDocument(source, section);
            doc.metadata().put("test-counter", counter++);
            return doc;
        }
    }
}
