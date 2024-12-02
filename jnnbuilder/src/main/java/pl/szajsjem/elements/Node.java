package pl.szajsjem.elements;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

// Base class for all nodes
public class Node {
    public int x;
    public int y;
    public int width = 100;
    public int height = 60;
    static int overRadius = 15;
    public List<Node> next = new ArrayList<>();
    String label;
    public List<Node> prev = new ArrayList<>();
    public boolean inputHighlighted = false;
    public boolean outputHighlighted = false;

    public Node(String label) {
        this.label = label;
    }

    public void paint(Graphics g, boolean isSelected) {
        // Background
        if (isSelected) {
            // Draw selection border
            g.setColor(new Color(180, 200, 255));
            g.fillRect(x - 2, y - 2, width + 4, height + 4);
        }

        // Node body
        g.setColor(Color.WHITE);
        g.fillRect(x, y, width, height);
        g.setColor(isSelected ? new Color(0, 100, 200) : Color.BLACK);
        g.drawRect(x, y, width, height);
        g.drawString(label, x + 10, y + height / 2);

        // Connection points
        g.setColor(inputHighlighted ? Color.GREEN : (isSelected ? new Color(0, 100, 200) : Color.BLACK));
        g.fillOval(x - 5, y + height / 2 - 5, 10, 10);  // Input

        g.setColor(outputHighlighted ? Color.GREEN : (isSelected ? new Color(0, 100, 200) : Color.BLACK));
        g.fillOval(x + width - 5, y + height / 2 - 5, 10, 10);  // Output
    }


    public boolean isOverInputDot(Point p) {
        Rectangle inputDot = new Rectangle(x - overRadius, y + height / 2 - overRadius, 2 * overRadius, 2 * overRadius);
        return inputDot.contains(p);
    }

    public boolean isOverOutputDot(Point p) {
        Rectangle outputDot = new Rectangle(x + width - overRadius, y + height / 2 - overRadius, 2 * overRadius, 2 * overRadius);
        return outputDot.contains(p);
    }

    // 1 if this.out -> n.in
    // -1 if this.in <- n.out
    // 0 othervise
    public int isDotOverDot(Node n) {
        if (isOverOutputDot(new Point(n.x, n.y)))
            return 1;
        if (isOverInputDot(new Point(n.x, n.y)))
            return -1;
        return 0;
    }

    public boolean contains(Point p) {
        Rectangle box = new Rectangle(x - 5, y - 5, width + 10, height + 10);
        return box.contains(p);
    }

    public String getLabel() {
        return label;
    }
}

