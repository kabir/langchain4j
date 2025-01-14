package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link DocumentSplitter} that takes a Markdown as input.
 *
 * <p>The class is instantiated via the {@link Builder} returned from the {@link #builder()} method.</p>
 *
 * <p>It first splits out the sections according to the headers, and organises these hierarchically.
 * Then it optionally splits each section with the {@link DocumentSplitter} passed in to the Builder.
 *
 */
public class MarkdownSectionSplitter implements DocumentSplitter {

    public static final String SECTION_LEVEL = "md-section-level";
    public static final String SECTION_HEADER = "md-section-header";
    public static final String SECTION_INDEX_WITHIN_PARENT = "md-section-index-in-parent";
    public static final String SECTION_PARENT_HEADER = "md-parent-header";
    private static final Pattern HEADER_PATTERN = Pattern.compile("^#+ .*");
    private static final String CODE_BLOCK_MARKER = "```";

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
        List<Section> sections = readSections(document);
        sections = organiseSectionsByHeader(sections);

        return splitSections(document, sections);
    }

    private List<TextSegment> splitSections(Document document, List<Section> sections) {
        List<TextSegment> segments = new ArrayList<>();
        for (Section section : sections) {
            Document sectionDoc = convertSectionToDocument(document, section);
            List<TextSegment> sectionSegments = sectionSplitter.split(sectionDoc);

            segments.addAll(sectionSegments);

            if (!section.children.isEmpty()) {
                segments.addAll(splitSections(document, section.children));
            }
        }
        return segments;
    }

    private List<Section> organiseSectionsByHeader(List<Section> sections) {
        List<Section> topSections = new ArrayList<>();
        for (Section section : sections) {
            addSectionHierarchically(topSections, null, section);
        }
        return topSections;
    }

    private static void addSectionHierarchically(List<Section> sections, Section parent, Section section) {
        Section last = sections.isEmpty() ? null : sections.get(sections.size() - 1);
        if (last == null || last.level >= section.level) {
            sections.add(section);
            section.indexInParent = sections.size() - 1;
            if (parent != null) {
                section.parent = parent;
            }
        } else {
            last.addChild(section);
        }
    }

    private List<Section> readSections(Document document) {
        List<Section> sections = new ArrayList<>();

        BufferedReader reader = new BufferedReader(new StringReader(document.text()));
        boolean inCodeBlock = false;
        Section current = null;
        try {
            String line = reader.readLine();
            while (line != null) {
                if (line.startsWith(CODE_BLOCK_MARKER)) {
                    inCodeBlock = !inCodeBlock;
                }
                String header = null;
                if (!inCodeBlock) {
                    Matcher matcher = HEADER_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        header = line;
                        // A new header means a new section
                        current = new Section(header);
                        sections.add(current);
                    }
                }
                if (header == null) {
                    // Special handling for when the document does not start with a header,
                    // and thus there is no current section
                    if (current == null) {
                        current = new Section(0);
                        current.header = documentTitle;
                        sections.add(current);
                    }
                    current.sb.append(line);
                    current.sb.append("\n");
                }
                line = reader.readLine();
            }
        } catch (IOException ignore) {
            // No IO is being done above
        }
        return sections;
    }

    /**
     * Creates a {@link Document} from the {@link Section}. This Document is used as the input to further splitting
     * the section.
     * <p>
     * The {@link Metadata} is copied from the {@code source} Document, and then augmented with where in the
     * Markdown hierarchy the section was found.
     * <p>
     * <p></p>Override this method to further augment the {@link Metadata}. In order to override, use
     * {@link Builder#setConstructor(Function)} with the constructor of a subclass of {@link MarkdownSectionSplitter}.
     *
     * @param source the {@link Document} that was split into a hierarchy of {@link Section}s
     * @param section the section to convert to a Document
     * @return the section converted to a Document
     */
    protected Document convertSectionToDocument(Document source, Section section) {
        return section.convertToDocument(source);
    }

    /**
     * Represents a Markdown section in the discovered hierarchy.
     */
    protected class Section {
        private final StringBuilder sb = new StringBuilder();
        private final int level;
        public int indexInParent;
        private String header;

        private Section parent;
        private List<Section> children = new ArrayList<>();

        private Section(int level) {
            this.level = level;
        }

        private Section(String header) {
            this(headerLevel(header));
            this.header = header.substring(this.level + 1).trim();
        }

        private void addChild(Section section) {
            addSectionHierarchically(children, this, section);
        }

        private static int headerLevel(String header) {
            int i = 0;
            for (; i < header.length(); i++) {
                if (header.charAt(i) != '#') {
                    return i - 1;
                }
            }
            return i;
        }

        /**
         * Gets the text in this section
         * @return the section text
         */
        public String getSectionText() {
            return sb.toString();
        }

        /**
         * Gets the level of this section within the document hierarchy
         *
         * @return the level of this section
         */
        public int getLevel() {
            return level;
        }

        /**
         * Gets the index of this section within the parent
         * @return the index of this section within the parent
         */
        public int getIndexInParent() {
            return indexInParent;
        }

        /**
         * Gets the header of this section
         * @return the header
         */
        public String getHeader() {
            return header;
        }

        /**
         * Gets the parent of this section
         * @return the parent, or {@code null} if this is a top-level section.
         */
        public Section getParent() {
            return parent;
        }

        /**
         * Gets the children of this section
         * @return the children
         */
        public List<Section> getChildren() {
            return children;
        }

        private Document convertToDocument(Document source) {
            // Grab the metadata from the document
            Metadata metadata = new Metadata(source.metadata().toMap());

            // Set metadata about this particular section
            metadata.put(SECTION_LEVEL, level);
            if (header != null) {
                metadata.put(SECTION_HEADER, header);
            }
            metadata.put(SECTION_INDEX_WITHIN_PARENT, indexInParent);
            if (parent != null && parent.header != null) {
                metadata.put(SECTION_PARENT_HEADER, parent.header);
            }

            String text = sb.toString();
            if (text.isBlank() && emptySectionPlaceholderText != null) {
                // Document constructor does not like blank text
                text = emptySectionPlaceholderText;
            }
            return new Document(text, metadata);
        }

        @Override
        public String toString() {
            return "Section{" + "level=" + level + ", header='" + header + '\'' + ",\nsb=" + sb + '}';
        }
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
}
