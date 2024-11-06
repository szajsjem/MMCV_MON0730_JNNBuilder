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
    static int overRadius = 5;
    public List<Node> next = new ArrayList<>();
    String label;
    public List<Node> prev = new ArrayList<>();
    public boolean inputHighlighted = false;
    public boolean outputHighlighted = false;

    public Node(String label) {
        this.label = label;
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


    public void paint(Graphics g) {
        g.setColor(Color.WHITE);
        g.fillRect(x, y, width, height);
        g.setColor(Color.BLACK);
        g.drawRect(x, y, width, height);
        g.drawString(label, x + 10, y + height/2);

        // Draw connection points with highlighting
        g.setColor(inputHighlighted ? Color.GREEN : Color.BLACK);
        g.fillOval(x - 5, y + height/2 - 5, 10, 10);  // Input

        g.setColor(outputHighlighted ? Color.GREEN : Color.BLACK);
        g.fillOval(x + width - 5, y + height/2 - 5, 10, 10);  // Output
    }
}

