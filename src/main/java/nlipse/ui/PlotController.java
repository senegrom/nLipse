package nlipse.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.TableModelEvent;
import nlipse.io.PlotExports;
import nlipse.math.DistanceField;
import nlipse.math.DistanceFields;
import nlipse.math.ScalarRanges;
import nlipse.model.CurveType;
import nlipse.model.Focus;
import nlipse.model.PlotConfig;
import nlipse.model.PlotConfigIO;
import nlipse.model.PlotModel;
import nlipse.model.PlotSnapshot;
import nlipse.render.AsyncRenderService;
import nlipse.render.FieldExtrema;
import nlipse.render.PlotRenderer;
import nlipse.render.RenderQuality;
import nlipse.render.RenderRequest;
import nlipse.render.RenderResult;
import nlipse.render.Viewport;

/** Coordinates model mutations, view events, rendering and durable background exports. */
public final class PlotController implements AutoCloseable {
    private static final int SLIDER_TICKS = 1000;
    private static final double ZOOM_STEP = 0.85;
    private static final double NUDGE_STEP = 0.1;
    private static final double NUDGE_FINE = 0.01;
    private static final double HIT_RADIUS = 11;

    private enum RangeAdjustment {
        NONE,
        CLAMP,
        AUTO_FIT
    }

    private final PlotModel model;
    private final PlotWindow view;
    private final PlotRenderer renderer;
    private final AsyncRenderService renderService;
    private final Timer previewTimer;
    private final Timer fullTimer;

    private boolean suppressSliders;
    private boolean suppressTable;
    private boolean suppressControls;
    private double fullMin;
    private double fullMax;
    private double sampledMin;
    private double sampledMax;
    private RangeAdjustment pendingRangeAdjustment = RangeAdjustment.CLAMP;
    private DistanceField cursorField;

    private int draggingFocus = -1;
    private boolean panning;
    private int panStartX;
    private int panStartY;
    private Viewport panStartViewport;

    public PlotController(final PlotModel model, final PlotWindow view,
            final PlotRenderer renderer, final AsyncRenderService renderService) {
        if (model == null || view == null || renderer == null || renderService == null) {
            throw new IllegalArgumentException("Model, view and render services are required");
        }
        this.model = model;
        this.view = view;
        this.renderer = renderer;
        this.renderService = renderService;
        fullMin = Math.min(0, model.getDistanceMin());
        fullMax = Math.max(fullMin + 1, model.getDistanceMax());
        sampledMin = fullMin;
        sampledMax = fullMax;
        refreshCursorField();

        previewTimer = new Timer(40, event -> submit(RenderQuality.PREVIEW));
        previewTimer.setRepeats(false);
        fullTimer = new Timer(180, event -> submit(RenderQuality.FULL));
        fullTimer.setRepeats(false);

        installControlListeners();
        installTableListeners();
        installMouseListeners();
        installKeyboardBindings();
        installWindowListeners();
        syncTableFromModel();
        syncSlidersFromModel();
        SwingUtilities.invokeLater(this::requestFullRender);
    }

