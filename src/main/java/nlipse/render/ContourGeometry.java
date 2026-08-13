package nlipse.render;

import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nlipse.math.DistanceField;

/** Stitched contour topology stored in world coordinates, independent of paint settings. */
final class ContourGeometry {
    private static final double ENDPOINT_QUANTIZATION = 1_000_000.0;

    private final List<List<Polyline>> polylines;
    private final long estimatedBytes;

    private ContourGeometry(final List<List<Polyline>> polylines) {
        this.polylines = List.copyOf(polylines);
        long bytes = 128L;
        for (final List<Polyline> levelLines : polylines) {
            bytes += 48;
            for (final Polyline line : levelLines) {
                bytes += line.estimatedBytes();
            }
        }
        estimatedBytes = bytes;
    }

    static ContourGeometry trace(final FieldGrid grid, final DistanceField field,
            final Viewport viewport, final double[] levels, final CancellationToken token) {
        if (grid == null || field == null || viewport == null || levels == null || token == null) {
            throw new IllegalArgumentException(
                    "Grid, field, viewport, levels and cancellation token are required");
        }
        final List<LevelAssembler> assemblers = new ArrayList<>(levels.length);
        for (int index = 0; index < levels.length; index++) {
            assemblers.add(new LevelAssembler(viewport, grid.getPixelWidth(), grid.getPixelHeight()));
        }
        MarchingSquares.traceLevels(grid, field, viewport, levels, token,
                (levelIndex, x1, y1, x2, y2) -> assemblers.get(levelIndex)
                        .add(x1, y1, x2, y2));

        final List<List<Polyline>> lines = new ArrayList<>(levels.length);
        for (final LevelAssembler assembler : assemblers) {
            token.throwIfCancelled();
            lines.add(assembler.stitch());
        }
        return new ContourGeometry(lines);
    }

    int levelCount() {
        return polylines.size();
    }

    List<Polyline> polylines(final int index) {
        return polylines.get(index);
    }

    long estimatedBytes() {
        return estimatedBytes;
    }

