package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * A {@link DocumentSplitter} that takes a Markdown as input.
 *
 * <p>The class is instantiated via the {@link Builder} returned from the {@link #builder()} method.</p>
 *
 * <p>Internally it splits the document into sections, with metadata entries to identify the location of each section
 * in the document. It then optionally splits each section with the {@link DocumentSplitter} passed in to the Builder.
 *
 */
public class MarkdownSectionSplitter implements DocumentSplitter {

    private static final Header NO_HEADER = new Header("==NO HEADER==", 1);

    public static final String SECTION_LEVEL = "md_section_level";
    public static final String SECTION_HEADER = "md_section_header";
    public static final String SECTION_INDEX_WITHIN_PARENT = "md_section_index_in_parent";
    public static final String SECTION_PARENT_HEADER = "md_parent_header";
    private static final DocumentSplitter NO_SPLIT = document -> Collections.singletonList(document.toTextSegment());

    private final DocumentSplitter sectionSplitter;

    private final String documentTitle;

    private final String emptySectionPlaceholderText;

    protected MarkdownSectionSplitter(Builder builder) {
        this.sectionSplitter = builder.sectionSplitter;
        this.documentTitle = builder.documentTitle;
        this.emptySectionPlaceholderText = builder.emptySectionPlaceholderText;
        if (sectionSplitter == null) {
            throw new IllegalArgumentException("Null sectionSplitter");
        }
    }

    /**
     * Creates a new Builder used to instantiate this class.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<TextSegment> split(Document document) {
        Parser parser = Parser.builder().build();
        Node root = parser.parse(document.text());

        SectionsByHeaderVisitor visitor = new SectionsByHeaderVisitor(document.metadata());
        root.accept(visitor);
        visitor.finish();
        return visitor.segments;
    }

    /**
     * Hook to add more data to the {@link Document} representing a split section before splitting
     * it into {@link TextSegment}s. This default implementation does nothing.
     *
     * @param document the document containing the section
     */
    protected Document adjustDocument(Document document) {
        return document;
    }

    public static class Builder {
        private DocumentSplitter sectionSplitter = NO_SPLIT;
        private String documentTitle;
        private String emptySectionPlaceholderText;

        private Function<Builder, MarkdownSectionSplitter> constructor;

        /**
         * <p>Sets the {@link DocumentSplitter} to further split each section.</p>
         *
         * <p>If not specified, the {@link MarkdownSectionSplitter} created by this builder will not
         * attempt to split the sections further.</p>
         *
         * @param sectionSplitter the {@link DocumentSplitter} used to further split the sections.
         * @return this Builder instance
         */
        public Builder setSectionSplitter(DocumentSplitter sectionSplitter) {
            this.sectionSplitter = sectionSplitter;
            return this;
        }

        /**
         * <p>Sets the title of the source document</p>
         *
         * <p>This is for the corner case where a Markdown document does not start with a header. If the document title
         * is set, it is used as the header for the first section.</p>
         *
         * @param title the document title
         * @return this Builder instance
         */
        public Builder setDocumentTitle(String title) {
            this.documentTitle = title;
            return this;
        }

        /**
         * <p>Set placeholder text to be used for empty sections, i.e. ones that just have a header.</p>
         *
         * <p>This is needed because the {@link Document} constructor throws an error if the constructor is empty.</p>
         * @param text placeholder text
         * @return this Builder instance
         */
        public Builder setEmptySectionPlaceholderText(String text) {
            this.emptySectionPlaceholderText = text;
            return this;
        }

        /**
         *
         * @param constructor
         * @return
         */
        public Builder setConstructor(Function<Builder, MarkdownSectionSplitter> constructor) {
            this.constructor = constructor;
            return this;
        }

        /**
         * Constructs the {@link MarkdownSectionSplitter} instance
         * @return the MarkdownSectionSplitter
         */
        public MarkdownSectionSplitter build() {
            if (constructor == null) {
                return new MarkdownSectionSplitter(this);
            }
            return constructor.apply(this);
        }
    }

    private class SectionsByHeaderVisitor extends AbstractVisitor {
        private final Metadata originalMetadata;
        private final List<TextSegment> segments = new ArrayList<>();
        private final List<Header> headers = new ArrayList<>();
        private StringBuilder currentSection = new StringBuilder();
        private Header currentHeader;
        private int nullHeaderIndex = 0;

        public SectionsByHeaderVisitor(final Metadata originalMetadata) {
            this.originalMetadata = new Metadata(originalMetadata.toMap());
        }

        @Override
        public void visit(final Heading heading) {
            endSection();

            currentHeader = new Header(heading);
        }

        @Override
        public void visit(final Paragraph paragraph) {
            if (currentHeader != null || headers.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                paragraph.accept(new AbstractVisitor() {
                    @Override
                    public void visit(final HardLineBreak hardLineBreak) {
                        sb.append("\n");
                    }

                    @Override
                    public void visit(final SoftLineBreak softLineBreak) {
                        sb.append("\n");
                    }

                    @Override
                    public void visit(final Text text) {
                        sb.append(text.getLiteral());
                    }

                    @Override
                    public void visit(final Code code) {
                        sb.append("`").append(code.getLiteral()).append("`");
                    }



//                    @Override
//                    public void visit(final BlockQuote blockQuote) {
//                        super.v
//                    }
                });
                if (currentHeader == null) {
                    currentHeader = new Header(documentTitle, 1);
                }
                currentSection.append(sb);
            }
            super.visit(paragraph);
        }

        @Override
        public void visit(final FencedCodeBlock codeBlock) {
            currentSection.append("\n```\n");
            currentSection.append(codeBlock.getLiteral());
            currentSection.append("```\n");
        }

        @Override
        public void visit(final IndentedCodeBlock codeBlock) {
            currentSection.append("\n```\n");
            currentSection.append(codeBlock.getLiteral());
            currentSection.append("```\n");
        }


        private void endSection() {
            if (currentHeader != null || !currentSection.isEmpty()) {
                Header header = currentHeader != null ? currentHeader : NO_HEADER;
                addHeaderToHierarchy(header);
                addSectionSegments(header, currentSection.toString());
            }

            if (!currentSection.isEmpty()) {
                currentSection = new StringBuilder();
            }
        }

        private void addSectionSegments(Header header, String sectionText) {
            // Set metadata about this particular section. Work on a copy
            Metadata metadata = new Metadata(originalMetadata.toMap());
            metadata.put(SECTION_LEVEL, header.level);
            if (header.text != null) {
                metadata.put(SECTION_HEADER, header.text);
            }
            metadata.put(SECTION_INDEX_WITHIN_PARENT, header.indexInParent);
            if (header.parent != null && header.parent.text != null) {
                metadata.put(SECTION_PARENT_HEADER, header.parent.text);
            }

            if (sectionText.isBlank() && emptySectionPlaceholderText != null) {
                // Document constructor does not like blank text
                sectionText = emptySectionPlaceholderText;
            }
            Document document = new Document(sectionText, metadata);
            document = adjustDocument(document);
            segments.addAll(sectionSplitter.split(document));
        }

        private void addHeaderToHierarchy(Header header) {
            if (!headers.isEmpty()) {
                for (ListIterator<Header> it = headers.listIterator(headers.size()) ; it.hasPrevious() ; ) {
                    Header curr = it.previous();
                    if (curr.level < header.level) {
                        curr.addChild(header);
                        break;
                    }
                }
            }

            headers.add(header);
            if (header.parent == null) {
                header.indexInParent = nullHeaderIndex++;
            }

        }

        private void finish() {
            endSection();
        }
    }

    private static class Header {
        private final String text;
        private final int level;
        private Header parent;
        private final List<Header> children = new ArrayList<>();
        private int indexInParent;

        Header(Heading heading) {
            this(
                    heading.getFirstChild() != null ? ((Text) heading.getFirstChild()).getLiteral() : null,
                    heading.getLevel());
        }

        private Header(final String text, final int level) {
            this.text = text;
            this.level = level - 1;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Header header = (Header) o;
            return level == header.level && Objects.equals(text, header.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(text, level);
        }

        private void addChild(Header child) {
            children.add(child);
            child.indexInParent = children.size() - 1;
            child.parent = this;
        }
    }
}
