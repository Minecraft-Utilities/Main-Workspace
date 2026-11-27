package xyz.mcutils.backend.common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

/**
 * The statistics behind a {@link NameModel}: how likely each symbol is given the preceding
 * symbols and the position in the name.
 *
 * <p>Two estimates are blended per symbol. The first backs off from the last four symbols (and, on
 * longer names, five) towards shorter contexts with Kneser-Ney style continuation counts. The
 * second backs off from the position plus the last three symbols. The weight of the second grows
 * as the first has less evidence, so rare contexts lean on the position.
 *
 * <p>Orders up to three are folded into dense base distributions; only the higher orders, which
 * are sparse, are looked up per symbol.
 */
final class NameModelTables {
    static final int SYMBOLS = NameModel.SYMBOLS;
    static final int CONTEXT_SYMBOLS = NameModel.CONTEXT_SYMBOLS;
    static final int COST_SCALE = 16;

    private static final int C2 = CONTEXT_SYMBOLS * CONTEXT_SYMBOLS;
    private static final int C3 = C2 * CONTEXT_SYMBOLS;
    private static final int C4 = C3 * CONTEXT_SYMBOLS;
    private static final double DISCOUNT = 0.9;
    private static final int FIFTH_ORDER_MIN_LENGTH = 8;
    private static final int FIFTH_ORDER_MIN_TOTAL = 10;
    private static final int BOUND_PARALLELISM = 2;

    final int length;
    final boolean fifthOrder;
    private final double weightFloor;
    private final double weightCeiling;
    private final double evidence;
    private final float[] baseHistory;
    private final float[] basePosition;
    private final SparseRows fourth;
    private final SparseRows fifth;
    private final SparseRows positional;
    private final int[] fourthRow;
    private final int[] positionalRow;
    // Bounds on the cost of the symbols from a position to the end, over every older context.
    private final short[][] minRest;
    private final short[][] maxRest;

    private NameModelTables(int length, boolean fifthOrder, float[] baseHistory, float[] basePosition,
                            SparseRows fourth, SparseRows fifth, SparseRows positional) {
        this.length = length;
        this.fifthOrder = fifthOrder;
        this.weightFloor = fifthOrder ? 0.3 : 0.4;
        this.weightCeiling = 0.7;
        this.evidence = fifthOrder ? 20 : 50;
        this.baseHistory = baseHistory;
        this.basePosition = basePosition;
        this.fourth = fourth;
        this.fifth = fifth;
        this.positional = positional;
        this.fourthRow = denseIndex(fourth, C4);
        this.positionalRow = denseIndex(positional, length * C3);
        this.minRest = new short[length + 1][C3];
        this.maxRest = new short[length + 1][C3];
        computeBounds();
    }

    static boolean usesFifthOrder(int length) {
        return length >= FIFTH_ORDER_MIN_LENGTH;
    }

    int minRest(int position, int context3) {
        return this.minRest[position][context3];
    }

    int maxRest(int position, int context3) {
        return this.maxRest[position][context3];
    }

    /**
     * Fills {@code out} with the cost of each symbol after the five symbols of {@code context5}.
     */
    void costs(int position, int context5, double[] history, double[] byPosition, int[] out) {
        int context3 = context5 % C3;
        int topTotal = fillHistory(context5, history);
        fillPosition(position, context3, byPosition);
        blendCosts(history, byPosition, topTotal, out);
    }

    private int fillHistory(int context5, double[] out) {
        int context3 = context5 % C3;
        int base = context3 * SYMBOLS;
        for (int s = 0; s < SYMBOLS; s++) {
            out[s] = this.baseHistory[base + s];
        }
        int total = 0;
        int row = this.fourthRow[context5 % C4];
        if (row >= 0) {
            total = apply(this.fourth, row, out);
        }
        if (this.fifthOrder) {
            total = 0;
            int fifthRow = this.fifth.find(context5);
            if (fifthRow >= 0) {
                total = apply(this.fifth, fifthRow, out);
            }
        }
        return total;
    }

