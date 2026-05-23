package com.example.atf22v10c;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Data model for the Atmel ATF22V10C AND-array fuse map.
 *
 * Layout (top -> bottom):
 *   - Row  0          : AR  (Asynchronous Reset product term)
 *   - For each of 10 output macrocells (i = 0..9):
 *       - 1 OE row
 *       - N product-term rows, where N = {8,10,12,14,16,16,14,12,10,8}[i]
 *   - Last row        : SP  (Synchronous Preset product term)
 *
 * Columns (left -> right): 44 columns, one for the true and one for the
 * complement of each of the 22 inputs. Column 2k carries the true input,
 * column 2k+1 carries its complement.
 *
 * Each fuse is either INTACT (connected -> drawn as a dot) or BLOWN
 * (disconnected -> empty crossing). All fuses default to INTACT, which is
 * the unprogrammed state of an ATF22V10C.
 */
public final class FuseMap {

    public static final int NUM_COLUMNS = 44;
    public static final int NUM_INPUTS = 22;
    public static final int NUM_MACROCELLS = 10;

    /** Product-term count per macrocell (output 1..10). */
    public static final int[] PT_PER_OUTPUT = {8, 10, 12, 14, 16, 16, 14, 12, 10, 8};

    /** Total product terms in the AND array, excluding AR/SP/OE. */
    public static final int TOTAL_PT = sum(PT_PER_OUTPUT); // 120

    /** Row kind, used for rendering and labelling. */
    public enum RowKind {
        AR,        // single asynchronous-reset term at the very top
        OE,        // one output-enable term per macrocell
        PT,        // ordinary product term (sum term contributor)
        SP         // single synchronous-preset term at the very bottom
    }

    /** Description of a single product-term row in the array. */
    public static final class RowInfo {
        public final RowKind kind;
        /** 0-based macrocell index this row belongs to (-1 for AR/SP). */
        public final int macrocell;
        /** PT index within the macrocell (0-based), -1 for non-PT rows. */
        public final int ptIndex;
        public final String label;

        RowInfo(RowKind kind, int macrocell, int ptIndex, String label) {
            this.kind = kind;
            this.macrocell = macrocell;
            this.ptIndex = ptIndex;
            this.label = label;
        }
    }

    private final List<RowInfo> rows;
    private final boolean[][] intact; // [row][col] -> true = connected

    public FuseMap() {
        this.rows = Collections.unmodifiableList(buildRows());
        this.intact = new boolean[rows.size()][NUM_COLUMNS];
        for (boolean[] r : intact) {
            Arrays.fill(r, true);
        }
    }

    private static List<RowInfo> buildRows() {
        List<RowInfo> out = new ArrayList<>();
        out.add(new RowInfo(RowKind.AR, -1, -1, "AR"));
        for (int mc = 0; mc < NUM_MACROCELLS; mc++) {
            int n = PT_PER_OUTPUT[mc];
            for (int p = 0; p < n; p++) {
                out.add(new RowInfo(RowKind.PT, mc, p, Integer.toString(p)));
            }
            out.add(new RowInfo(RowKind.OE, mc, -1, "OE"));
        }
        out.add(new RowInfo(RowKind.SP, -1, -1, "SP"));
        return out;
    }

    public int rowCount() {
        return rows.size();
    }

    public RowInfo row(int r) {
        return rows.get(r);
    }

    public List<RowInfo> rows() {
        return rows;
    }

    public boolean isIntact(int r, int c) {
        return intact[r][c];
    }

    public void set(int r, int c, boolean intactValue) {
        intact[r][c] = intactValue;
    }

    /** Toggle a single fuse; returns the new state. */
    public boolean toggle(int r, int c) {
        intact[r][c] = !intact[r][c];
        return intact[r][c];
    }

    public void setAll(boolean intactValue) {
        for (boolean[] r : intact) {
            Arrays.fill(r, intactValue);
        }
    }

    /** Count of intact (connected) fuses across the whole array. */
    public int intactCount() {
        int n = 0;
        for (boolean[] r : intact) {
            for (boolean v : r) {
                if (v) n++;
            }
        }
        return n;
    }

    public int totalFuses() {
        return rows.size() * NUM_COLUMNS;
    }

    /**
     * Label for a column: each input I_k (1-based for display) provides two
     * columns: true (k) and complement (~k). Columns are interleaved
     * {0,~0,1,~1,...} so column index c maps to input c/2.
     */
    public static String columnLabel(int c) {
        int input = c / 2;
        boolean complement = (c % 2) == 1;
        return (complement ? "~" : "") + input;
    }

    private static int sum(int[] a) {
        int s = 0;
        for (int v : a) s += v;
        return s;
    }
}