    private void installControlListeners() {
        view.getCurveType().addActionListener(event -> {
            if (suppressControls) {
                return;
            }
            final CurveType type = (CurveType) view.getCurveType().getSelectedItem();
            if (type == null || type == model.getCurveType()) {
                return;
            }
            model.setCurveType(type);
            view.setCurvePresentation(type, model.getFamilyParameter());
            suppressControls = true;
            view.getLogSpacing().setSelected(type.defaultLogSpacing());
            suppressControls = false;
            model.setLogSpacing(type.defaultLogSpacing());
            refreshCursorField();
            markRangeAdjustment(RangeAdjustment.AUTO_FIT);
            requestFullRender();
        });

        view.getDistanceMin().addChangeListener(event -> distanceSliderChanged(true));
        view.getDistanceMax().addChangeListener(event -> distanceSliderChanged(false));

        view.getFamilyParameter().addActionListener(event -> applyFamilyParameter());
        view.getFamilyParameter().addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(final FocusEvent event) {
                applyFamilyParameter();
            }
        });

        view.getCurveCount().addActionListener(event -> applyCurveCount());
        view.getCurveCount().addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(final FocusEvent event) {
                applyCurveCount();
            }
        });

        view.getLogSpacing().addActionListener(event -> {
            if (!suppressControls) {
                model.setLogSpacing(view.getLogSpacing().isSelected());
                requestFullRender();
            }
        });
        view.getShowBackground().addActionListener(event -> {
            if (!suppressControls) {
                model.setShowBackground(view.getShowBackground().isSelected());
                requestFullRender();
            }
        });
        view.getShowExtrema().addActionListener(event -> {
            if (!suppressControls) {
                model.setShowExtrema(view.getShowExtrema().isSelected());
                requestFullRender();
            }
        });
        view.getAntiAlias().addActionListener(event -> {
            if (!suppressControls) {
                model.setAntiAlias(view.getAntiAlias().isSelected());
                requestFullRender();
            }
        });
        view.getShowLegend().addActionListener(event -> {
            if (!suppressControls) {
                model.setShowLegend(view.getShowLegend().isSelected());
                requestFullRender();
            }
        });
        view.getSaveSetup().addActionListener(event -> saveSetup());
        view.getLoadSetup().addActionListener(event -> loadSetup());
        view.getExportImage().addActionListener(event -> exportImage());
        view.getExportSvg().addActionListener(event -> exportSvg());

        view.getAddFocus().addActionListener(event -> {
            if (model.getFocusCount() >= PlotConfig.MAX_FOCI) {
                JOptionPane.showMessageDialog(view,
                        "At most " + PlotConfig.MAX_FOCI + " focus points are supported.",
                        "Focus limit", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            final Viewport viewport = model.getViewport();
            model.addFocus(new Focus(
                    ScalarRanges.interpolate(viewport.xMin(), viewport.xMax(), 0.5),
                    ScalarRanges.interpolate(viewport.yMin(), viewport.yMax(), 0.5), 1));
            refreshCursorField();
            syncTableFromModel();
            markRangeAdjustment(RangeAdjustment.CLAMP);
            requestFullRender();
        });
        view.getRemoveFocus().addActionListener(event -> removeFocusAt(view.getFocusTable().getSelectedRow()));
        view.getFitDistance().addActionListener(event -> {
            markRangeAdjustment(RangeAdjustment.AUTO_FIT);
            requestFullRender();
        });
        view.getResetView().addActionListener(event -> {
            model.resetViewport();
            markRangeAdjustment(RangeAdjustment.CLAMP);
            requestFullRender();
        });
    }

    private void installTableListeners() {
        view.getFocusTableModel().addTableModelListener(event -> {
            if (suppressTable || event.getType() != TableModelEvent.UPDATE) {
                return;
            }
            final int row = event.getFirstRow();
            final int column = event.getColumn();
            if (row < 0 || row >= model.getFocusCount() || column < 0 || column > 2) {
                return;
            }
            try {
                final double value = parseFinite(view.getFocusTableModel().getValueAt(row, column));
                final Focus focus = model.getFocus(row);
                if (column == 0) {
                    model.setFocusPosition(row, value, focus.y());
                } else if (column == 1) {
                    model.setFocusPosition(row, focus.x(), value);
                } else {
                    model.setFocusWeight(row, value);
                }
                refreshCursorField();
                markRangeAdjustment(RangeAdjustment.CLAMP);
                requestFullRender();
            } catch (final IllegalArgumentException exception) {
                syncTableFromModel();
            }
        });
        view.getFocusTable().getSelectionModel().addListSelectionListener(event -> {
            if (suppressTable || event.getValueIsAdjusting()) {
                return;
            }
            model.setSelectedFocusIndex(view.getFocusTable().getSelectedRow());
            requestFullRender();
        });
    }

    private void installMouseListeners() {
        final PlotCanvas canvas = view.getCanvas();
        canvas.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(final ComponentEvent event) {
                if (canvas.isShowing() && !panning) {
                    requestInteractiveRender();
                }
            }
        });

        final MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent event) {
                canvas.requestFocusInWindow();
                if (SwingUtilities.isMiddleMouseButton(event)) {
                    panning = true;
                    panStartX = event.getX();
                    panStartY = event.getY();
                    panStartViewport = model.getViewport();
                    previewTimer.stop();
                    fullTimer.stop();
                    renderService.cancel();
                    canvas.beginPanPreview();
                    return;
                }
                final int hit = hitTest(event.getX(), event.getY());
                if (SwingUtilities.isRightMouseButton(event)) {
                    removeFocusAt(hit);
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }
                if (hit >= 0) {
                    draggingFocus = hit;
                    selectFocus(hit);
                } else if (canvas.getWidth() >= 2 && canvas.getHeight() >= 2) {
                    if (model.getFocusCount() >= PlotConfig.MAX_FOCI) {
                        return;
                    }
                    final Viewport viewport = model.getViewport();
                    final int index = model.addFocus(new Focus(
                            viewport.worldX(event.getX(), canvas.getWidth()),
                            viewport.worldY(event.getY(), canvas.getHeight()), 1));
                    draggingFocus = index;
                    refreshCursorField();
                    syncTableFromModel();
                    markRangeAdjustment(RangeAdjustment.CLAMP);
                    requestInteractiveRender();
                }
            }

            @Override
            public void mouseReleased(final MouseEvent event) {
                if (panning) {
                    panning = false;
                    panStartViewport = null;
                    canvas.commitPanPreview();
                    previewTimer.stop();
                    fullTimer.stop();
                    requestFullRender();
                } else if (draggingFocus >= 0) {
                    draggingFocus = -1;
                    previewTimer.stop();
                    fullTimer.stop();
                    requestFullRender();
                }
            }

            @Override
            public void mouseMoved(final MouseEvent event) {
                updateCursorInfo(event.getX(), event.getY());
            }

            @Override
            public void mouseDragged(final MouseEvent event) {
                updateCursorInfo(event.getX(), event.getY());
                if (panning && panStartViewport != null
                        && canvas.getWidth() >= 2 && canvas.getHeight() >= 2) {
                    final int offsetX = event.getX() - panStartX;
                    final int offsetY = event.getY() - panStartY;
                    model.setViewport(panStartViewport.panPixels(offsetX, offsetY,
                            canvas.getWidth(), canvas.getHeight()));
                    markRangeAdjustment(RangeAdjustment.CLAMP);
                    if (canvas.isPanPreviewActive()) {
                        canvas.updatePanPreview(offsetX, offsetY);
                    } else {
                        requestInteractiveRender();
                    }
                } else if (draggingFocus >= 0 && draggingFocus < model.getFocusCount()
                        && canvas.getWidth() >= 2 && canvas.getHeight() >= 2) {
                    final Viewport viewport = model.getViewport();
                    model.setFocusPosition(draggingFocus,
                            viewport.worldX(event.getX(), canvas.getWidth()),
                            viewport.worldY(event.getY(), canvas.getHeight()));
                    refreshCursorField();
                    syncFocusRow(draggingFocus);
                    markRangeAdjustment(RangeAdjustment.CLAMP);
                    requestInteractiveRender();
                }
            }
        };
        // One adapter, registered for both delivery paths: mouseMoved only
        // reaches MouseMotionListener registrations, so registering it solely
        // as a MouseListener silently starves the hover coordinates.
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
        canvas.addMouseWheelListener(this::zoom);
    }

    private void installKeyboardBindings() {
        final PlotCanvas canvas = view.getCanvas();
        final InputMap inputMap = canvas.getInputMap(JComponent.WHEN_FOCUSED);
        final ActionMap actionMap = canvas.getActionMap();
        bind(inputMap, actionMap, KeyEvent.VK_DELETE, 0, "delete", () ->
                removeFocusAt(model.getSelectedFocusIndex()));
        bindNudge(inputMap, actionMap, KeyEvent.VK_LEFT, 0, -NUDGE_STEP, 0);
        bindNudge(inputMap, actionMap, KeyEvent.VK_RIGHT, 0, NUDGE_STEP, 0);
        bindNudge(inputMap, actionMap, KeyEvent.VK_UP, 0, 0, NUDGE_STEP);
        bindNudge(inputMap, actionMap, KeyEvent.VK_DOWN, 0, 0, -NUDGE_STEP);
        bindNudge(inputMap, actionMap, KeyEvent.VK_LEFT, KeyEvent.SHIFT_DOWN_MASK, -NUDGE_FINE, 0);
        bindNudge(inputMap, actionMap, KeyEvent.VK_RIGHT, KeyEvent.SHIFT_DOWN_MASK, NUDGE_FINE, 0);
        bindNudge(inputMap, actionMap, KeyEvent.VK_UP, KeyEvent.SHIFT_DOWN_MASK, 0, NUDGE_FINE);
        bindNudge(inputMap, actionMap, KeyEvent.VK_DOWN, KeyEvent.SHIFT_DOWN_MASK, 0, -NUDGE_FINE);
    }

    private void installWindowListeners() {
        view.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(final WindowEvent event) {
                commitPendingEdits();
                saveLastSession();
                close();
            }
        });
    }

    private void saveLastSession() {
        try {
            PlotConfigIO.save(PlotConfigIO.lastSessionFile(), model.snapshot());
        } catch (final IOException | RuntimeException failed) {
            // Last-session persistence is best-effort; never block closing.
            System.err.println("Could not save the last session: " + failed.getMessage());
        }
    }

    private void saveSetup() {
        if (!commitPendingEdits()) {
            return;
        }
        final JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save plot setup");
        chooser.setSelectedFile(new File("nlipse-setup.properties"));
        if (chooser.showSaveDialog(view) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            PlotConfigIO.save(chooser.getSelectedFile().toPath(), model.snapshot());
        } catch (final IOException failed) {
            JOptionPane.showMessageDialog(view, failed.getMessage(), "Save failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadSetup() {
        if (!commitPendingEdits()) {
            return;
        }
        final JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load plot setup");
        if (chooser.showOpenDialog(view) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final Path file = chooser.getSelectedFile().toPath();
        final PlotConfig config;
        try {
            config = PlotConfigIO.load(file);
        } catch (final IOException | IllegalArgumentException failed) {
            JOptionPane.showMessageDialog(view, failed.getMessage(), "Load failed",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        model.apply(config);
        suppressControls = true;
        view.syncControls(model.snapshot());
        suppressControls = false;
        refreshCursorField();
        syncTableFromModel();
        markRangeAdjustment(RangeAdjustment.CLAMP);
        requestFullRender();
    }

    @FunctionalInterface
    private interface ExportWriter {
        void write(RenderResult result, Path target) throws IOException;
    }

    private void exportImage() {
        exportPlot("PNG", "Export plot as PNG", "nlipse-plot.png", ".png",
                PlotExports::writePng);
    }

    private void exportSvg() {
        exportPlot("SVG", "Export plot as SVG", "nlipse-plot.svg", ".svg",
                PlotExports::writeSvg);
    }

    private void exportPlot(final String format, final String dialogTitle,
            final String suggestedName, final String extension, final ExportWriter writer) {
        if (!commitPendingEdits()) {
            return;
        }
        if (pendingRangeAdjustment != RangeAdjustment.NONE) {
            requestFullRender();
            JOptionPane.showMessageDialog(view,
                    "The field-level range is still being updated. Retry after the render finishes.",
                    "Export not ready", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        final PlotCanvas canvas = view.getCanvas();
        final int width = canvas.getWidth();
        final int height = canvas.getHeight();
        if (width < 2 || height < 2) {
            JOptionPane.showMessageDialog(view, "The plot area is not visible yet.",
                    "Export " + format, JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        final JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(dialogTitle);
        chooser.setSelectedFile(new File(suggestedName));
        if (chooser.showSaveDialog(view) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final Path target = withExtension(chooser.getSelectedFile().toPath(), extension);
        final RenderRequest request = new RenderRequest(
                model.snapshot(), width, height, RenderQuality.FULL);
        final boolean accepted;
        try {
            accepted = renderService.submitExport(request,
                    result -> writer.write(result, target),
                    result -> exportCompleted(format, target, result),
                    failure -> exportFailed(format, failure));
        } catch (final IllegalStateException closed) {
            exportFailed(format, closed);
            return;
        }
        if (!accepted) {
            JOptionPane.showMessageDialog(view,
                    "Another export is already active. Let it finish before starting a new one.",
                    "Export busy", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        view.getRenderInfo().setText("Exporting " + format + "…");
    }

    private void exportCompleted(final String format, final Path target,
            final RenderResult result) {
        final PlotCanvas canvas = view.getCanvas();
        result.renderPackage().ifPresent(completed -> {
            if (completed.snapshot().equals(model.snapshot())
                    && completed.width() == canvas.getWidth()
                    && completed.height() == canvas.getHeight()) {
                renderCompleted(result);
            }
        });
        view.getRenderInfo().setText(
                "Exported " + format + " · " + target.toAbsolutePath());
    }

    private void exportFailed(final String format, final Throwable failure) {
        final String message = failure.getMessage() == null
                ? failure.getClass().getSimpleName() : failure.getMessage();
        view.getRenderInfo().setText("Export failed");
        JOptionPane.showMessageDialog(view, message, "Export " + format + " failed",
                JOptionPane.ERROR_MESSAGE);
    }

    private static Path withExtension(final Path target, final String extension) {
        final String fileName = target.getFileName().toString();
        if (fileName.toLowerCase(Locale.ROOT).endsWith(extension)) {
            return target;
        }
        return target.resolveSibling(fileName + extension);
    }

    private void bindNudge(final InputMap inputMap, final ActionMap actionMap,
            final int keyCode, final int modifiers, final double dx, final double dy) {
        bind(inputMap, actionMap, keyCode, modifiers,
                "nudge-" + keyCode + '-' + modifiers, () -> nudge(dx, dy));
    }

    private static void bind(final InputMap inputMap, final ActionMap actionMap,
            final int keyCode, final int modifiers, final String name, final Runnable action) {
        inputMap.put(KeyStroke.getKeyStroke(keyCode, modifiers), name);
        actionMap.put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(final ActionEvent event) {
                action.run();
            }
        });
    }

    private void nudge(final double dx, final double dy) {
        final int selected = model.getSelectedFocusIndex();
        if (selected < 0 || selected >= model.getFocusCount()) {
            return;
        }
        final Focus focus = model.getFocus(selected);
        final double newX = focus.x() + dx;
        final double newY = focus.y() + dy;
        if (!Double.isFinite(newX) || !Double.isFinite(newY)) {
            return;
        }
        model.setFocusPosition(selected, newX, newY);
        refreshCursorField();
        syncFocusRow(selected);
        markRangeAdjustment(RangeAdjustment.CLAMP);
        requestFullRender();
    }

    private void zoom(final MouseWheelEvent event) {
        final PlotCanvas canvas = view.getCanvas();
        if (canvas.getWidth() < 2 || canvas.getHeight() < 2) {
            return;
        }
        final double scale = Math.pow(ZOOM_STEP, -event.getPreciseWheelRotation());
        if (!Double.isFinite(scale) || scale <= 0) {
            return;
        }
        model.setViewport(model.getViewport().zoomAtPixel(event.getX(), event.getY(),
                canvas.getWidth(), canvas.getHeight(), scale));
        markRangeAdjustment(RangeAdjustment.CLAMP);
        requestInteractiveRender();
    }

    private int hitTest(final int pixelX, final int pixelY) {
        final PlotCanvas canvas = view.getCanvas();
        if (canvas.getWidth() < 2 || canvas.getHeight() < 2) {
            return -1;
        }
        final Viewport viewport = model.getViewport();
        final List<Focus> foci = model.getFociCopy();
        int best = -1;
        double bestDistance = HIT_RADIUS * HIT_RADIUS;
        for (int index = 0; index < foci.size(); index++) {
            final Focus focus = foci.get(index);
            final double dx = viewport.pixelX(focus.x(), canvas.getWidth()) - pixelX;
            final double dy = viewport.pixelY(focus.y(), canvas.getHeight()) - pixelY;
            final double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        return best;
    }

    private void selectFocus(final int index) {
        model.setSelectedFocusIndex(index);
        suppressTable = true;
        view.getFocusTable().setRowSelectionInterval(index, index);
        suppressTable = false;
        requestInteractiveRender();
    }

    private void removeFocusAt(final int index) {
        if (!model.removeFocus(index)) {
            return;
        }
        refreshCursorField();
        syncTableFromModel();
        markRangeAdjustment(RangeAdjustment.CLAMP);
        requestFullRender();
    }

    private boolean commitPendingEdits() {
        if (view.getFocusTable().isEditing()
                && !view.getFocusTable().getCellEditor().stopCellEditing()) {
            return false;
        }
        applyFamilyParameter();
        applyCurveCount();
        return true;
    }

    private void applyFamilyParameter() {
        final CurveType type = model.getCurveType();
        if (!type.usesParameter()) {
            view.setCurvePresentation(type, model.getFamilyParameter());
            return;
        }
        try {
            final double parameter = type.parseParameter(view.getFamilyParameter().getText());
            if (Double.doubleToLongBits(parameter)
                    == Double.doubleToLongBits(model.getFamilyParameter())) {
                view.setCurvePresentation(type, parameter);
                return;
            }
            model.setFamilyParameter(parameter);
            view.setCurvePresentation(type, parameter);
            refreshCursorField();
            markRangeAdjustment(RangeAdjustment.AUTO_FIT);
            requestFullRender();
        } catch (final IllegalArgumentException exception) {
            view.setCurvePresentation(type, model.getFamilyParameter());
        }
    }

    private void applyCurveCount() {
        try {
            final int count = Integer.parseInt(view.getCurveCount().getText().trim());
            if (count != model.getCurveCount()) {
                model.setCurveCount(count);
                requestFullRender();
            } else {
                view.getCurveCount().setText(Integer.toString(count));
            }
        } catch (final IllegalArgumentException exception) {
            view.getCurveCount().setText(Integer.toString(model.getCurveCount()));
        }
    }

    private void distanceSliderChanged(final boolean minimumChanged) {
        if (suppressSliders) {
            return;
        }
        final JSlider minimum = view.getDistanceMin();
        final JSlider maximum = view.getDistanceMax();
        if (minimumChanged && minimum.getValue() > maximum.getValue()) {
            suppressSliders = true;
            maximum.setValue(minimum.getValue());
            suppressSliders = false;
        } else if (!minimumChanged && maximum.getValue() < minimum.getValue()) {
            suppressSliders = true;
            minimum.setValue(maximum.getValue());
            suppressSliders = false;
        }
        model.setDistanceRange(sliderToDistance(minimum.getValue()),
                sliderToDistance(maximum.getValue()));
        pendingRangeAdjustment = RangeAdjustment.NONE;
        updateDistanceLabels();
        final JSlider source = minimumChanged ? minimum : maximum;
        if (source.getValueIsAdjusting()) {
            requestInteractiveRender();
        } else {
            requestFullRender();
        }
    }

    private void requestInteractiveRender() {
        view.getCanvas().setRendering(true);
        if (!previewTimer.isRunning()) {
            previewTimer.start();
        }
        fullTimer.restart();
    }

    private void requestFullRender() {
        previewTimer.stop();
        fullTimer.stop();
        submit(RenderQuality.FULL);
    }

    private void submit(final RenderQuality quality) {
        final int width = view.getCanvas().getWidth();
        final int height = view.getCanvas().getHeight();
        if (width < 2 || height < 2 || !view.isDisplayable()) {
            return;
        }
        view.getCanvas().setRendering(true);
        final RenderRequest request = new RenderRequest(model.snapshot(), width, height, quality);
        renderService.submitInteractive(request, this::renderCompleted, this::renderFailed);
    }

    private void renderCompleted(final RenderResult result) {
        final FieldExtrema extrema = result.extrema().orElse(null);
        final boolean rangeChanged;
        if (extrema == null) {
            sampledMin = Double.NaN;
            sampledMax = Double.NaN;
            pendingRangeAdjustment = RangeAdjustment.NONE;
            rangeChanged = false;
        } else {
            sampledMin = extrema.minimum();
            sampledMax = extrema.maximum();
            fullMin = sampledMin;
            fullMax = sampledMax;
            if (fullMax <= fullMin) {
                final double centre = fullMin;
                final double padding = Math.max(1, Math.abs(centre) * 0.05);
                fullMin = centre - padding;
                fullMax = centre + padding;
                if (!Double.isFinite(fullMin) || !Double.isFinite(fullMax)) {
                    if (centre >= 0) {
                        fullMin = Math.nextDown(centre);
                        fullMax = centre;
                    } else {
                        fullMin = centre;
                        fullMax = Math.nextUp(centre);
                    }
                }
            }
            rangeChanged = applyPendingRangeAdjustment();
        }
        syncSlidersFromModel();
        view.getCanvas().setRenderResult(result);
        view.getRenderInfo().setText(String.format(Locale.ROOT,
                "%s · %.1f ms · %d×%d · cache %s%s",
                result.quality() == RenderQuality.FULL ? "Full" : "Preview",
                result.renderNanos() / 1_000_000.0,
                result.image().getWidth(), result.image().getHeight(),
                renderer.cacheSummary(), extrema == null ? " · no finite samples" : ""));
        if (rangeChanged) {
            requestFullRender();
        }
    }

    private void renderFailed(final Throwable throwable) {
        final String message = throwable.getMessage() == null
                ? throwable.getClass().getSimpleName() : throwable.getMessage();
        view.getCanvas().setMessage("Rendering failed: " + message);
        view.getRenderInfo().setText("Rendering failed");
    }

    private boolean applyPendingRangeAdjustment() {
        final RangeAdjustment adjustment = pendingRangeAdjustment;
        pendingRangeAdjustment = RangeAdjustment.NONE;
        if (adjustment == RangeAdjustment.NONE) {
            return false;
        }
        final double oldMin = model.getDistanceMin();
        final double oldMax = model.getDistanceMax();
        double newMin = oldMin;
        double newMax = oldMax;
        if (adjustment == RangeAdjustment.AUTO_FIT
                || oldMax < fullMin || oldMin > fullMax || oldMin > oldMax) {
            newMin = ScalarRanges.interpolate(fullMin, fullMax, 0.05);
            newMax = ScalarRanges.interpolate(fullMin, fullMax, 0.95);
        } else {
            newMin = Math.max(fullMin, oldMin);
            newMax = Math.min(fullMax, oldMax);
            if (newMin > newMax) {
                newMin = ScalarRanges.interpolate(fullMin, fullMax, 0.05);
                newMax = ScalarRanges.interpolate(fullMin, fullMax, 0.95);
            }
        }
        if (sameDouble(oldMin, newMin) && sameDouble(oldMax, newMax)) {
            return false;
        }
        model.setDistanceRange(newMin, newMax);
        return true;
    }

    private static boolean sameDouble(final double first, final double second) {
        final double scale = Math.max(1, Math.max(Math.abs(first), Math.abs(second)));
        return Math.abs(first - second) <= 1e-12 * scale;
    }

    private void markRangeAdjustment(final RangeAdjustment adjustment) {
        if (adjustment == RangeAdjustment.AUTO_FIT
                || pendingRangeAdjustment == RangeAdjustment.NONE) {
            pendingRangeAdjustment = adjustment;
        }
    }

    private void syncSlidersFromModel() {
        suppressSliders = true;
        view.getDistanceMin().setValue(distanceToSlider(model.getDistanceMin()));
        view.getDistanceMax().setValue(distanceToSlider(model.getDistanceMax()));
        suppressSliders = false;
        updateDistanceLabels();
    }

    private void updateDistanceLabels() {
        view.getDistanceMinLabel().setText(String.format(Locale.ROOT,
                "Level min: %.5g   (field min: %s)", model.getDistanceMin(),
                formatFieldValue(sampledMin)));
        view.getDistanceMaxLabel().setText(String.format(Locale.ROOT,
                "Level max: %.5g   (field max: %s)", model.getDistanceMax(),
                formatFieldValue(sampledMax)));
    }

    private double sliderToDistance(final int sliderValue) {
        return ScalarRanges.interpolate(fullMin, fullMax,
                sliderValue / (double) SLIDER_TICKS);
    }

    private int distanceToSlider(final double distance) {
        if (fullMax <= fullMin) {
            return SLIDER_TICKS / 2;
        }
        final int value = (int) Math.round(SLIDER_TICKS
                * ScalarRanges.fraction(distance, fullMin, fullMax));
        return Math.clamp(value, 0, SLIDER_TICKS);
    }

    private static String formatFieldValue(final double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.5g", value) : "unavailable";
    }

    private void syncTableFromModel() {
        suppressTable = true;
        view.setFocusRows(model.getFociCopy(), model.getSelectedFocusIndex());
        suppressTable = false;
    }

    private void syncFocusRow(final int index) {
        if (index < 0 || index >= model.getFocusCount()) {
            return;
        }
        final Focus focus = model.getFocus(index);
        suppressTable = true;
        view.getFocusTableModel().setValueAt(format(focus.x()), index, 0);
        view.getFocusTableModel().setValueAt(format(focus.y()), index, 1);
        view.getFocusTableModel().setValueAt(format(focus.weight()), index, 2);
        suppressTable = false;
    }

    private void refreshCursorField() {
        final PlotSnapshot snapshot = model.snapshot();
        cursorField = DistanceFields.create(snapshot.curveType(), snapshot.foci(),
                snapshot.familyParameter());
    }

    private void updateCursorInfo(final int pixelX, final int pixelY) {
        final PlotCanvas canvas = view.getCanvas();
        if (canvas.getWidth() < 2 || canvas.getHeight() < 2 || cursorField == null) {
            return;
        }
        final Viewport viewport = model.getViewport();
        final double x = viewport.worldX(pixelX, canvas.getWidth());
        final double y = viewport.worldY(pixelY, canvas.getHeight());
        final double value = cursorField.value(x, y);
        view.getCursorInfo().setText(String.format(Locale.ROOT, "(%.5g, %.5g)   f=%.6g", x, y, value));
    }

    private static double parseFinite(final Object value) {
        final double parsed;
        try {
            parsed = Double.parseDouble(String.valueOf(value).trim());
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException("Value is not a number");
        }
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException("Value must be finite");
        }
        return parsed;
    }

    private static String format(final double value) {
        if (value == Math.floor(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.6g", value);
    }

    @Override
    public void close() {
        previewTimer.stop();
        fullTimer.stop();
        renderService.close();
    }
}
