package xyz.mcutils.backend.common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Symbol counts for the contexts that occur in training, laid out as sorted contexts with a
 * compressed row of (symbol, count) cells each. Only contexts that were seen take space, which is
 * what makes contexts of four and five symbols affordable.
 */
final class SparseRows {
    private static final int BIN_LIMIT = 65_536;
    private static final int EVENTS_PER_PASS = 4_000_000;

    final int[] contexts;
    // starts[row] .. starts[row + 1] is the cell range of contexts[row].
    final int[] starts;
    final byte[] symbols;
    final int[] counts;

    SparseRows(int[] contexts, int[] starts, byte[] symbols, int[] counts) {
        this.contexts = contexts;
        this.starts = starts;
        this.symbols = symbols;
        this.counts = counts;
    }

    int rows() {
        return this.contexts.length;
    }

    int find(int context) {
        int index = Arrays.binarySearch(this.contexts, context);
        return index >= 0 ? index : -1;
    }

    int total(int row) {
        int total = 0;
        for (int cell = this.starts[row]; cell < this.starts[row + 1]; cell++) {
            total += this.counts[cell];
        }
        return total;
    }

    SparseRows withCounts(int[] replacement) {
        return new SparseRows(this.contexts, this.starts, this.symbols, replacement);
    }

    /**
     * Keeps only the contexts whose counts sum to at least {@code minTotal}.
     */
    SparseRows pruned(int minTotal) {
        Builder builder = new Builder();
        for (int row = 0; row < rows(); row++) {
            if (total(row) < minTotal) {
                continue;
            }
            builder.startRow(this.contexts[row]);
            for (int cell = this.starts[row]; cell < this.starts[row + 1]; cell++) {
                builder.cell(this.symbols[cell], this.counts[cell]);
            }
        }
        return builder.build();
    }

    void write(DataOutputStream out) throws IOException {
        out.writeInt(rows());
        int previous = 0;
        for (int row = 0; row < rows(); row++) {
            writeVarint(out, this.contexts[row] - previous);
            previous = this.contexts[row];
            out.writeByte(this.starts[row + 1] - this.starts[row]);
            for (int cell = this.starts[row]; cell < this.starts[row + 1]; cell++) {
                out.writeByte(this.symbols[cell]);
                writeVarint(out, this.counts[cell]);
            }
        }
    }

    static SparseRows read(DataInputStream in) throws IOException {
        int rows = in.readInt();
        Builder builder = new Builder();
        int previous = 0;
        for (int row = 0; row < rows; row++) {
            previous += readVarint(in);
            builder.startRow(previous);
            int cells = in.readUnsignedByte();
            for (int cell = 0; cell < cells; cell++) {
                byte symbol = in.readByte();
                builder.cell(symbol, readVarint(in));
            }
        }
        return builder.build();
    }

    /**
     * Counts every (context, next symbol) event of the names in {@code data}, each name being
     * {@code length} symbols. A context is the previous {@code order} symbols, padded at the start
     * of the name, and with {@code positional} also the position within the name.
     *
     * <p>Events are gathered one key range at a time and sorted, so memory stays bounded by
     * {@link #EVENTS_PER_PASS} however many names there are.
     */
    static SparseRows count(byte[] data, int names, int length, int order, boolean positional) {
        long space = (long) NameModel.power(order) * (positional ? length : 1);
        int shift = 0;
        while ((space >> shift) > BIN_LIMIT) {
            shift++;
        }
        long[] histogram = new long[(int) ((space - 1) >> shift) + 1];
        int histogramShift = shift;
        scan(data, names, length, order, positional, (key, symbol) -> histogram[key >> histogramShift]++);

        Builder builder = new Builder();
        int bin = 0;
        while (bin < histogram.length) {
            int first = bin;
            long events = 0;
            while (bin < histogram.length && (events == 0 || events + histogram[bin] <= EVENTS_PER_PASS)) {
                events += histogram[bin++];
            }
            if (events == 0) {
                continue;
            }
            long low = (long) first << shift;
            long high = (long) bin << shift;
            long[] gathered = new long[(int) events];
            int[] filled = {0};
            scan(data, names, length, order, positional, (key, symbol) -> {
                if (key >= low && key < high) {
                    gathered[filled[0]++] = ((long) key << 6) | symbol;
                }
            });
            Arrays.parallelSort(gathered);
            appendRuns(gathered, builder);
        }
        return builder.build();
    }

    private static void appendRuns(long[] sorted, Builder builder) {
        int index = 0;
        int currentKey = -1;
        while (index < sorted.length) {
            long event = sorted[index];
            int run = 1;
            while (index + run < sorted.length && sorted[index + run] == event) {
                run++;
            }
            int key = (int) (event >>> 6);
            if (key != currentKey) {
                builder.startRow(key);
                currentKey = key;
            }
            builder.cell((byte) (event & 63), run);
            index += run;
        }
    }

    private interface Events {
        void accept(int key, int symbol);
    }

    private static void scan(byte[] data, int names, int length, int order, boolean positional, Events events) {
        int span = NameModel.power(order);
        int dropOldest = NameModel.power(order - 1);
        int padded = 0;
        for (int i = 0; i < order; i++) {
            padded = padded * NameModel.CONTEXT_SYMBOLS + NameModel.PAD;
        }
        for (int name = 0; name < names; name++) {
            int base = name * length;
            int context = padded;
            for (int position = 0; position < length; position++) {
                int symbol = data[base + position];
                events.accept(positional ? position * span + context : context, symbol);
                context = (context % dropOldest) * NameModel.CONTEXT_SYMBOLS + symbol;
            }
        }
    }

    private static void writeVarint(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarint(DataInputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        int next;
        do {
            next = in.readUnsignedByte();
            value |= (next & 0x7F) << shift;
            shift += 7;
        } while ((next & 0x80) != 0);
        return value;
    }

    static final class Builder {
        private int[] contexts = new int[1024];
        private int[] starts = new int[1025];
        private byte[] symbols = new byte[4096];
        private int[] counts = new int[4096];
        private int rows;
        private int cells;

        void startRow(int context) {
            if (this.rows == this.contexts.length) {
                this.contexts = Arrays.copyOf(this.contexts, this.rows * 2);
                this.starts = Arrays.copyOf(this.starts, this.rows * 2 + 1);
            }
            this.contexts[this.rows] = context;
            this.starts[this.rows] = this.cells;
            this.rows++;
        }

        void cell(byte symbol, int count) {
            if (this.cells == this.symbols.length) {
                this.symbols = Arrays.copyOf(this.symbols, this.cells * 2);
                this.counts = Arrays.copyOf(this.counts, this.cells * 2);
            }
            this.symbols[this.cells] = symbol;
            this.counts[this.cells] = count;
            this.cells++;
        }

        SparseRows build() {
            int[] finalStarts = Arrays.copyOf(this.starts, this.rows + 1);
            finalStarts[this.rows] = this.cells;
            return new SparseRows(
                    Arrays.copyOf(this.contexts, this.rows),
                    finalStarts,
                    Arrays.copyOf(this.symbols, this.cells),
                    Arrays.copyOf(this.counts, this.cells));
        }
    }
}