    Path2D.Double path(final int levelIndex, final Viewport viewport,
            final int width, final int height, final CancellationToken token) {
        final Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO);
        int lineIndex = 0;
        for (final Polyline line : polylines.get(levelIndex)) {
            if ((lineIndex++ & 31) == 0) {
                token.throwIfCancelled();
            }
            line.appendTo(path, viewport, width, height);
        }
        return path;
    }

    private static Endpoint endpoint(final double pixelX, final double pixelY) {
        return new Endpoint(Math.round(pixelX * ENDPOINT_QUANTIZATION),
                Math.round(pixelY * ENDPOINT_QUANTIZATION));
    }

    static final class Polyline {
        private final double[] coordinates;
        private final boolean closed;

        Polyline(final double[] coordinates, final boolean closed) {
            if (coordinates.length < 4 || (coordinates.length & 1) != 0) {
                throw new IllegalArgumentException("A contour polyline requires at least two points");
            }
            this.coordinates = coordinates;
            this.closed = closed;
        }

        int pointCount() {
            return coordinates.length / 2;
        }

        boolean closed() {
            return closed;
        }

        double x(final int pointIndex) {
            return coordinates[pointIndex * 2];
        }

        double y(final int pointIndex) {
            return coordinates[pointIndex * 2 + 1];
        }

        long estimatedBytes() {
            return 48L + (long) coordinates.length * Double.BYTES;
        }

        private void appendTo(final Path2D.Double path, final Viewport viewport,
                final int width, final int height) {
            path.moveTo(viewport.pixelX(coordinates[0], width),
                    viewport.pixelY(coordinates[1], height));
            for (int index = 2; index < coordinates.length; index += 2) {
                path.lineTo(viewport.pixelX(coordinates[index], width),
                        viewport.pixelY(coordinates[index + 1], height));
            }
            if (closed) {
                path.closePath();
            }
        }
    }

    private static final class LevelAssembler {
        private final Viewport viewport;
        private final int width;
        private final int height;
        private final List<Segment> segments = new ArrayList<>();
        private final Map<Endpoint, List<Integer>> adjacency = new HashMap<>();

        LevelAssembler(final Viewport viewport, final int width, final int height) {
            this.viewport = viewport;
            this.width = width;
            this.height = height;
        }

        void add(final double pixelX1, final double pixelY1,
                final double pixelX2, final double pixelY2) {
            final Endpoint first = endpoint(pixelX1, pixelY1);
            final Endpoint second = endpoint(pixelX2, pixelY2);
            if (first.equals(second)) {
                return;
            }
            final Segment segment = new Segment(first, second,
                    viewport.worldX(pixelX1, width), viewport.worldY(pixelY1, height),
                    viewport.worldX(pixelX2, width), viewport.worldY(pixelY2, height));
            final int index = segments.size();
            segments.add(segment);
            adjacency.computeIfAbsent(first, ignored -> new ArrayList<>(2)).add(index);
            adjacency.computeIfAbsent(second, ignored -> new ArrayList<>(2)).add(index);
        }

        List<Polyline> stitch() {
            if (segments.isEmpty()) {
                return List.of();
            }
            final boolean[] visited = new boolean[segments.size()];
            final List<Polyline> result = new ArrayList<>();

            for (final Map.Entry<Endpoint, List<Integer>> entry : adjacency.entrySet()) {
                if (entry.getValue().size() == 2) {
                    continue;
                }
                for (final int index : entry.getValue()) {
                    if (!visited[index]) {
                        result.add(follow(index, entry.getKey(), visited));
                    }
                }
            }
            for (int index = 0; index < segments.size(); index++) {
                if (!visited[index]) {
                    result.add(follow(index, segments.get(index).first(), visited));
                }
            }
            return List.copyOf(result);
        }

        private int degree(final Endpoint endpoint) {
            final List<Integer> incident = adjacency.get(endpoint);
            return incident == null ? 0 : incident.size();
        }

        private Polyline follow(final int initialSegment, final Endpoint start,
                final boolean[] visited) {
            final DoubleBuilder points = new DoubleBuilder();
            Endpoint current = start;
            int segmentIndex = initialSegment;
            boolean closed = false;
            appendEndpoint(points, segments.get(segmentIndex), current);

            while (segmentIndex >= 0) {
                final Segment segment = segments.get(segmentIndex);
                visited[segmentIndex] = true;
                final Endpoint next = segment.other(current);
                if (next.equals(start)) {
                    closed = true;
                    break;
                }
                appendEndpoint(points, segment, next);
                if (degree(next) != 2) {
                    break;
                }
                current = next;
                segmentIndex = nextUnvisited(current, visited);
            }
            return new Polyline(points.toArray(), closed);
        }

        private int nextUnvisited(final Endpoint endpoint, final boolean[] visited) {
            final List<Integer> incident = adjacency.get(endpoint);
            if (incident == null) {
                return -1;
            }
            for (final int index : incident) {
                if (!visited[index]) {
                    return index;
                }
            }
            return -1;
        }

        private static void appendEndpoint(final DoubleBuilder points,
                final Segment segment, final Endpoint endpoint) {
            if (endpoint.equals(segment.first())) {
                points.add(segment.x1());
                points.add(segment.y1());
            } else {
                points.add(segment.x2());
                points.add(segment.y2());
            }
        }
    }

    private record Endpoint(long x, long y) {
    }

    private record Segment(Endpoint first, Endpoint second,
            double x1, double y1, double x2, double y2) {
        Endpoint other(final Endpoint endpoint) {
            return endpoint.equals(first) ? second : first;
        }
    }

    private static final class DoubleBuilder {
        private double[] values = new double[32];
        private int size;

        void add(final double value) {
            if (size == values.length) {
                final double[] expanded = new double[values.length * 2];
                System.arraycopy(values, 0, expanded, 0, values.length);
                values = expanded;
            }
            values[size++] = value;
        }

        double[] toArray() {
            final double[] result = new double[size];
            System.arraycopy(values, 0, result, 0, size);
            return result;
        }
    }
}
