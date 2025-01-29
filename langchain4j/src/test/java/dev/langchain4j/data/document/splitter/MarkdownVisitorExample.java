package dev.langchain4j.data.document.splitter;

import org.commonmark.node.*;
import org.commonmark.parser.Parser;

public class MarkdownVisitorExample {

    public static void main(String[] args) {
        String markdown = "# Heading 1\n\n" +
                "This is a paragraph with **bold** text.\n\n" +
                "## Heading 2\n\n" +
                "This is another paragraph with *italic* text.\n\n" +
                "```java\n" +
                "System.out.println(\"Hello, World!\");\n" +
                "```\n\n" +
                "### Heading 3\n\n" +
                "This is a list:\n\n" +
                "- Item 1\n" +
                "- Item 2\n" +
                "- Item 3\n";

        Parser parser = Parser.builder().build();
        Node document = parser.parse(markdown);

        MarkdownOutputVisitor visitor = new MarkdownOutputVisitor();
        document.accept(visitor);

        System.out.println(visitor.getOutput());
    }

    static class MarkdownOutputVisitor extends AbstractVisitor {
        private StringBuilder output = new StringBuilder();

        public String getOutput() {
            return output.toString();
        }

        @Override
        public void visit(Heading heading) {
            output.append("#".repeat(heading.getLevel())).append(" ").append(heading.getFirstChild()).append("\n\n");
            visitChildren(heading);
        }

        @Override
        public void visit(Paragraph paragraph) {
            output.append(paragraph.getFirstChild()).append("\n\n");
            visitChildren(paragraph);
        }

        @Override
        public void visit(Text text) {
            output.append(text.getLiteral());
            visitChildren(text);
        }

        @Override
        public void visit(StrongEmphasis strongEmphasis) {
            output.append("**").append(strongEmphasis.getFirstChild()).append("**");
            visitChildren(strongEmphasis);
        }

        @Override
        public void visit(Emphasis emphasis) {
            output.append("*").append(emphasis.getFirstChild()).append("*");
            visitChildren(emphasis);
        }

        @Override
        public void visit(FencedCodeBlock fencedCodeBlock) {
            output.append("```").append(fencedCodeBlock.getInfo()).append("\n")
                  .append(fencedCodeBlock.getLiteral())
                  .append("```\n\n");
            visitChildren(fencedCodeBlock);
        }

        @Override
        public void visit(BulletList bulletList) {
            output.append("\n");
            visitChildren(bulletList);
            output.append("\n");
        }

        @Override
        public void visit(ListItem listItem) {
            output.append("- ").append(listItem.getFirstChild()).append("\n");
            visitChildren(listItem);
        }
    }
}
