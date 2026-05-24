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
 * Top header strip that draws the 22 input pin buffers feeding the AND array.
 *
 * Every input is a dedicated input pin — there is NO separate "macrocell
 * feedback" category on the ATF22V10C. The first 10 pins (1..10) also happen
 * to be macrocell I/O pins, meaning the macrocell's tri-state output drives
 * the same physical pin that the AND-array input buffer reads. That feedback
 * relationship is drawn by {@link FuseGridPanel} as a green wire from the
 * macrocell output looping back to the top of the matching input buffer's
 * pin stem — there is no extra column or extra buffer for it.
 *
 * Wire-stitching across the panel boundary: this strip lives directly above
 * the FuseGridPanel. The feedback wire in FuseGridPanel terminates at
 * (centerXOfInput(macro), 0) at the top of that panel, i.e. flush against
 * the bottom of this strip. The strip therefore extends the pin stem for
 * the first 10 inputs all the way from the buffer triangle DOWN through the
 * strip to the bottom edge, so the green wire meets the pin stem exactly
 * at the panel boundary.
 *
 * For each input the buffer fans out into two column lines: a TRUE line that
 * goes straight down into one column of the array, and a COMPLEMENT line
 * that passes through an inverter bubble before entering the adjacent column.
 * Column 2k carries the true value of input k; column 2k+1 carries its
 * complement.
 */
public class InputStrip extends JPanel {

    static final int HEIGHT = 96;

    private static final Color BUFFER_LINE  = new Color(40, 40, 40);
    private static final Color FB_LINE      = new Color(0,  110, 60);
    private static final Color INPUT_BG     = new Color(250, 250, 250);
    private static final Color CLK_COLOR    = new Color(30, 100, 200);

    /** First 10 input pins are also macrocell I/O pins (pin N = MC N). */
    private static final int IO_PIN_COUNT = 10;

    public InputStrip() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(FuseGridPanel.TOTAL_WIDTH, HEIGHT));
    }

    /** X of the centre of input pin {@code k} (0-based). Matches FuseGridPanel column geometry. */
    static int centerXOfInput(int k) {
        int col0X = FuseGridPanel.COL_OFFSET + (2 * k) * FuseGridPanel.CELL;
        int col1X = FuseGridPanel.COL_OFFSET + (2 * k + 1) * FuseGridPanel.CELL;
        return (col0X + col1X) / 2;
    }

    /** X of the vertical pin stem for input pin {@code k} (0-based). The
     *  feedback wire from FuseGridPanel must terminate at this X at y=0 so
     *  that the green wire visibly joins the input buffer's pin stem at the
     *  InputStrip/FuseGridPanel boundary. */
    static int stemXOfInput(int k) {
        return centerXOfInput(k) - 9;
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
            int arrayEndX = FuseGridPanel.OUTPUT_AREA_X;
            int totalW = getWidth();

            g.setColor(INPUT_BG);
            g.fillRect(0, 0, arrayEndX, HEIGHT);
            g.setColor(Color.WHITE);
            g.fillRect(arrayEndX, 0, totalW - arrayEndX, HEIGHT);

            g.setFont(getFont().deriveFont(Font.BOLD, 10f));
            g.setColor(new Color(80, 80, 80));
            g.drawString("INPUT PINS  (1 = CLK; pins 1..10 are also macrocell I/O)", 6, 12);

            for (int k = 0; k < FuseMap.NUM_INPUTS; k++) {
                drawInputBuffer(g, k, cell, colOff);
            }
        } finally {
            g.dispose();
        }
    }

    private void drawInputBuffer(Graphics2D g, int k, int cell, int colOff) {
        int col0X = colOff + (2 * k) * cell;
        int col1X = colOff + (2 * k + 1) * cell;
        int centerX = (col0X + col1X) / 2;

        boolean isIoPin = k < IO_PIN_COUNT;
        String pinLabel = Integer.toString(k + 1);

        g.setColor(isIoPin ? FB_LINE : BUFFER_LINE);
        g.setFont(getFont().deriveFont(Font.BOLD, 9f));
        int pw = g.getFontMetrics().stringWidth(pinLabel);
        g.drawString(pinLabel, centerX - pw / 2, 24);

        if (k == 0) {
            g.setColor(CLK_COLOR);
            g.setFont(getFont().deriveFont(Font.BOLD, 8f));
            g.drawString("CLK", centerX + 8, 24);
        }

        // Buffer geometry. The "pin" of the buffer is on the LEFT side
        // (apex pointing right), with the input wire coming straight in
        // from the left along the pin stem; the buffer output then exits
        // from the apex on the right and drops into the fan-out.
        //
        // Pin stem layout: for IO pins (k < 10) the stem runs the FULL
        // height of the strip alongside the buffer so it meets the green
        // feedback wire arriving from the FuseGridPanel below. For pure
        // dedicated input pins the stem just covers the top half (the
        // external chip pin is implied above the strip).
        int bufLeftX  = centerX - 5;
        int bufRightX = centerX + 5;
        int bufTopY   = 30;
        int bufBotY   = 42;
        int bufApexY  = (bufTopY + bufBotY) / 2;

        int stemX     = bufLeftX - 4;
        int stemTopY  = 28;
        int stemBotY  = isIoPin ? HEIGHT : bufApexY;

        g.setColor(isIoPin ? FB_LINE : BUFFER_LINE);
        g.setStroke(new BasicStroke(isIoPin ? 1.2f : 1f));
        g.drawLine(stemX, stemTopY, stemX, stemBotY);
        g.drawLine(stemX, bufApexY, bufLeftX, bufApexY);

        g.setColor(BUFFER_LINE);
        g.setStroke(new BasicStroke(1f));
        Polygon buf = new Polygon(
                new int[]{bufLeftX, bufLeftX, bufRightX},
                new int[]{bufTopY, bufBotY, bufApexY}, 3);
        g.draw(buf);

        int splitY   = 52;
        int compInvY = 57;
        int compBub  = 5;

        g.drawLine(bufRightX, bufApexY, centerX, bufApexY);
        g.drawLine(centerX, bufApexY, centerX, splitY);
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
}
