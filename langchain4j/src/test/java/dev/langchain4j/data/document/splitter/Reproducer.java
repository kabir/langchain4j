package dev.langchain4j.data.document.splitter;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

public class Reproducer {
    private static final String BULLET_LIST_WITH_NESTED_BULLET_LIST =
                    "* One\n" +
                    "  * 1-1\n" +
                    "  * 1-2\n" +
                    "* Two\n";
    private static final String BULLET_LIST_WITH_NESTED_ORDERED_LIST =
                    "* One\n" +
                    "   1. 1-1\n" +
                    "   2. 1-2\n" +
                    "* Two\n";
    private static final String ORDERED_LIST_WITH_NESTED_ORDERED_LIST =
                    "1. One\n" +
                    "   1. 1-1\n" +
                    "   2. 1-2\n" +
                    "2. Two\n";
    public static void main(String[] args) {
        parseMarkdown(BULLET_LIST_WITH_NESTED_BULLET_LIST);
        parseMarkdown(BULLET_LIST_WITH_NESTED_ORDERED_LIST);
        parseMarkdown(ORDERED_LIST_WITH_NESTED_ORDERED_LIST);
    }

    private static void parseMarkdown(String markdown) {
        System.out.println("========");
        System.out.println("Parsing:\n" + markdown);

        Parser parser = Parser.builder().build();
        Node document = parser.parse(markdown);

        System.out.println("----");
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
        public void visit(BulletList list) {
            if (output.isEmpty() || output.charAt(output.length() - 1) != '\n') {
                output.append("\n");
            }
            visitChildren(list);
            if (!(list.getParent() instanceof ListItem)) {
                output.append("\n");
            }
        }

        @Override
        public void visit(OrderedList list) {
            if (output.isEmpty() || output.charAt(output.length() - 1) != '\n') {
                output.append("\n");
            }
            visitChildren(list);
            if (!(list.getParent() instanceof ListItem)) {
                output.append("\n");
            }
        }

        @Override
        public void visit(ListItem listItem) {
            output.append("- ");

            ListBlock parent = findListBlockParent(listItem);
            String parentId = parent.getClass().getSimpleName() + "@" + System.identityHashCode(parent);

            output.append(" (");
            output.append("list=" + parentId);
            // These always return markerIndent=0; contentIndent=2
            // output.append("; markerIndent=" + listItem.getMarkerIndent());
            // output.append("; contentIndent=" + listItem.getContentIndent());
            output.append(") ");

            super.visit(listItem);
            if (listItem.getNext() != null) {
                output.append("\n");
            }
        }

         @Override
         public void visit(final Text text) {
             output.append(text.getLiteral());
         }

         private ListBlock findListBlockParent(Node node) {
             Node current = node;
             while (current != null) {
                 if (current instanceof ListBlock) {
                     return (ListBlock) current;
                 }
                 current = current.getParent();
             }
             return null;
         }
     }

    private static class Marker {

    }
}
