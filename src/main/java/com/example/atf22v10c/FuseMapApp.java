package com.example.atf22v10c;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

/** Top-level frame for the ATF22V10C interactive fuse map viewer. */
public class FuseMapApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(FuseMapApp::launch);
    }

    private static void launch() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        FuseMap map = new FuseMap();
        FuseGridPanel grid = new FuseGridPanel(map);
        RowHeader rowHeader = new RowHeader(map);
        InputStrip inputStrip = new InputStrip();

        JScrollPane scroll = new JScrollPane(grid,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setColumnHeaderView(inputStrip);
        scroll.setRowHeaderView(rowHeader);
        scroll.setViewportBorder(BorderFactory.createLineBorder(new Color(180, 180, 180)));

        JLabel status = new JLabel(" ");
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        status.setFont(status.getFont().deriveFont(Font.PLAIN, 12f));

        JLabel counter = new JLabel();
        counter.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        counter.setFont(counter.getFont().deriveFont(Font.PLAIN, 12f));

        Runnable refreshCounter = () -> {
            int intact = map.intactCount();
            int total = map.totalFuses();
            counter.setText("fuses: " + intact + " connected / " + total + " total");
        };
        refreshCounter.run();

        grid.setStatusListener(text -> {
            status.setText(text);
            refreshCounter.run();
        });

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton connectAll = new JButton("Connect all");
        JButton blowAll = new JButton("Disconnect all");
        JButton clearNets = new JButton("Clear connect points");
        connectAll.addActionListener(e -> {
            map.setAll(true);
            grid.repaint();
            refreshCounter.run();
        });
        blowAll.addActionListener(e -> {
            map.setAll(false);
            grid.repaint();
            refreshCounter.run();
        });
        clearNets.addActionListener(e -> {
            grid.getWireGraph().clear();
            grid.repaint();
        });
        JLabel hint = new JLabel(
                "Left-click: highlight net (and toggle the fuse if you hit one) \u2022 click a colored dot to remove it");
        hint.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        toolbar.add(connectAll);
        toolbar.add(blowAll);
        toolbar.addSeparator();
        toolbar.add(clearNets);
        toolbar.addSeparator();
        toolbar.add(hint);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.X_AXIS));
        south.add(status);
        south.add(Box.createHorizontalGlue());
        south.add(counter);

        JFrame frame = new JFrame("ATF22V10C Fuse Map");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(toolbar, BorderLayout.NORTH);
        frame.add(scroll, BorderLayout.CENTER);
        frame.add(south, BorderLayout.SOUTH);
        frame.setPreferredSize(new Dimension(1180, 820));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
