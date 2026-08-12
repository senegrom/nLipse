package nlipse.math;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import nlipse.model.CurveType;
import nlipse.model.Focus;

/** Separates ordinary field throughput from deliberately exceptional arithmetic. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 750, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
public class FieldMathBenchmark {
    @State(Scope.Benchmark)
    public static class Fields {
        private static final int SAMPLE_COUNT = 256;

        DistanceField ellipse;
        DistanceField potential;
        DistanceField gaussian;
        DistanceField exceptionalEllipse;
        DistanceField exceptionalPotential;
        double[] xs;
        double[] ys;

        @Setup(Level.Trial)
        public void setup() {
            final List<Focus> ordinary = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                ordinary.add(new Focus(Math.cos(index * 0.7) * (1 + index * 0.08),
                        Math.sin(index * 0.7) * (1 + index * 0.08),
                        (index % 5 - 2) * 0.55 + 0.2));
            }
            ellipse = DistanceFields.create(CurveType.LIPSE, ordinary);
            potential = DistanceFields.create(CurveType.POTENTIAL, ordinary);
            gaussian = DistanceFields.create(CurveType.GAUSSIAN, ordinary, 1.25);
            exceptionalEllipse = DistanceFields.create(CurveType.LIPSE, List.of(
                    new Focus(0, 0, 2),
                    new Focus(Double.MIN_VALUE, 0, -2)));
            exceptionalPotential = DistanceFields.create(CurveType.POTENTIAL, List.of(
                    new Focus(8.460432894611084e18, 0, -3.841044956269343e35),
                    new Focus(1.2899577986045925e19, 0, 5.8564212468211325e35)));

            xs = new double[SAMPLE_COUNT];
            ys = new double[SAMPLE_COUNT];
            long state = 0x9e3779b97f4a7c15L;
            for (int index = 0; index < SAMPLE_COUNT; index++) {
                state ^= state << 13;
                state ^= state >>> 7;
                state ^= state << 17;
                xs[index] = ((state >>> 11) * 0x1.0p-53 - 0.5) * 8;
                state ^= state << 13;
                state ^= state >>> 7;
                state ^= state << 17;
                ys[index] = ((state >>> 11) * 0x1.0p-53 - 0.5) * 6;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(Fields.SAMPLE_COUNT)
    public double ordinaryEllipseBatch(final Fields fields) {
        return evaluateBatch(fields.ellipse, fields.xs, fields.ys);
    }

    @Benchmark
    @OperationsPerInvocation(Fields.SAMPLE_COUNT)
    public double ordinaryPotentialBatch(final Fields fields) {
        return evaluateBatch(fields.potential, fields.xs, fields.ys);
    }

    @Benchmark
    @OperationsPerInvocation(Fields.SAMPLE_COUNT)
    public double ordinaryGaussianBatch(final Fields fields) {
        return evaluateBatch(fields.gaussian, fields.xs, fields.ys);
    }

    @Benchmark
    public double exceptionalEllipse(final Fields fields) {
        return fields.exceptionalEllipse.value(Double.MAX_VALUE, 0);
    }

    @Benchmark
    public double exceptionalPotential(final Fields fields) {
        return fields.exceptionalPotential.value(0, 0);
    }

    private static double evaluateBatch(final DistanceField field,
            final double[] xs, final double[] ys) {
        double checksum = 0;
        for (int index = 0; index < xs.length; index++) {
            checksum += field.value(xs[index], ys[index]);
        }
        return checksum;
    }
}
