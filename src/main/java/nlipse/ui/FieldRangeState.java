package nlipse.ui;

import java.util.Optional;
import nlipse.math.ScalarRanges;
import nlipse.render.FieldExtrema;

/** Tracks the last exact slider range separately from diagnostic sampled extrema. */
final class FieldRangeState {
    enum Adjustment {
        NONE,
        CLAMP,
        AUTO_FIT
    }

    record Completion(boolean rangeChanged, double minimum, double maximum) {
    }

    private double fullMinimum;
    private double fullMaximum;
    private double sampledMinimum;
    private double sampledMaximum;
    private boolean sampledApproximate;
    private Adjustment pendingAdjustment = Adjustment.CLAMP;

    FieldRangeState(final double configuredMinimum, final double configuredMaximum) {
        fullMinimum = Math.min(0, configuredMinimum);
        fullMaximum = Math.max(fullMinimum + 1, configuredMaximum);
        sampledMinimum = fullMinimum;
        sampledMaximum = fullMaximum;
    }

    Completion observe(final Optional<FieldExtrema> extrema,
            final boolean precisionLimited, final double configuredMinimum,
            final double configuredMaximum) {
        if (extrema.isPresent()) {
            sampledMinimum = extrema.orElseThrow().minimum();
            sampledMaximum = extrema.orElseThrow().maximum();
        } else {
            sampledMinimum = Double.NaN;
            sampledMaximum = Double.NaN;
        }
        sampledApproximate = precisionLimited;

        // A limited render is useful for display and diagnostics, but its extrema
        // must never replace the exact slider mapping or persisted contour range.
        if (precisionLimited) {
            return unchanged(configuredMinimum, configuredMaximum);
        }

        if (extrema.isEmpty()) {
            pendingAdjustment = Adjustment.NONE;
            return unchanged(configuredMinimum, configuredMaximum);
        }

        setExactRange(sampledMinimum, sampledMaximum);
        final Adjustment adjustment = pendingAdjustment;
        pendingAdjustment = Adjustment.NONE;
        if (adjustment == Adjustment.NONE) {
            return unchanged(configuredMinimum, configuredMaximum);
        }

        double newMinimum = configuredMinimum;
        double newMaximum = configuredMaximum;
        if (adjustment == Adjustment.AUTO_FIT
                || configuredMaximum < fullMinimum
                || configuredMinimum > fullMaximum
                || configuredMinimum > configuredMaximum) {
            newMinimum = ScalarRanges.interpolate(fullMinimum, fullMaximum, 0.05);
            newMaximum = ScalarRanges.interpolate(fullMinimum, fullMaximum, 0.95);
        } else {
            newMinimum = Math.max(fullMinimum, configuredMinimum);
            newMaximum = Math.min(fullMaximum, configuredMaximum);
            if (newMinimum > newMaximum) {
                newMinimum = ScalarRanges.interpolate(fullMinimum, fullMaximum, 0.05);
                newMaximum = ScalarRanges.interpolate(fullMinimum, fullMaximum, 0.95);
            }
        }
        return sameDouble(configuredMinimum, newMinimum)
                && sameDouble(configuredMaximum, newMaximum)
                ? unchanged(configuredMinimum, configuredMaximum)
                : new Completion(true, newMinimum, newMaximum);
    }

    private void setExactRange(final double minimum, final double maximum) {
        fullMinimum = minimum;
        fullMaximum = maximum;
        if (fullMaximum > fullMinimum) {
            return;
        }
        final double centre = fullMinimum;
        final double padding = Math.max(1, Math.abs(centre) * 0.05);
        fullMinimum = centre - padding;
        fullMaximum = centre + padding;
        if (Double.isFinite(fullMinimum) && Double.isFinite(fullMaximum)) {
            return;
        }
        if (centre >= 0) {
            fullMinimum = Math.nextDown(centre);
            fullMaximum = centre;
        } else {
            fullMinimum = centre;
            fullMaximum = Math.nextUp(centre);
        }
    }

    private static Completion unchanged(final double minimum, final double maximum) {
        return new Completion(false, minimum, maximum);
    }

    void mark(final Adjustment adjustment) {
        if (adjustment == Adjustment.AUTO_FIT || pendingAdjustment == Adjustment.NONE) {
            pendingAdjustment = adjustment;
        }
    }

    void clearPendingAdjustment() {
        pendingAdjustment = Adjustment.NONE;
    }

    boolean hasPendingAdjustment() {
        return pendingAdjustment != Adjustment.NONE;
    }

    double sliderToValue(final int sliderValue, final int sliderTicks) {
        return ScalarRanges.interpolate(fullMinimum, fullMaximum,
                sliderValue / (double) sliderTicks);
    }

    int valueToSlider(final double value, final int sliderTicks) {
        if (fullMaximum <= fullMinimum) {
            return sliderTicks / 2;
        }
        final int sliderValue = (int) Math.round(sliderTicks
                * ScalarRanges.fraction(value, fullMinimum, fullMaximum));
        return Math.clamp(sliderValue, 0, sliderTicks);
    }

    double sampledMinimum() {
        return sampledMinimum;
    }

    double sampledMaximum() {
        return sampledMaximum;
    }

    boolean sampledApproximate() {
        return sampledApproximate;
    }

    double fullMinimum() {
        return fullMinimum;
    }

    double fullMaximum() {
        return fullMaximum;
    }

    static boolean sameDouble(final double first, final double second) {
        // Signed zero is already canonical model state; direct equality keeps it
        // equal while retaining every other representable change.
        return first == second;
    }
}
