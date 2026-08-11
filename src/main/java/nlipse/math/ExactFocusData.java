package nlipse.math;

import java.math.BigDecimal;

/** Exact binary-double focal data, constructed lazily once per immutable field. */
final class ExactFocusData {
    private final BigDecimal[] xs;
    private final BigDecimal[] ys;
    private final BigDecimal[] weights;
    private final BigDecimal[] absoluteWeights;

    ExactFocusData(final FocusSet foci) {
        xs = new BigDecimal[foci.size()];
        ys = new BigDecimal[foci.size()];
        weights = new BigDecimal[foci.size()];
        absoluteWeights = new BigDecimal[foci.size()];
        for (int index = 0; index < foci.size(); index++) {
            xs[index] = AdaptiveDecimal.exact(foci.x(index));
            ys[index] = AdaptiveDecimal.exact(foci.y(index));
            weights[index] = AdaptiveDecimal.exact(foci.weight(index));
            absoluteWeights[index] = weights[index].abs();
        }
    }

    BigDecimal x(final int index) {
        return xs[index];
    }

    BigDecimal y(final int index) {
        return ys[index];
    }

    BigDecimal weight(final int index) {
        return weights[index];
    }

    BigDecimal absoluteWeight(final int index) {
        return absoluteWeights[index];
    }
}
