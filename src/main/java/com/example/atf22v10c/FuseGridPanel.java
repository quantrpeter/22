package com.example.atf22v10c;

import com.example.atf22v10c.FuseMap.RowInfo;
import com.example.atf22v10c.FuseMap.RowKind;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputAdapter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Single all-in-one panel that draws:
 *   - The clickable AND-array fuse grid (132 PT rows x 44 input columns).
 *   - The macrocell output area on the right: OR gate, OE inverter,
 *     OUTPUT LOGIC block, tri-state output buffer, feedback tap and ASYNCH
 *     trunk.
 *
 * Coordinates:
 *   x(col)  = COL_OFFSET + col * CELL
 *   y(row)  = ROW_OFFSET + row * CELL
 *
 * The output area lives entirely to the right of the array (x >= OUTPUT_AREA_X)
 * and is purely decorative — clicks there are ignored.
 */
public class FuseGridPanel extends JPanel {

    static final int CELL = 14;
    static final int COL_OFFSET = CELL / 2;
    static final int DOT_DIAMETER = 7;
    /** Vertical space reserved above the array for ASYNCH + feedback + CLK routing. */
    static final int TOP_CHANNEL = 92;
    static final int ROW_OFFSET = TOP_CHANNEL;

    /* Output area geometry. */
    static final int OUTPUT_AREA_WIDTH = 220;
    static final int OUTPUT_AREA_X     = COL_OFFSET + FuseMap.NUM_COLUMNS * CELL;
    static final int TOTAL_WIDTH       = OUTPUT_AREA_X + OUTPUT_AREA_WIDTH;

    /* Per-macrocell output layout (absolute X coordinates, computed from OUTPUT_AREA_X). */
    private static final int GATE_LEFT   = OUTPUT_AREA_X + 6;
    private static final int GATE_W      = 38;
    private static final int GATE_RIGHT  = GATE_LEFT + GATE_W;
    private static final int LOGIC_X     = GATE_RIGHT + 18;
    private static final int LOGIC_W     = 58;
    private static final int LOGIC_RIGHT = LOGIC_X + LOGIC_W;
    private static final int TRI_X       = LOGIC_RIGHT + 14;
    private static final int TRI_SIZE    = 18;
    private static final int TRI_RIGHT   = TRI_X + TRI_SIZE;

    private static final int ASYNCH_Y_OFFSET = 4;
    /** Y of the global CLK bus inside the top channel, just above the AR row. */
    private static final int CLK_BUS_Y = 76;
    /** X of the vertical CLK trunk that serves every macrocell (left of OUTPUT LOGIC). */
    private static final int CLK_TRUNK_X = LOGIC_X - 12;

    private static final Color PT_LINE_COLOR     = new Color(60, 60, 60);
    private static final Color INPUT_LINE_COLOR  = new Color(60, 60, 60);
    private static final Color INTACT_COLOR      = new Color(20, 20, 20);
    private static final Color OE_BG             = new Color(255, 244, 215);
    private static final Color AR_SP_BG          = new Color(232, 244, 255);
    private static final Color BLOCK_SEPARATOR   = new Color(160, 160, 160);
    private static final Color GATE_LINE         = new Color(40, 40, 40);
    private static final Color FEEDBACK_COLOR    = new Color(0, 110, 60);
    private static final Color CLK_COLOR         = new Color(30, 100, 200);

    private final FuseMap map;
    private final WireGraph wireGraph;
    private int hoverRow = -1;
    private int hoverCol = -1;
    private Consumer<String> statusListener = s -> {};

