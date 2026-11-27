package xyz.mcutils.backend.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Character model of the usernames of one length, used to list candidate names from most to least
 * likely without materialising the name space.
 *
 * <p>Each symbol is scored from the preceding symbols and its position (see
 * {@link NameModelTables}). A name's cost is the sum of its symbol costs, {@code -ln(p)} scaled and
 * rounded to an integer, so names group into exact integer cost bands and a position is a
 * (cost, last name) pair that can be resumed in {@code O(length)}. Within a band names are ordered
 * by alphabet index.
 *
 * <p>Instances are immutable; the cursor is only meaningful for the model it came from.
 */
public final class NameModel {
    public static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789_";

    static final int SYMBOLS = ALPHABET.length();
    static final int PAD = SYMBOLS;
    static final int CONTEXT_SYMBOLS = SYMBOLS + 1;
    private static final int CONTEXT3 = CONTEXT_SYMBOLS * CONTEXT_SYMBOLS * CONTEXT_SYMBOLS;
    private static final int CONTEXT4 = CONTEXT3 * CONTEXT_SYMBOLS;
    private static final int START_CONTEXT = padded(5);
    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 16;
    private static final int FORMAT_VERSION = 2;

    private final int length;
    private final NameModelTables tables;

    private NameModel(int length, NameModelTables tables) {
        this.length = length;
        this.tables = tables;
    }

    public int length() {
        return this.length;
    }

    public Cursor start() {
        return new Cursor(this.tables.minRest(0, START_CONTEXT % CONTEXT3), null);
    }

    /**
     * Returns up to {@code limit} names following {@code from}, in ascending cost order. The page
     * is shorter than {@code limit} only when the model has no further names.
     */
    public Page next(Cursor from, int limit) {
        int lastBand = this.maxCost();
        List<String> names = new ArrayList<>(limit);
        int band = from.cost();
        String after = from.lastName();
        int emittedBand = band;
        String emitted = after;
        Walk walk = new Walk(limit, names);
        while (names.size() < limit && band <= lastBand) {
            int before = names.size();
            walk.run(band, after == null ? null : symbolsOf(after));
            if (names.size() > before) {
                emittedBand = band;
                emitted = names.getLast();
            }
            if (names.size() < limit) {
                band++;
                after = null;
            }
        }
        if (names.size() < limit) {
            return new Page(names, new Cursor(lastBand + 1, null), true);
        }
        return new Page(names, new Cursor(emittedBand, emitted), false);
    }

