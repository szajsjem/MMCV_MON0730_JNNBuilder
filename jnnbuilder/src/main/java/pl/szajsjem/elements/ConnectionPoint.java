package pl.szajsjem.elements;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class ConnectionPoint {
    static int overRadius = 15;
    public boolean highlighted = false;
    public List<ConnectionPoint> connected = new ArrayList<>();
    String name;
    boolean isInput;
    int x, y;
    public Node parent;

    public ConnectionPoint(Node parent, String name, boolean isInput, int x, int y) {
        this.name = name;
        this.parent = parent;
        this.isInput = isInput;
        this.x = x;
        this.y = y;
    }

    public Point getAbsolutePos() {
        return new Point(parent.x + x, parent.y + y);
    }

    public boolean isInput() {
        return isInput;
    }

    public void paint(Graphics2D g2d, boolean isSelected) {
        g2d.setColor(highlighted ? Color.GREEN :
                (isSelected ? new Color(0, 100, 200) : Color.BLACK));
        g2d.fillOval(parent.x + x - 5, parent.y + y - 5, 10, 10);

        // Draw name
        g2d.setColor(Color.BLACK);
        FontMetrics fm = g2d.getFontMetrics();
        int textY = isInput ? parent.y + y - 10 : parent.y + y + 20;
        int textX = parent.x + x - fm.stringWidth(name) / 2;
        g2d.drawString(name, textX, textY);

        if (!isInput) {  // Draw only once
            Point start = getAbsolutePos();
            for (var targetPoint : connected) {
                Point end = targetPoint.getAbsolutePos();
                g2d.setColor(Color.BLACK);
                g2d.setStroke(new BasicStroke(2.0f));
                int controlDist = 50;
                Point2D.Float ctrl1 = new Point2D.Float(start.x + controlDist, start.y);
                Point2D.Float ctrl2 = new Point2D.Float(end.x - controlDist, end.y);

                Path2D.Float path = new Path2D.Float();
                path.moveTo(start.x, start.y);
                path.curveTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, end.x, end.y);
                g2d.draw(path);
            }
        }
    }

    public boolean isOver(Point p) {
        Rectangle dot = new Rectangle(
                x + parent.x - overRadius,
                y + parent.y - overRadius,
                2 * overRadius,
                2 * overRadius
        );
        return dot.contains(p);
    }
}
