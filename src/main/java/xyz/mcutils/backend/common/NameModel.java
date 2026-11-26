package xyz.mcutils.backend.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Character model of the usernames of one length, used to list candidate names from most to least
 * likely without materialising the name space.
 *
 * <p>Each symbol is scored by the previous three symbols. A name's cost is the sum of its
 * transition costs, {@code -ln(p)} scaled and rounded to an integer, so names group into exact
 * integer cost bands and a position is a (cost, last name) pair that can be resumed in
 * {@code O(length)}. Within a band names are ordered by alphabet index.
 *
 * <p>Instances are immutable; the cursor is only meaningful for the model it came from.
 */
public final class NameModel {
    public static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789_";

    private static final int SYMBOLS = ALPHABET.length();
    private static final int PAD = SYMBOLS;
    private static final char PAD_CHAR = '^';
    private static final int CONTEXT_SYMBOLS = SYMBOLS + 1;
    private static final int PAIRS = CONTEXT_SYMBOLS * CONTEXT_SYMBOLS;
    private static final int CONTEXTS = PAIRS * CONTEXT_SYMBOLS;
    private static final int START_CONTEXT = (PAD * CONTEXT_SYMBOLS + PAD) * CONTEXT_SYMBOLS + PAD;
    private static final int COST_SCALE = 16;
    private static final double SMOOTHING = 0.3;
    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 16;
    private static final int FORMAT_VERSION = 1;

    private final int length;
    private final int[] cost;
    // minRest[n][context] and maxRest[n][context] bound the cost of writing n more symbols.
    private final int[][] minRest;
    private final int[][] maxRest;

    private NameModel(int length, int[] cost) {
        this.length = length;
        this.cost = cost;
        this.minRest = new int[length + 1][];
        this.maxRest = new int[length + 1][];
        this.minRest[0] = new int[CONTEXTS];
        this.maxRest[0] = new int[CONTEXTS];
        for (int rest = 1; rest <= length; rest++) {
            int[] min = new int[CONTEXTS];
            int[] max = new int[CONTEXTS];
            int[] previousMin = this.minRest[rest - 1];
            int[] previousMax = this.maxRest[rest - 1];
            for (int context = 0; context < CONTEXTS; context++) {
                int lowest = Integer.MAX_VALUE;
                int highest = Integer.MIN_VALUE;
                for (int symbol = 0; symbol < SYMBOLS; symbol++) {
                    int next = nextContext(context, symbol);
                    int step = cost[context * SYMBOLS + symbol];
                    lowest = Math.min(lowest, step + previousMin[next]);
                    highest = Math.max(highest, step + previousMax[next]);
                }
                min[context] = lowest;
                max[context] = highest;
            }
            this.minRest[rest] = min;
            this.maxRest[rest] = max;
        }
    }

    public int length() {
        return this.length;
    }

    public Cursor start() {
        return new Cursor(this.minRest[this.length][START_CONTEXT], null);
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
        while (names.size() < limit && band <= lastBand) {
            int before = names.size();
            new Walk(band, after == null ? null : symbolsOf(after), limit, names).run();
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
            for (int value : this.cost) {
                out.writeShort(value);
            }
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
            int[] cost = new int[CONTEXTS * SYMBOLS];
            for (int i = 0; i < cost.length; i++) {
                cost[i] = in.readShort();
            }
            return new NameModel(length, cost);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    int costOf(String name) {
        int context = START_CONTEXT;
        int total = 0;
        for (int symbol : symbolsOf(name)) {
            total += this.cost[context * SYMBOLS + symbol];
            context = nextContext(context, symbol);
        }
        return total;
    }

    private int maxCost() {
        return this.maxRest[this.length][START_CONTEXT];
    }

    private static int nextContext(int context, int symbol) {
        return (context % PAIRS) * CONTEXT_SYMBOLS + symbol;
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
     * Counts how often each symbol follows each three-symbol context in real names of one length.
     */
    public static final class Trainer {
        private final int length;
        private final long[] counts = new long[CONTEXTS * SYMBOLS];

        public Trainer(int length) {
            validateLength(length);
            this.length = length;
        }

        /**
         * @param context the three preceding characters, {@code ^} standing for the start of the name
         */
        public void add(String context, char symbol, long count) {
            int symbolIndex = ALPHABET.indexOf(symbol);
            if (symbolIndex < 0) {
                throw new IllegalArgumentException("Symbol outside the alphabet: " + symbol);
            }
            this.counts[contextOf(context) * SYMBOLS + symbolIndex] += count;
        }

        public NameModel build() {
            int[] cost = new int[CONTEXTS * SYMBOLS];
            for (int context = 0; context < CONTEXTS; context++) {
                long total = 0;
                for (int symbol = 0; symbol < SYMBOLS; symbol++) {
                    total += this.counts[context * SYMBOLS + symbol];
                }
                double denominator = total + SMOOTHING * SYMBOLS;
                for (int symbol = 0; symbol < SYMBOLS; symbol++) {
                    double probability = (this.counts[context * SYMBOLS + symbol] + SMOOTHING) / denominator;
                    cost[context * SYMBOLS + symbol] = (int) Math.round(-Math.log(probability) * COST_SCALE);
                }
            }
            return new NameModel(this.length, cost);
        }

        private static int contextOf(String context) {
            if (context.length() != 3) {
                throw new IllegalArgumentException("Context must be three characters: " + context);
            }
            int value = 0;
            for (int i = 0; i < 3; i++) {
                char c = context.charAt(i);
                int symbol = c == PAD_CHAR ? PAD : ALPHABET.indexOf(c);
                if (symbol < 0) {
                    throw new IllegalArgumentException("Context outside the alphabet: " + context);
                }
                value = value * CONTEXT_SYMBOLS + symbol;
            }
            return value;
        }
    }

    /**
     * Depth-first walk over the names of exactly one cost band, pruned by the cheapest and
     * costliest way to finish a prefix. When a seek is given, names up to and including it are
     * skipped.
     */
    private final class Walk {
        private final int band;
        private final int[] seek;
        private final int limit;
        private final List<String> out;
        private final char[] name = new char[length];

        Walk(int band, int[] seek, int limit, List<String> out) {
            this.band = band;
            this.seek = seek;
            this.limit = limit;
            this.out = out;
        }

        void run() {
            if (this.band < minRest[length][START_CONTEXT] || this.band > maxRest[length][START_CONTEXT]) {
                return;
            }
            descend(0, START_CONTEXT, this.band, this.seek != null);
        }

        private boolean descend(int position, int context, int budget, boolean seeking) {
            int rest = length - position - 1;
            for (int symbol = seeking ? this.seek[position] : 0; symbol < SYMBOLS; symbol++) {
                int left = budget - cost[context * SYMBOLS + symbol];
                if (left < 0) {
                    continue;
                }
                int next = nextContext(context, symbol);
                if (left < minRest[rest][next] || left > maxRest[rest][next]) {
                    continue;
                }
                this.name[position] = ALPHABET.charAt(symbol);
                boolean onSeekPath = seeking && symbol == this.seek[position];
                if (rest == 0) {
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
