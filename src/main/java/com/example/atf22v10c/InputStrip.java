package com.example.atf22v10c;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;

/**
 * Top header strip that draws the 22 input buffers feeding the AND array:
 *
 *   - 12 dedicated input pins (left side), labelled with their (DIP, SMT)
 *     package pin numbers.
 *   - 10 macrocell feedback inputs (right side), labelled Q1..Q10.
 *
 * For each input the buffer fans out into two column lines: a TRUE line that
 * goes straight down into one column of the array, and a COMPLEMENT line that
 * passes through an inverter bubble before entering the adjacent column.
 *
 * Column layout in the array: column 2k carries the true value of input k,
 * column 2k+1 carries its complement.
 */
public class InputStrip extends JPanel {

    static final int HEIGHT = 96;

    private static final Color BUFFER_LINE = new Color(40, 40, 40);
    private static final Color FB_LINE     = new Color(0,  110, 60);
    private static final Color FB_BG       = new Color(236, 248, 236);
    private static final Color INPUT_BG    = new Color(250, 250, 250);
    private static final Color SECTION_LINE = new Color(160, 160, 160);

    /** 12 dedicated input pin labels: (DIP pin, PLCC/SMT pin). Pin 12 = GND. */
    private static final String[] DEDICATED_PIN_LABELS = {
            "(1,2)", "(2,3)", "(3,4)", "(4,5)", "(5,6)", "(6,7)",
            "(7,9)", "(8,10)", "(9,11)", "(10,12)", "(11,13)", "(13,16)"
    };

    public InputStrip() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(FuseGridPanel.TOTAL_WIDTH, HEIGHT));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int cell = FuseGridPanel.CELL;
            int colOff = FuseGridPanel.COL_OFFSET;
            int dedicatedEndX = colOff + 24 * cell;
            int arrayEndX = FuseGridPanel.OUTPUT_AREA_X;
            int totalW = getWidth();

            g.setColor(INPUT_BG);
            g.fillRect(0, 0, dedicatedEndX, HEIGHT);
            g.setColor(FB_BG);
            g.fillRect(dedicatedEndX, 0, arrayEndX - dedicatedEndX, HEIGHT);
            g.setColor(Color.WHITE);
            g.fillRect(arrayEndX, 0, totalW - arrayEndX, HEIGHT);

            g.setColor(SECTION_LINE);
            g.drawLine(dedicatedEndX, 0, dedicatedEndX, HEIGHT);

            g.setFont(getFont().deriveFont(Font.BOLD, 10f));
            g.setColor(new Color(80, 80, 80));
            g.drawString("DEDICATED INPUTS  (DIP, SMT)", 6, 12);
            g.setColor(FB_LINE);
            g.drawString("MACROCELL FEEDBACK  (Q1..Q10)", dedicatedEndX + 6, 12);

            for (int k = 0; k < FuseMap.NUM_INPUTS; k++) {
                drawInput(g, k, cell, colOff);
            }
        } finally {
            g.dispose();
        }
    }

    private void drawInput(Graphics2D g, int k, int cell, int colOff) {
        int col0X = colOff + (2 * k) * cell;
        int col1X = colOff + (2 * k + 1) * cell;
        int centerX = (col0X + col1X) / 2;

        boolean isFeedback = k >= 12;

        if (isFeedback) {
            drawFeedbackHeader(g, k, col0X, col1X, centerX);
        } else {
            drawDedicatedInputBuffer(g, k, col0X, col1X, centerX);
        }
    }

    /** Buffered DIP/SMT input pin: triangle with true/complement column fan-out. */
    private void drawDedicatedInputBuffer(Graphics2D g, int k,
                                          int col0X, int col1X, int centerX) {
        String[] parts = DEDICATED_PIN_LABELS[k].replaceAll("[()]", "").split(",");
        String line1 = parts[0];
        String line2 = parts[1];

        g.setColor(BUFFER_LINE);
        g.setFont(getFont().deriveFont(Font.PLAIN, 8f));
        int w1 = g.getFontMetrics().stringWidth(line1);
        int w2 = g.getFontMetrics().stringWidth(line2);
        g.drawString(line1, centerX - w1 / 2, 24);
        g.drawString(line2, centerX - w2 / 2, 34);

        if (k == 0) {
            g.setColor(new Color(30, 100, 200));
            g.setFont(getFont().deriveFont(Font.BOLD, 8f));
            g.drawString("CLK", centerX + 8, 24);
        }
        g.setColor(BUFFER_LINE);
        g.setFont(getFont().deriveFont(Font.PLAIN, 8f));

        int pinY     = 38;
        int bufTopY  = 42;
        int bufBotY  = 56;
        int splitY   = 62;
        int compInvY = 67;
        int compBub  = 5;

        g.setStroke(new BasicStroke(1f));
        g.drawLine(centerX, pinY, centerX, bufTopY);

        Polygon buf = new Polygon(
                new int[]{centerX - 6, centerX + 6, centerX},
                new int[]{bufTopY, bufTopY, bufBotY}, 3);
        g.draw(buf);

        g.drawLine(centerX, bufBotY, centerX, splitY);
        g.drawLine(col0X, splitY, col1X, splitY);

        g.drawLine(col0X, splitY, col0X, HEIGHT);

        g.drawLine(col1X, splitY, col1X, compInvY);
        g.draw(new Ellipse2D.Double(col1X - compBub / 2.0, compInvY,
                compBub, compBub));
        g.drawLine(col1X, compInvY + compBub, col1X, HEIGHT);

        g.setFont(getFont().deriveFont(Font.PLAIN, 7f));
        g.setColor(new Color(140, 140, 140));
        g.drawString("T", col0X - 4, splitY - 2);
        g.drawString("C", col1X - 4, splitY - 2);
    }

    /**
     * Macrocell feedback header: label only, with a downward arrow into the
     * matching column pair. The actual driving signal is the green feedback
     * wire drawn inside the grid panel, which terminates exactly under this
     * header at columns 2k and 2k+1.
     */
    private void drawFeedbackHeader(Graphics2D g, int k,
                                    int col0X, int col1X, int centerX) {
        String label = "Q" + (k - 11);

        g.setColor(FB_LINE);
        g.setFont(getFont().deriveFont(Font.BOLD, 9f));
        int lw = g.getFontMetrics().stringWidth(label);
        g.drawString(label, centerX - lw / 2, 26);

        g.setFont(getFont().deriveFont(Font.PLAIN, 7f));
        g.setColor(new Color(0, 130, 70));
        g.drawString("fb", centerX - g.getFontMetrics().stringWidth("fb") / 2, 36);

        g.setColor(FB_LINE);
        g.setStroke(new BasicStroke(1f));
        for (int x : new int[]{col0X, col1X}) {
            g.drawLine(x, 44, x, HEIGHT);
        }
        g.fill(new Polygon(
                new int[]{col0X - 3, col0X + 3, col0X},
                new int[]{HEIGHT - 7, HEIGHT - 7, HEIGHT - 1}, 3));
        g.fill(new Polygon(
                new int[]{col1X - 3, col1X + 3, col1X},
                new int[]{HEIGHT - 7, HEIGHT - 7, HEIGHT - 1}, 3));

        g.setFont(getFont().deriveFont(Font.PLAIN, 7f));
        g.setColor(new Color(140, 140, 140));
        g.drawString("T", col0X - 4, 42);
        g.drawString("C", col1X - 4, 42);
    }
}
