package pl.szajsjem.elements;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class RNNNode extends Node {
    private static final int FEEDBACK_DOT_Y_OFFSET = 10; // Distance from bottom

    public List<Node> feedbackNodes = new ArrayList<>();
    public boolean feedbackHighlighted = false;

    public RNNNode(String label) {
        super(label);
        height = 80; // Make it slightly taller to accommodate the feedback dot
    }

    public boolean isOverFeedbackDot(Point p) {
        Rectangle feedbackDot = new Rectangle(
                x + width / 2 - overRadius,
                y + height - FEEDBACK_DOT_Y_OFFSET - overRadius,
                2 * overRadius,
                2 * overRadius
        );
        return feedbackDot.contains(p);
    }

    @Override
    public int isDotOverDot(Node n) {
        int result = super.isDotOverDot(n);
        if (result != 0) return result;

        // Check feedback dot
        if (isOverFeedbackDot(new Point(n.x, n.y))) return 2; // New case for feedback connection
        return 0;
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);

        // Draw t-1 feedback connection point
        g.setColor(feedbackHighlighted ? Color.GREEN : Color.BLACK);
        g.fillOval(
                x + width / 2 - 5,
                y + height - FEEDBACK_DOT_Y_OFFSET - 5,
                10,
                10
        );

        // Draw "t-1" label
        g.setColor(Color.BLACK);
        g.drawString("t-1", x + width / 2 - 10, y + height - FEEDBACK_DOT_Y_OFFSET + 20);
    }
}
