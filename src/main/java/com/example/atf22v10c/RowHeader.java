package com.example.atf22v10c;

import com.example.atf22v10c.FuseMap.RowInfo;
import com.example.atf22v10c.FuseMap.RowKind;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Left-side row labels: AR, OE, PT index, SP, plus macrocell tag on each block. */
public class RowHeader extends JPanel {

    static final int WIDTH = 70;

    private static final Color AR_SP_BG = new Color(232, 244, 255);
    private static final Color OE_BG    = new Color(255, 244, 215);
    private static final Color BLOCK_SEPARATOR = new Color(160, 160, 160);

    private final FuseMap map;

    public RowHeader(FuseMap map) {
        this.map = map;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(WIDTH,
                FuseGridPanel.ROW_OFFSET * 2 + map.rowCount() * FuseGridPanel.CELL));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int cell = FuseGridPanel.CELL;
            Font plain = getFont().deriveFont(Font.PLAIN, 10f);
            Font bold  = getFont().deriveFont(Font.BOLD, 10f);
            Font small = getFont().deriveFont(Font.PLAIN, 9f);

            RowKind prevKind = null;
            for (int r = 0; r < map.rowCount(); r++) {
                RowInfo ri = map.row(r);
                int y = FuseGridPanel.ROW_OFFSET + r * cell;
                int rowTop = y - cell / 2;

                Color bg = null;
                if (ri.kind == RowKind.AR || ri.kind == RowKind.SP) bg = AR_SP_BG;
                else if (ri.kind == RowKind.OE) bg = OE_BG;
                if (bg != null) {
                    g.setColor(bg);
                    g.fillRect(0, rowTop, WIDTH, cell);
                }

                if (ri.kind == RowKind.OE && r > 0) {
                    g.setColor(BLOCK_SEPARATOR);
                    g.drawLine(0, rowTop, WIDTH, rowTop);
                }
                if (ri.kind == RowKind.SP) {
                    g.setColor(BLOCK_SEPARATOR);
                    g.drawLine(0, rowTop, WIDTH, rowTop);
                }
                if (prevKind == RowKind.AR) {
                    g.setColor(BLOCK_SEPARATOR);
                    g.drawLine(0, rowTop, WIDTH, rowTop);
                }

                g.setColor(new Color(40, 40, 40));
                String text;
                Font f;
                switch (ri.kind) {
                    case AR -> { text = "AR";   f = bold;  }
                    case SP -> { text = "SP";   f = bold;  }
                    case OE -> { text = "OE";   f = bold;  }
                    case PT -> { text = ri.label; f = plain; }
                    default -> { text = "";     f = plain; }
                }
                g.setFont(f);
                int tw = g.getFontMetrics().stringWidth(text);
                g.drawString(text, WIDTH - tw - 6, y + 4);

                if (ri.kind == RowKind.PT && ri.ptIndex == 0) {
                    g.setColor(new Color(80, 80, 80));
                    g.setFont(small);
                    g.drawString("MC" + (ri.macrocell + 1), 4, y + 4);
                }

                prevKind = ri.kind;
            }
        } finally {
            g.dispose();
        }
    }
}