    public byte[] toBytes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(FORMAT_VERSION);
            out.writeInt(this.length);
            this.tables.write(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static NameModel fromBytes(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported name model format " + version);
            }
            int length = in.readInt();
            validateLength(length);
            return new NameModel(length, NameModelTables.read(length, in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    int costOf(String name) {
        double[] history = new double[SYMBOLS];
        double[] byPosition = new double[SYMBOLS];
        int[] costs = new int[SYMBOLS];
        int context = START_CONTEXT;
        int total = 0;
        int position = 0;
        for (int symbol : symbolsOf(name)) {
            this.tables.costs(position++, context, history, byPosition, costs);
            total += costs[symbol];
            context = nextContext(context, symbol);
        }
        return total;
    }

    static int power(int exponent) {
        int value = 1;
        for (int i = 0; i < exponent; i++) {
            value *= CONTEXT_SYMBOLS;
        }
        return value;
    }

    private static int padded(int symbols) {
        int value = 0;
        for (int i = 0; i < symbols; i++) {
            value = value * CONTEXT_SYMBOLS + PAD;
        }
        return value;
    }

    private int maxCost() {
        return this.tables.maxRest(0, START_CONTEXT % CONTEXT3);
    }

    private static int nextContext(int context, int symbol) {
        return (context % CONTEXT4) * CONTEXT_SYMBOLS + symbol;
    }

    private static void validateLength(int length) {
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new IllegalArgumentException("Name length must be " + MIN_LENGTH + "-" + MAX_LENGTH + ": " + length);
        }
    }

    private int[] symbolsOf(String name) {
        if (name.length() != this.length) {
            throw new IllegalArgumentException("Expected a name of length " + this.length + ": " + name);
        }
        int[] symbols = new int[this.length];
        for (int i = 0; i < this.length; i++) {
            symbols[i] = ALPHABET.indexOf(name.charAt(i));
            if (symbols[i] < 0) {
                throw new IllegalArgumentException("Name outside the alphabet: " + name);
            }
        }
        return symbols;
    }

    /**
     * A position in the ranked name list: the band being walked and the last name returned from
     * it, or {@code null} at the start of the band.
     */
    public record Cursor(int cost, String lastName) {}

    public record Page(List<String> names, Cursor next, boolean exhausted) {}

    /**
     * Collects the real names of one length to train a model on.
     */
    public static final class Trainer {
        private final int length;
        private final boolean fifthOrder;
        private byte[] symbols = new byte[1 << 16];
        private int names;

        public Trainer(int length) {
            this(length, NameModelTables.usesFifthOrder(length));
        }

        Trainer(int length, boolean fifthOrder) {
            validateLength(length);
            this.length = length;
            this.fifthOrder = fifthOrder;
        }

        /**
         * @param name a name of this trainer's length in the model's alphabet
         */
        public synchronized void add(String name) {
            int[] indexes = new int[this.length];
            if (name.length() != this.length) {
                throw new IllegalArgumentException("Expected a name of length " + this.length + ": " + name);
            }
            for (int i = 0; i < this.length; i++) {
                indexes[i] = ALPHABET.indexOf(name.charAt(i));
                if (indexes[i] < 0) {
                    throw new IllegalArgumentException("Name outside the alphabet: " + name);
                }
            }
            int needed = (this.names + 1) * this.length;
            if (needed > this.symbols.length) {
                this.symbols = Arrays.copyOf(this.symbols, Math.max(needed, this.symbols.length * 2));
            }
            for (int i = 0; i < this.length; i++) {
                this.symbols[this.names * this.length + i] = (byte) indexes[i];
            }
            this.names++;
        }

        public synchronized NameModel build() {
            SparseRows fourth = SparseRows.count(this.symbols, this.names, this.length, 4, false);
            SparseRows fifth = this.fifthOrder ? SparseRows.count(this.symbols, this.names, this.length, 5, false) : null;
            SparseRows positional = SparseRows.count(this.symbols, this.names, this.length, 3, true);
            return new NameModel(this.length, NameModelTables.derive(this.length, fourth, fifth, positional));
        }
    }

    /**
     * Depth-first walk over the names of exactly one cost band, pruned by the cheapest and
     * costliest way to finish a prefix. When a seek is given, names up to and including it are
     * skipped.
     */
    private final class Walk {
        private final int limit;
        private final List<String> out;
        private final char[] name = new char[length];
        private final int[][] costs = new int[length][SYMBOLS];
        private final double[] history = new double[SYMBOLS];
        private final double[] byPosition = new double[SYMBOLS];
        private int[] seek;

        Walk(int limit, List<String> out) {
            this.limit = limit;
            this.out = out;
        }

        void run(int band, int[] seek) {
            this.seek = seek;
            int startContext3 = START_CONTEXT % CONTEXT3;
            if (band < tables.minRest(0, startContext3) || band > tables.maxRest(0, startContext3)) {
                return;
            }
            descend(0, START_CONTEXT, band, seek != null);
        }

        private boolean descend(int position, int context, int budget, boolean seeking) {
            int[] stepCosts = this.costs[position];
            tables.costs(position, context, this.history, this.byPosition, stepCosts);
            boolean last = position == length - 1;
            for (int symbol = seeking ? this.seek[position] : 0; symbol < SYMBOLS; symbol++) {
                int left = budget - stepCosts[symbol];
                if (left < 0) {
                    continue;
                }
                int next = nextContext(context, symbol);
                int nextContext3 = next % CONTEXT3;
                if (left < tables.minRest(position + 1, nextContext3) || left > tables.maxRest(position + 1, nextContext3)) {
                    continue;
                }
                this.name[position] = ALPHABET.charAt(symbol);
                boolean onSeekPath = seeking && symbol == this.seek[position];
                if (last) {
                    if (!onSeekPath) {
                        this.out.add(new String(this.name));
                        if (this.out.size() >= this.limit) {
                            return true;
                        }
                    }
                    continue;
                }
                if (descend(position + 1, next, left, onSeekPath)) {
                    return true;
                }
            }
            return false;
        }
    }
}
