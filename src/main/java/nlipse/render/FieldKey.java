package nlipse.render;

import java.util.List;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;

/** Cache key for data that changes the sampled scalar field. */
final class FieldKey {
    private final Object[] values;

    private FieldKey(final Object[] values) {
        this.values = values;
    }

    static FieldKey from(final RenderRequest request) {
        final PlotSnapshot snapshot = request.getSnapshot();
        final List<Focus> foci = snapshot.getFoci();
        final Object[] values = new Object[6 + foci.size() * 3];
        int index = 0;
        values[index++] = snapshot.getCurveType();
        values[index++] = snapshot.getViewport();
        values[index++] = request.getWidth();
        values[index++] = request.getHeight();
        values[index++] = request.getQuality().getSampleStep();
        values[index++] = foci.size();
        for (final Focus focus : foci) {
            values[index++] = Double.doubleToLongBits(focus.getX());
            values[index++] = Double.doubleToLongBits(focus.getY());
            values[index++] = Double.doubleToLongBits(focus.getWeight());
        }
        return new FieldKey(values);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FieldKey)) {
            return false;
        }
        final FieldKey key = (FieldKey) other;
        return java.util.Arrays.equals(values, key.values);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(values);
    }
}