    public FuseGridPanel(FuseMap map) {
        this.map = map;
        setBackground(Color.WHITE);
        setOpaque(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        setPreferredSize(new Dimension(
                TOTAL_WIDTH,
                ROW_OFFSET + map.rowCount() * CELL + CELL));

        this.wireGraph = new WireGraph(enumerateWireSegments());

        MouseInputAdapter mouse = new MouseInputAdapter() {
            @Override public void mouseMoved(MouseEvent e)   { updateHover(e); }
            @Override public void mouseDragged(MouseEvent e) { updateHover(e); }
            @Override public void mouseExited(MouseEvent e) {
                hoverRow = hoverCol = -1;
                repaint();
                statusListener.accept(" ");
            }
            @Override public void mousePressed(MouseEvent e) {
                boolean isLeft  = SwingUtilities.isLeftMouseButton(e);
                boolean isRight = SwingUtilities.isRightMouseButton(e);
                if (!isLeft && !isRight) return;

                int x = e.getX(), y = e.getY();

                // 1) clicking an existing connect point removes it (either button)
                if (wireGraph.removeJunctionNear(x, y)) {
                    statusListener.accept("removed connect point");
                    repaint();
                    return;
                }

                // 2) try to highlight: drop a connect point on any wire under the cursor
                boolean highlighted = wireGraph.addJunction(x, y);

                // 3) left-click on a fuse cell also toggles that fuse
                if (isLeft) {
                    int r = rowAt(y), c = colAt(x);
                    if (r >= 0 && c >= 0) {
                        boolean nowIntact = map.toggle(r, c);
                        String msg = describe(r, c) + " -> "
                                + (nowIntact ? "CONNECTED" : "disconnected");
                        if (highlighted) msg += "   [+ highlight]";
                        statusListener.accept(msg);
                        repaint();
                        return;
                    }
                }

                if (highlighted) {
                    statusListener.accept("connect point added ("
                            + wireGraph.junctions().size() + " total)");
                } else {
                    statusListener.accept("no wire under cursor");
                }
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public WireGraph getWireGraph() {
        return wireGraph;
    }

    public void setStatusListener(Consumer<String> l) {
        this.statusListener = (l == null ? s -> {} : l);
    }

    public FuseMap getMap() {
        return map;
    }

    private void updateHover(MouseEvent e) {
        int r = rowAt(e.getY());
        int c = colAt(e.getX());
        if (r != hoverRow || c != hoverCol) {
            int oldR = hoverRow, oldC = hoverCol;
            hoverRow = r;
            hoverCol = c;
            if (oldR >= 0 && oldC >= 0) repaint(cellBounds(oldR, oldC));
            if (r   >= 0 && c   >= 0)   repaint(cellBounds(r, c));
        }
        if (r >= 0 && c >= 0) {
            statusListener.accept(describe(r, c)
                    + "  (" + (map.isIntact(r, c) ? "connected" : "disconnected") + ")");
        } else {
            statusListener.accept(" ");
        }
    }

    private String describe(int r, int c) {
        RowInfo ri = map.row(r);
        String rowDesc = switch (ri.kind) {
            case AR -> "AR";
            case SP -> "SP";
            case OE -> "OE[" + (ri.macrocell + 1) + "]";
            case PT -> "MC" + (ri.macrocell + 1) + ".PT" + ri.ptIndex;
        };
        return rowDesc + " x I" + FuseMap.columnLabel(c);
    }

    int rowAt(int py) {
        // Map the click to the NEAREST row line, treating each row's hit area
        // as the half-cell either side of its line (so a click on the fuse
        // visually lands on the toggled fuse, not the cell above-left).
        if (py < ROW_OFFSET - CELL / 2) return -1;
        int r = (py - ROW_OFFSET + CELL / 2) / CELL;
        if (r >= map.rowCount()) return -1;
        return r;
    }

    int colAt(int px) {
        if (px < COL_OFFSET - CELL / 2) return -1;
        int c = (px - COL_OFFSET + CELL / 2) / CELL;
        if (c >= FuseMap.NUM_COLUMNS) return -1;
        return c;
    }

    java.awt.Rectangle cellBounds(int r, int c) {
        // Padding must comfortably contain the hover ring (radius ~6.5 + stroke).
        int pad = 10;
        return new java.awt.Rectangle(
                COL_OFFSET + c * CELL - pad,
                ROW_OFFSET + r * CELL - pad,
                pad * 2, pad * 2);
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

            int rStart = 0;
            int rEnd   = map.rowCount() - 1;

            paintRowBackgrounds(g, rStart, rEnd);
            paintInputLines(g, rStart, rEnd);
            paintProductTermLines(g, rStart, rEnd);
            paintFuses(g, rStart, rEnd);
            paintOutputArea(g);
            paintAsynchTrunk(g);
            paintClockBus(g);
            paintBlockSeparators(g);
            paintNetHighlights(g);
        } finally {
            g.dispose();
        }
    }

    private void paintRowBackgrounds(Graphics2D g, int rStart, int rEnd) {
        for (int r = rStart; r <= rEnd; r++) {
            RowInfo ri = map.row(r);
            Color bg = null;
            if (ri.kind == RowKind.AR || ri.kind == RowKind.SP) bg = AR_SP_BG;
            else if (ri.kind == RowKind.OE) bg = OE_BG;
            if (bg != null) {
                g.setColor(bg);
                g.fillRect(0, ROW_OFFSET + r * CELL - CELL / 2, TOTAL_WIDTH, CELL);
            }
        }
    }

    private void paintInputLines(Graphics2D g, int rStart, int rEnd) {
        g.setColor(INPUT_LINE_COLOR);
        g.setStroke(new BasicStroke(1f));
        // Extend columns up to y=0 so they continue visually into the
        // InputStrip (column header) above, and downward past the last row.
        int yTop = 0;
        int yBot = ROW_OFFSET + (rEnd + 1) * CELL - CELL / 2;
        for (int c = 0; c < FuseMap.NUM_COLUMNS; c++) {
            int x = COL_OFFSET + c * CELL;
            g.drawLine(x, yTop, x, yBot);
        }
    }

    private void paintProductTermLines(Graphics2D g, int rStart, int rEnd) {
        g.setColor(PT_LINE_COLOR);
        g.setStroke(new BasicStroke(1f));
        int xL = COL_OFFSET;
        for (int r = rStart; r <= rEnd; r++) {
            RowInfo ri = map.row(r);
            int y = ROW_OFFSET + r * CELL;
            int xR = switch (ri.kind) {
                case PT -> GATE_LEFT + (int) (GATE_W * 0.18);
                case OE -> OUTPUT_AREA_X + 14 + 12;
                default -> OUTPUT_AREA_X;
            };
            g.drawLine(xL, y, xR, y);
        }
    }

    private void paintFuses(Graphics2D g, int rStart, int rEnd) {
        Ellipse2D.Double dot = new Ellipse2D.Double();
        g.setColor(INTACT_COLOR);
        for (int r = rStart; r <= rEnd; r++) {
            int y = ROW_OFFSET + r * CELL;
            for (int c = 0; c < FuseMap.NUM_COLUMNS; c++) {
                if (!map.isIntact(r, c)) continue;
                int x = COL_OFFSET + c * CELL;
                dot.setFrame(x - DOT_DIAMETER / 2.0, y - DOT_DIAMETER / 2.0,
                        DOT_DIAMETER, DOT_DIAMETER);
                g.fill(dot);
            }
        }
    }

    private void paintBlockSeparators(Graphics2D g) {
        g.setColor(BLOCK_SEPARATOR);
        g.setStroke(new BasicStroke(1.2f));
        RowKind prevKind = null;
        for (int r = 0; r < map.rowCount(); r++) {
            RowInfo ri = map.row(r);
            boolean newBlock =
                    (prevKind == RowKind.AR) ||
                    (prevKind == RowKind.OE) ||
                    (ri.kind == RowKind.SP);
            if (newBlock && r > 0) {
                int y = ROW_OFFSET + r * CELL - CELL / 2;
                g.drawLine(0, y, TOTAL_WIDTH, y);
            }
            prevKind = ri.kind;
        }
    }

    /* -------------------------- output-area painting -------------------------- */

    private void paintOutputArea(Graphics2D g) {
        int firstPtRow = -1;
        int currentMacro = -1;
        for (int r = 0; r < map.rowCount(); r++) {
            RowInfo ri = map.row(r);
            if (ri.kind == RowKind.PT && ri.ptIndex == 0) {
                firstPtRow = r;
                currentMacro = ri.macrocell;
            } else if (ri.kind == RowKind.OE && firstPtRow >= 0) {
                drawMacrocell(g, currentMacro, firstPtRow, r - 1, r);
                firstPtRow = -1;
                currentMacro = -1;
            }
        }
    }

    /**
     * Draw the global ASYNCH trunk. The asynchronous-reset signal is generated
     * by the AR product-term row (row 0) and broadcast across the top of the
     * chip. The wire is therefore rooted at the right-hand end of the AR row,
     * climbs into the top channel and runs out to the right edge of the panel.
     * No per-macrocell vertical drops are drawn — ASYNCH is treated as an
     * implicit global signal that reaches every macrocell.
     */
    private void paintAsynchTrunk(Graphics2D g) {
        g.setColor(GATE_LINE);
        g.setStroke(new BasicStroke(1f));

        int y = ASYNCH_Y_OFFSET;
        int arY = ROW_OFFSET;

        g.drawLine(OUTPUT_AREA_X, arY, OUTPUT_AREA_X, y);
        g.fillOval(OUTPUT_AREA_X - 2, arY - 2, 4, 4);

        g.drawLine(OUTPUT_AREA_X, y, TOTAL_WIDTH - 2, y);

        g.setFont(getFont().deriveFont(java.awt.Font.BOLD, 11f));
        g.drawString("ASYNCH", TRI_X - 44, y + 10);
    }

    /**
     * Global clock bus: pin 1 (DIP1 / SMT2) drives the CLK input of every
     * output macrocell. The bus is drawn as:
     *   - a vertical drop at pin 1's center X from the top of the panel
     *     down to the horizontal CLK bus,
     *   - the horizontal bus across the array to the CLK trunk,
     *   - a vertical CLK trunk running through every macrocell row, with a
     *     short branch and ">" port drawn per macrocell by drawClockPort.
     */
    private void paintClockBus(Graphics2D g) {
        int pin1CenterX = COL_OFFSET + CELL / 2;

        g.setColor(CLK_COLOR);
        g.setStroke(new BasicStroke(1.2f));

        g.drawLine(pin1CenterX, 0, pin1CenterX, CLK_BUS_Y);
        g.fillOval(pin1CenterX - 2, CLK_BUS_Y - 2, 4, 4);

        g.drawLine(pin1CenterX, CLK_BUS_Y, CLK_TRUNK_X, CLK_BUS_Y);

        int lastY = ROW_OFFSET + (map.rowCount() - 2) * CELL;
        g.drawLine(CLK_TRUNK_X, CLK_BUS_Y, CLK_TRUNK_X, lastY);

        g.setFont(getFont().deriveFont(java.awt.Font.BOLD, 10f));
        g.drawString("CLK (pin 1)", pin1CenterX + 6, CLK_BUS_Y - 3);
    }

    /**
     * Draws the full output network for one macrocell: OR gate, OE inverter,
     * OUTPUT LOGIC block, tri-state buffer, feedback tap and routing.
     */
    private void drawMacrocell(Graphics2D g, int macro,
                               int firstPtRow, int lastPtRow, int oeRow) {
        int ptCount = lastPtRow - firstPtRow + 1;

        int yFirst = ROW_OFFSET + firstPtRow * CELL;
        int yLast  = ROW_OFFSET + lastPtRow  * CELL;
        int yOE    = ROW_OFFSET + oeRow      * CELL;
        int midY   = (yFirst + yLast) / 2;

        int gateTop = yFirst - CELL / 2 + 1;
        int gateBot = yLast  + CELL / 2 - 1;
        int gateH   = gateBot - gateTop;

        g.setColor(GATE_LINE);
        g.setStroke(new BasicStroke(1.4f));
        GeneralPath orGate = orGatePath(GATE_LEFT, gateTop, GATE_W, gateH);
        g.draw(orGate);

        g.setFont(getFont().deriveFont(java.awt.Font.BOLD, 11f));
        String pts = Integer.toString(ptCount);
        int sw = g.getFontMetrics().stringWidth(pts);
        g.drawString(pts, GATE_LEFT + GATE_W * 5 / 12 - sw / 2 + 2, midY + 4);

        int invX = OUTPUT_AREA_X + 14;
        int invW = 12;
        int invH = 10;
        int invY = yOE;
        int bub  = 4;

        g.setStroke(new BasicStroke(1f));
        Polygon inv = new Polygon(
                new int[]{invX, invX + invW, invX},
                new int[]{invY - invH / 2, invY, invY + invH / 2}, 3);
        g.draw(inv);
        g.draw(new Ellipse2D.Double(invX + invW, invY - bub / 2.0, bub, bub));

        g.setStroke(new BasicStroke(1.2f));
        int logicH = Math.max(36, gateH * 7 / 10);
        int logicY = midY - logicH / 2;
        g.drawRect(LOGIC_X, logicY, LOGIC_W, logicH);

        g.setFont(getFont().deriveFont(java.awt.Font.BOLD, 9f));
        drawCenteredMultiline(g, "OUT-\nPUT\nLOGIC",
                LOGIC_X + LOGIC_W / 2, midY);

        g.setStroke(new BasicStroke(1f));
        g.drawLine(GATE_RIGHT, midY, LOGIC_X, midY);

        Polygon tri = new Polygon(
                new int[]{TRI_X, TRI_X + TRI_SIZE, TRI_X},
                new int[]{midY - TRI_SIZE / 2, midY, midY + TRI_SIZE / 2}, 3);
        g.draw(tri);

        g.drawLine(LOGIC_RIGHT, midY, TRI_X, midY);
        g.drawLine(TRI_RIGHT, midY, TOTAL_WIDTH - 2, midY);

        int invOutX = invX + invW + bub;
        int routeX  = LOGIC_X - 6;
        int routeY  = logicY + logicH + 6;
        int triEnX  = TRI_X + TRI_SIZE / 2;

        g.drawLine(invOutX, invY, routeX, invY);
        g.drawLine(routeX, invY, routeX, routeY);
        g.drawLine(routeX, routeY, triEnX, routeY);
        g.drawLine(triEnX, routeY, triEnX, midY + TRI_SIZE / 2);

        drawClockPort(g, logicY, logicH);

        drawFeedbackWire(g, macro, midY);
    }

    /**
     * Draws the CLK input on a single OUTPUT LOGIC block: a horizontal stub
     * from the global CLK trunk into the block, plus a small ">" edge-trigger
     * marker just inside the block.
     */
    private void drawClockPort(Graphics2D g, int logicY, int logicH) {
        int clkY = logicY + logicH - 8;

        g.setColor(CLK_COLOR);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(CLK_TRUNK_X, clkY, LOGIC_X, clkY);

        g.setColor(GATE_LINE);
        g.drawLine(LOGIC_X, clkY - 4, LOGIC_X + 6, clkY);
        g.drawLine(LOGIC_X + 6, clkY, LOGIC_X, clkY + 4);
    }

    /**
     * Route a feedback wire from the macrocell's I/O pin back into the AND
     * array as the two Q{n} feedback columns (true + complement).
     *
     * Routing:
     *   1. Tap the OUT trace just right of the tri-state buffer.
     *   2. Vertical trunk on the far right of the panel (staggered X per macrocell).
     *   3. Horizontal channel across the reserved TOP_CHANNEL area
     *      (staggered Y per macrocell).
     *   4. A small inverter-bubble split that drops INTO column 2k (true)
     *      and column 2k+1 (complement), where k = 12 + macrocell index.
     */
    private void drawFeedbackWire(Graphics2D g, int macro, int midY) {
        int trunkX  = TRI_RIGHT + 6 + macro * 2;
        int channelY = 10 + macro * 6;

        int trueCol = 24 + macro * 2;
        int compCol = trueCol + 1;
        int trueX = COL_OFFSET + trueCol * CELL;
        int compX = COL_OFFSET + compCol * CELL;
        int midX  = (trueX + compX) / 2;

        g.setColor(FEEDBACK_COLOR);
        g.setStroke(new BasicStroke(1.2f));

        g.fillOval(trunkX - 2, midY - 2, 4, 4);

        g.drawLine(trunkX, midY, trunkX, channelY);
        g.drawLine(midX, channelY, trunkX, channelY);

        int splitY = channelY - 5;
        g.drawLine(midX, channelY, midX, splitY);
        g.drawLine(trueX, splitY, compX, splitY);

        g.drawLine(trueX, splitY, trueX, 0);

        int bub = 4;
        int bubY = splitY - bub - 1;
        g.drawLine(compX, splitY, compX, bubY + bub);
        g.draw(new java.awt.geom.Ellipse2D.Double(
                compX - bub / 2.0, bubY, bub, bub));
        g.drawLine(compX, bubY, compX, 0);

        g.setFont(getFont().deriveFont(java.awt.Font.PLAIN, 8f));
        g.drawString("Q" + (macro + 1), midX + 4, channelY - 1);
    }

    private static GeneralPath orGatePath(int x, int y, int w, int h) {
        GeneralPath p = new GeneralPath();
        double cx2 = x + w * 0.55;
        double tipX = x + w;

        p.moveTo(x, y);
        p.quadTo(x + w * 0.25, y + h * 0.20, cx2, y);
        p.quadTo(tipX - w * 0.10, y + h * 0.18, tipX, y + h / 2.0);
        p.quadTo(tipX - w * 0.10, y + h - h * 0.18, cx2, y + h);
        p.quadTo(x + w * 0.25, y + h - h * 0.20, x, y + h);
        p.quadTo(x + w * 0.18, y + h / 2.0, x, y);
        p.closePath();
        return p;
    }

    /* -------------------- net highlighting / connect points -------------------- */

    private void paintNetHighlights(Graphics2D g) {
        if (wireGraph == null) return;

        Stroke saved = g.getStroke();
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < wireGraph.segmentCount(); i++) {
            Color c = wireGraph.colorOf(i);
            if (c == null) continue;
            WireGraph.Seg s = wireGraph.segment(i);
            g.setColor(c);
            g.drawLine(s.x1(), s.y1(), s.x2(), s.y2());
        }
        g.setStroke(saved);
    }

    /**
     * Mirror of the geometry inside the various {@code paint*} methods, but
     * collected as a list of (x1,y1,x2,y2) line segments for {@link WireGraph}.
     * Any time the layout constants change, both this and the matching painter
     * must be updated together.
     */
    private List<WireGraph.Seg> enumerateWireSegments() {
        List<WireGraph.Seg> segs = new ArrayList<>();

        int colTop = 0;
        int colBot = ROW_OFFSET + map.rowCount() * CELL - CELL / 2;
        for (int c = 0; c < FuseMap.NUM_COLUMNS; c++) {
            int x = COL_OFFSET + c * CELL;
            segs.add(new WireGraph.Seg(x, colTop, x, colBot));
        }

        int xL = COL_OFFSET;
        for (int r = 0; r < map.rowCount(); r++) {
            RowInfo ri = map.row(r);
            int y = ROW_OFFSET + r * CELL;
            int xR = switch (ri.kind) {
                case PT -> GATE_LEFT + (int) (GATE_W * 0.18);
                case OE -> OUTPUT_AREA_X + 14 + 12;
                default -> OUTPUT_AREA_X;
            };
            segs.add(new WireGraph.Seg(xL, y, xR, y));
        }

        int firstPtRow = -1;
        int currentMacro = -1;
        for (int r = 0; r < map.rowCount(); r++) {
            RowInfo ri = map.row(r);
            if (ri.kind == RowKind.PT && ri.ptIndex == 0) {
                firstPtRow = r;
                currentMacro = ri.macrocell;
            } else if (ri.kind == RowKind.OE && firstPtRow >= 0) {
                enumerateMacrocellSegments(segs, currentMacro, firstPtRow, r - 1, r);
                firstPtRow = -1;
                currentMacro = -1;
            }
        }

        int pin1CenterX = COL_OFFSET + CELL / 2;
        segs.add(new WireGraph.Seg(pin1CenterX, 0, pin1CenterX, CLK_BUS_Y));
        segs.add(new WireGraph.Seg(pin1CenterX, CLK_BUS_Y, CLK_TRUNK_X, CLK_BUS_Y));
        int clkTrunkBot = ROW_OFFSET + (map.rowCount() - 2) * CELL;
        segs.add(new WireGraph.Seg(CLK_TRUNK_X, CLK_BUS_Y, CLK_TRUNK_X, clkTrunkBot));

        segs.add(new WireGraph.Seg(OUTPUT_AREA_X, ROW_OFFSET, OUTPUT_AREA_X, ASYNCH_Y_OFFSET));
        segs.add(new WireGraph.Seg(OUTPUT_AREA_X, ASYNCH_Y_OFFSET, TOTAL_WIDTH - 2, ASYNCH_Y_OFFSET));

        return segs;
    }

    private void enumerateMacrocellSegments(List<WireGraph.Seg> segs,
                                            int macro,
                                            int firstPtRow,
                                            int lastPtRow,
                                            int oeRow) {
        int yFirst = ROW_OFFSET + firstPtRow * CELL;
        int yLast  = ROW_OFFSET + lastPtRow  * CELL;
        int yOE    = ROW_OFFSET + oeRow      * CELL;
        int midY   = (yFirst + yLast) / 2;

        int gateTop = yFirst - CELL / 2 + 1;
        int gateBot = yLast  + CELL / 2 - 1;
        int gateH   = gateBot - gateTop;
        int logicH  = Math.max(36, gateH * 7 / 10);
        int logicY  = midY - logicH / 2;

        segs.add(new WireGraph.Seg(GATE_RIGHT, midY, LOGIC_X, midY));
        segs.add(new WireGraph.Seg(LOGIC_RIGHT, midY, TRI_X, midY));
        segs.add(new WireGraph.Seg(TRI_RIGHT, midY, TOTAL_WIDTH - 2, midY));

        int invX = OUTPUT_AREA_X + 14;
        int invW = 12;
        int bub  = 4;
        int invOutX = invX + invW + bub;
        int routeX  = LOGIC_X - 6;
        int routeY  = logicY + logicH + 6;
        int triEnX  = TRI_X + TRI_SIZE / 2;

        segs.add(new WireGraph.Seg(invOutX, yOE, routeX, yOE));
        segs.add(new WireGraph.Seg(routeX, yOE, routeX, routeY));
        segs.add(new WireGraph.Seg(routeX, routeY, triEnX, routeY));
        segs.add(new WireGraph.Seg(triEnX, routeY, triEnX, midY + TRI_SIZE / 2));

        int clkY = logicY + logicH - 8;
        segs.add(new WireGraph.Seg(CLK_TRUNK_X, clkY, LOGIC_X, clkY));

        int trunkX  = TRI_RIGHT + 6 + macro * 2;
        int channelY = 10 + macro * 6;
        int trueCol = 24 + macro * 2;
        int compCol = trueCol + 1;
        int trueX   = COL_OFFSET + trueCol * CELL;
        int compX   = COL_OFFSET + compCol * CELL;
        int midX    = (trueX + compX) / 2;
        int splitY  = channelY - 5;
        int bubY    = splitY - bub - 1;

        segs.add(new WireGraph.Seg(trunkX, midY, trunkX, channelY));
        segs.add(new WireGraph.Seg(midX, channelY, trunkX, channelY));
        segs.add(new WireGraph.Seg(midX, channelY, midX, splitY));
        segs.add(new WireGraph.Seg(trueX, splitY, compX, splitY));
        segs.add(new WireGraph.Seg(trueX, splitY, trueX, 0));
        segs.add(new WireGraph.Seg(compX, splitY, compX, bubY + bub));
        segs.add(new WireGraph.Seg(compX, bubY, compX, 0));
    }

    private static void drawCenteredMultiline(Graphics2D g, String text, int cx, int cy) {
        String[] lines = text.split("\n");
        int fh = g.getFontMetrics().getHeight();
        int totalH = fh * lines.length;
        int y = cy - totalH / 2 + g.getFontMetrics().getAscent();
        for (String line : lines) {
            int w = g.getFontMetrics().stringWidth(line);
            g.drawString(line, cx - w / 2, y);
            y += fh;
        }
    }
}