    private void fillPosition(int position, int context3, double[] out) {
        int base = context3 * SYMBOLS;
        for (int s = 0; s < SYMBOLS; s++) {
            out[s] = this.basePosition[base + s];
        }
        int row = this.positionalRow[position * C3 + context3];
        if (row >= 0) {
            apply(this.positional, row, out);
        }
    }

    private double weight(int topTotal) {
        return this.weightFloor + (this.weightCeiling - this.weightFloor) * this.evidence / (topTotal + this.evidence);
    }

    private void blendCosts(double[] history, double[] byPosition, int topTotal, int[] out) {
        double w = weight(topTotal);
        for (int s = 0; s < SYMBOLS; s++) {
            out[s] = cost((1 - w) * history[s] + w * byPosition[s]);
        }
    }

    private static int cost(double probability) {
        return (int) Math.round(-Math.log(probability) * COST_SCALE);
    }

    /**
     * Interpolates {@code out}, the estimate from shorter contexts, with the counts of one row by
     * absolute discounting, and returns the row's total count.
     */
    private static int apply(SparseRows rows, int row, double[] out) {
        int from = rows.starts[row];
        int to = rows.starts[row + 1];
        int total = rows.total(row);
        double backoff = DISCOUNT * (to - from) / total;
        for (int s = 0; s < SYMBOLS; s++) {
            out[s] *= backoff;
        }
        for (int cell = from; cell < to; cell++) {
            out[rows.symbols[cell]] += (rows.counts[cell] - DISCOUNT) / total;
        }
        return total;
    }

    private static int[] denseIndex(SparseRows rows, int size) {
        int[] index = new int[size];
        Arrays.fill(index, -1);
        for (int row = 0; row < rows.rows(); row++) {
            index[rows.contexts[row]] = row;
        }
        return index;
    }

