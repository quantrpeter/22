package com.example.atf22v10c;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Maintains the schematic-level wire graph for {@link FuseGridPanel} so the
 * user can drop "connect points" (junctions) at any wire crossing and have the
 * resulting net highlighted in a unique random color.
 *
 * Model:
 *   - The panel registers every line segment it draws (input columns, PT row
 *     lines, OR/LOGIC/TRI chains, OE routing, CLK, ASYNCH, feedback wires).
 *   - At construction we union-find any two segments that share an endpoint;
 *     that gives the "natural" nets formed by the wire layout alone.
 *   - The user adds a junction by clicking on the panel. We find every segment
 *     within {@link #CLICK_TOLERANCE} px of the click, union them all together
 *     and assign a fresh random color to the resulting net root. Two junctions
 *     that touch the same segment automatically merge their nets.
 */
public final class WireGraph {

    /** How close (px) a click must be to a wire segment to bind to it. */
    public static final int CLICK_TOLERANCE = 5;

    /** A single straight-line wire segment in panel coordinates. */
    public record Seg(int x1, int y1, int x2, int y2) {
        public double dist(double px, double py) {
            double dx = x2 - x1, dy = y2 - y1;
            double l2 = dx * dx + dy * dy;
            if (l2 < 0.25) return Math.hypot(px - x1, py - y1);
            double t = ((px - x1) * dx + (py - y1) * dy) / l2;
            if (t < 0) t = 0;
            else if (t > 1) t = 1;
            return Math.hypot(px - x1 - t * dx, py - y1 - t * dy);
        }
    }

    /** A user-placed connect point. */
    public static final class Junction {
        public final int x;
        public final int y;
        /** Any segment that belongs to this junction's net (used to look up the live color). */
        public final int repSeg;

        Junction(int x, int y, int repSeg) {
            this.x = x;
            this.y = y;
            this.repSeg = repSeg;
        }
    }

    private final List<Seg> segments;
    private final int[] parent;
    private final List<Junction> junctions = new ArrayList<>();
    private final Map<Integer, Color> netColors = new HashMap<>();
    private final Random random = new Random();

    public WireGraph(List<Seg> segments) {
        this.segments = List.copyOf(segments);
        this.parent = new int[this.segments.size()];
        resetUnionFind();
        seedNaturalConnectivity();
    }

    /* ----------------------------- public API ----------------------------- */

    public int segmentCount() { return segments.size(); }
    public Seg segment(int i)  { return segments.get(i); }
    public List<Junction> junctions() { return junctions; }

    /** Color of the net that segment {@code i} belongs to, or {@code null} if uncolored. */
    public Color colorOf(int segIdx) {
        return netColors.get(find(segIdx));
    }

    /**
     * Add a junction at the given panel coordinates. All wire segments within
     * {@link #CLICK_TOLERANCE} px of the click are merged into one net and the
     * net is assigned a fresh random color. Pre-existing colors of merged
     * sub-nets are discarded.
     *
     * @return {@code true} if any wire was close enough to bind to the junction.
     */
    public boolean addJunction(int x, int y) {
        List<Integer> near = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).dist(x, y) <= CLICK_TOLERANCE) {
                near.add(i);
            }
        }
        if (near.isEmpty()) return false;

        Set<Integer> oldRoots = new HashSet<>();
        for (int idx : near) oldRoots.add(find(idx));

        int anchor = near.get(0);
        for (int i = 1; i < near.size(); i++) union(anchor, near.get(i));
        int root = find(anchor);

        for (int r : oldRoots) {
            if (r != root) netColors.remove(r);
        }
        netColors.put(root, randomColor());

        junctions.add(new Junction(x, y, anchor));
        return true;
    }

    /** Remove the junction nearest to (x,y) within tolerance, if any. Returns true if removed. */
    public boolean removeJunctionNear(int x, int y) {
        int bestIdx = -1;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < junctions.size(); i++) {
            Junction j = junctions.get(i);
            double d = Math.hypot(j.x - x, j.y - y);
            if (d < bestDist) { bestDist = d; bestIdx = i; }
        }
        if (bestIdx < 0 || bestDist > CLICK_TOLERANCE + 4) return false;
        junctions.remove(bestIdx);
        rebuildFromJunctions();
        return true;
    }

    public void clear() {
        junctions.clear();
        netColors.clear();
        resetUnionFind();
        seedNaturalConnectivity();
    }

    /* ---------------------------- internals ------------------------------ */

    private void resetUnionFind() {
        for (int i = 0; i < parent.length; i++) parent[i] = i;
    }

    private void seedNaturalConnectivity() {
        for (int i = 0; i < segments.size(); i++) {
            for (int j = i + 1; j < segments.size(); j++) {
                if (sharesEndpoint(segments.get(i), segments.get(j))) {
                    union(i, j);
                }
            }
        }
    }

    /** Re-derive the union-find state from the natural layout + remaining junctions. */
    private void rebuildFromJunctions() {
        Map<Integer, Color> oldColors = new HashMap<>(netColors);
        netColors.clear();
        resetUnionFind();
        seedNaturalConnectivity();
        for (Junction j : junctions) {
            List<Integer> near = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                if (segments.get(i).dist(j.x, j.y) <= CLICK_TOLERANCE) near.add(i);
            }
            if (near.isEmpty()) continue;
            int anchor = near.get(0);
            for (int i = 1; i < near.size(); i++) union(anchor, near.get(i));
            int root = find(anchor);
            // Try to reuse a previous color for the junction's net if any.
            Color reused = oldColors.get(find(j.repSeg));
            netColors.put(root, reused != null ? reused : randomColor());
        }
    }

    private int find(int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private void union(int a, int b) {
        int ra = find(a), rb = find(b);
        if (ra != rb) parent[ra] = rb;
    }

    private static boolean sharesEndpoint(Seg a, Seg b) {
        int tol = 2;
        return  near(a.x1, a.y1, b.x1, b.y1, tol) ||
                near(a.x1, a.y1, b.x2, b.y2, tol) ||
                near(a.x2, a.y2, b.x1, b.y1, tol) ||
                near(a.x2, a.y2, b.x2, b.y2, tol);
    }

    private static boolean near(int x1, int y1, int x2, int y2, int tol) {
        return Math.abs(x1 - x2) <= tol && Math.abs(y1 - y2) <= tol;
    }

    /** Pleasant, well-saturated color chosen via HSB so two clicks rarely look alike. */
    private Color randomColor() {
        float hue = random.nextFloat();
        float sat = 0.65f + random.nextFloat() * 0.25f;
        float bri = 0.55f + random.nextFloat() * 0.30f;
        return Color.getHSBColor(hue, sat, bri);
    }
}