    private void computeBounds() {
        short[][] cheapest = new short[this.length][];
        short[][] dearest = new short[this.length][];
        try (ForkJoinPool pool = new ForkJoinPool(BOUND_PARALLELISM)) {
            pool.submit(() -> IntStream.range(0, this.length).parallel().forEach(position -> {
                short[][] steps = stepBounds(position);
                cheapest[position] = steps[0];
                dearest[position] = steps[1];
            })).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while bounding the name model", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to bound the name model", e.getCause());
        }
        for (int position = this.length - 1; position >= 0; position--) {
            for (int context3 = 0; context3 < C3; context3++) {
                int lowest = Integer.MAX_VALUE;
                int highest = Integer.MIN_VALUE;
                for (int s = 0; s < SYMBOLS; s++) {
                    int next = (context3 % C2) * CONTEXT_SYMBOLS + s;
                    lowest = Math.min(lowest, cheapest[position][context3 * SYMBOLS + s] + this.minRest[position + 1][next]);
                    highest = Math.max(highest, dearest[position][context3 * SYMBOLS + s] + this.maxRest[position + 1][next]);
                }
                this.minRest[position][context3] = (short) lowest;
                this.maxRest[position][context3] = (short) highest;
            }
        }
    }

    /**
     * The cheapest and costliest each symbol can be at {@code position} after three given symbols,
     * over every older context the model has seen and over the contexts it has not.
     */
    private short[][] stepBounds(int position) {
        double[] mostLikely = new double[C3 * SYMBOLS];
        double[] leastLikely = new double[C3 * SYMBOLS];
        Arrays.fill(leastLikely, Double.MAX_VALUE);
        double[] byPosition = new double[C3 * SYMBOLS];
        double[] scratch = new double[SYMBOLS];
        for (int context3 = 0; context3 < C3; context3++) {
            fillPosition(position, context3, scratch);
            System.arraycopy(scratch, 0, byPosition, context3 * SYMBOLS, SYMBOLS);
            for (int s = 0; s < SYMBOLS; s++) {
                scratch[s] = this.baseHistory[context3 * SYMBOLS + s];
            }
            widen(mostLikely, leastLikely, byPosition, context3, scratch, 0);
        }
        double[] history = new double[SYMBOLS];
        for (int row = 0; row < this.fourth.rows(); row++) {
            int context4 = this.fourth.contexts[row];
            int context3 = context4 % C3;
            for (int s = 0; s < SYMBOLS; s++) {
                history[s] = this.baseHistory[context3 * SYMBOLS + s];
            }
            int total = apply(this.fourth, row, history);
            widen(mostLikely, leastLikely, byPosition, context3, history, this.fifthOrder ? 0 : total);
        }
        if (this.fifthOrder) {
            for (int row = 0; row < this.fifth.rows(); row++) {
                int context5 = this.fifth.contexts[row];
                int context3 = context5 % C3;
                for (int s = 0; s < SYMBOLS; s++) {
                    history[s] = this.baseHistory[context3 * SYMBOLS + s];
                }
                int fourthRow = this.fourthRow[context5 % C4];
                if (fourthRow >= 0) {
                    apply(this.fourth, fourthRow, history);
                }
                int total = apply(this.fifth, row, history);
                widen(mostLikely, leastLikely, byPosition, context3, history, total);
            }
        }
        short[] cheapest = new short[C3 * SYMBOLS];
        short[] dearest = new short[C3 * SYMBOLS];
        for (int i = 0; i < cheapest.length; i++) {
            cheapest[i] = (short) cost(mostLikely[i]);
            dearest[i] = (short) cost(leastLikely[i]);
        }
        return new short[][]{cheapest, dearest};
    }

    private void widen(double[] mostLikely, double[] leastLikely, double[] byPosition, int context3, double[] history, int topTotal) {
        double w = weight(topTotal);
        int base = context3 * SYMBOLS;
        for (int s = 0; s < SYMBOLS; s++) {
            double probability = (1 - w) * history[s] + w * byPosition[base + s];
            mostLikely[base + s] = Math.max(mostLikely[base + s], probability);
            leastLikely[base + s] = Math.min(leastLikely[base + s], probability);
        }
    }

    void write(DataOutputStream out) throws IOException {
        out.writeBoolean(this.fifthOrder);
        for (float value : this.baseHistory) {
            out.writeFloat(value);
        }
        for (float value : this.basePosition) {
            out.writeFloat(value);
        }
        this.fourth.write(out);
        if (this.fifthOrder) {
            this.fifth.write(out);
        }
        this.positional.write(out);
    }

    static NameModelTables read(int length, DataInputStream in) throws IOException {
        boolean fifth = in.readBoolean();
        float[] history = new float[C3 * SYMBOLS];
        float[] position = new float[C3 * SYMBOLS];
        for (int i = 0; i < history.length; i++) {
            history[i] = in.readFloat();
        }
        for (int i = 0; i < position.length; i++) {
            position[i] = in.readFloat();
        }
        SparseRows fourth = SparseRows.read(in);
        SparseRows fifthRows = fifth ? SparseRows.read(in) : null;
        SparseRows positional = SparseRows.read(in);
        return new NameModelTables(length, fifth, history, position, fourth, fifthRows, positional);
    }

    /**
     * Derives the tables from the raw event counts of the training names.
     *
     * @param fourth counts of each symbol after four symbols
     * @param fifth counts after five symbols, or {@code null} when the length does not use them
     * @param positional counts after three symbols at each position
     */
    static NameModelTables derive(int length, SparseRows fourth, SparseRows fifth, SparseRows positional) {
        int[] raw3 = new int[C3 * SYMBOLS];
        int[] cont3 = new int[C3 * SYMBOLS];
        for (int row = 0; row < fourth.rows(); row++) {
            int base = (fourth.contexts[row] % C3) * SYMBOLS;
            for (int cell = fourth.starts[row]; cell < fourth.starts[row + 1]; cell++) {
                raw3[base + fourth.symbols[cell]] += fourth.counts[cell];
                cont3[base + fourth.symbols[cell]]++;
            }
        }
        int[] raw2 = marginal(raw3, C2);
        int[] raw1 = marginal(raw2, CONTEXT_SYMBOLS);
        int[] raw0 = marginal(raw1, 1);
        int[] cont2 = distinct(raw3, C2);
        int[] cont1 = distinct(raw2, CONTEXT_SYMBOLS);
        int[] cont0 = distinct(raw1, 1);

        float[] baseHistory = fold(cont0, cont1, cont2, cont3);
        float[] basePosition = fold(raw0, raw1, raw2, raw3);

        SparseRows runtimeFourth = fourth;
        SparseRows runtimeFifth = null;
        if (fifth != null) {
            runtimeFourth = fourth.withCounts(continuation(fourth, fifth));
            runtimeFifth = fifth.pruned(FIFTH_ORDER_MIN_TOTAL);
        }
        return new NameModelTables(length, fifth != null, baseHistory, basePosition, runtimeFourth, runtimeFifth, positional);
    }

    /**
     * For each cell of {@code fourth}, how many distinct five-symbol contexts extend it with that
     * symbol: the Kneser-Ney continuation count, used once a higher order sits above it.
     */
    private static int[] continuation(SparseRows fourth, SparseRows fifth) {
        int[] index = denseIndex(fourth, C4);
        int[] counts = new int[fourth.counts.length];
        for (int row = 0; row < fifth.rows(); row++) {
            int fourthRow = index[fifth.contexts[row] % C4];
            for (int cell = fifth.starts[row]; cell < fifth.starts[row + 1]; cell++) {
                for (int target = fourth.starts[fourthRow]; target < fourth.starts[fourthRow + 1]; target++) {
                    if (fourth.symbols[target] == fifth.symbols[cell]) {
                        counts[target]++;
                        break;
                    }
                }
            }
        }
        return counts;
    }

    private static int[] marginal(int[] counts, int contexts) {
        int[] out = new int[contexts * SYMBOLS];
        int sourceContexts = counts.length / SYMBOLS;
        for (int context = 0; context < sourceContexts; context++) {
            int base = (context % contexts) * SYMBOLS;
            for (int s = 0; s < SYMBOLS; s++) {
                out[base + s] += counts[context * SYMBOLS + s];
            }
        }
        return out;
    }

    private static int[] distinct(int[] counts, int contexts) {
        int[] out = new int[contexts * SYMBOLS];
        int sourceContexts = counts.length / SYMBOLS;
        for (int context = 0; context < sourceContexts; context++) {
            int base = (context % contexts) * SYMBOLS;
            for (int s = 0; s < SYMBOLS; s++) {
                if (counts[context * SYMBOLS + s] > 0) {
                    out[base + s]++;
                }
            }
        }
        return out;
    }

    /**
     * Interpolates the orders 0 to 3 into one distribution per three-symbol context.
     */
    private static float[] fold(int[] order0, int[] order1, int[] order2, int[] order3) {
        double[] root = new double[SYMBOLS];
        double total0 = 0;
        for (int s = 0; s < SYMBOLS; s++) {
            total0 += order0[s];
        }
        for (int s = 0; s < SYMBOLS; s++) {
            root[s] = (order0[s] + 1) / (total0 + SYMBOLS);
        }
        float[] out = new float[C3 * SYMBOLS];
        double[] row = new double[SYMBOLS];
        for (int context = 0; context < C3; context++) {
            System.arraycopy(root, 0, row, 0, SYMBOLS);
            discount(order1, (context % CONTEXT_SYMBOLS) * SYMBOLS, row);
            discount(order2, (context % C2) * SYMBOLS, row);
            discount(order3, context * SYMBOLS, row);
            for (int s = 0; s < SYMBOLS; s++) {
                out[context * SYMBOLS + s] = (float) row[s];
            }
        }
        return out;
    }

    private static void discount(int[] counts, int base, double[] row) {
        int total = 0;
        int distinct = 0;
        for (int s = 0; s < SYMBOLS; s++) {
            total += counts[base + s];
            if (counts[base + s] > 0) {
                distinct++;
            }
        }
        if (total == 0) {
            return;
        }
        double backoff = DISCOUNT * distinct / total;
        for (int s = 0; s < SYMBOLS; s++) {
            row[s] = Math.max(counts[base + s] - DISCOUNT, 0) / total + backoff * row[s];
        }
    }
}
