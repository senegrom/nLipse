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
import java.util.Optional;
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

    enum RangeAdjustment {
        NONE,
        CLAMP,
        AUTO_FIT
    }

    record RangeResolution(
            double fullMin,
            double fullMax,
            double levelMin,
            double levelMax,
            boolean rangeChanged,
            boolean adjustmentDeferred) {
    }

    private final PlotModel model;
    private final PlotWindow view;
    private final PlotRenderer renderer;
    private final AsyncRenderService renderService;
    private final AsyncCursorService cursorService;
    private final Timer previewTimer;
    private final Timer fullTimer;

    private boolean closed;
    private boolean suppressSliders;
    private boolean suppressTable;
    private boolean suppressControls;
    private double fullMin;
    private double fullMax;
    private double sampledMin;
    private double sampledMax;
    private boolean sampledRangeApproximate;
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
        cursorService = new AsyncCursorService();
        fullMin = Math.min(0, model.getDistanceMin());
        fullMax = initialFullMaximum(fullMin, model.getDistanceMax());
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
        view.curveType.addActionListener(event -> {
            if (suppressControls) {
                return;
            }
            final CurveType type = (CurveType) view.curveType.getSelectedItem();
            if (type == null || type == model.getCurveType()) {
                return;
            }
            if (!commitPendingEdits()) {
                suppressControls = true;
                view.curveType.setSelectedItem(model.getCurveType());
                suppressControls = false;
                return;
            }
            model.setCurveType(type);
            view.setCurvePresentation(type, model.getFamilyParameter());
            suppressControls = true;
            view.logSpacing.setSelected(type.defaultLogSpacing());
            suppressControls = false;
            model.setLogSpacing(type.defaultLogSpacing());
            refreshCursorField();
            markRangeAdjustment(RangeAdjustment.AUTO_FIT);
            requestFullRender(true);
        });

        view.distanceMin.addChangeListener(event -> distanceSliderChanged(true));
        view.distanceMax.addChangeListener(event -> distanceSliderChanged(false));

        view.familyParameter.addActionListener(event -> applyFamilyParameter());
        view.familyParameter.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(final FocusEvent event) {
                applyFamilyParameter();
            }
        });

        view.curveCount.addActionListener(event -> applyCurveCount());
        view.curveCount.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(final FocusEvent event) {
                applyCurveCount();
            }
        });

        view.logSpacing.addActionListener(event -> {
            if (!suppressControls) {
                model.setLogSpacing(view.logSpacing.isSelected());
                requestFullRender();
            }
        });
        view.showBackground.addActionListener(event -> {
            if (!suppressControls) {
                model.setShowBackground(view.showBackground.isSelected());
                requestFullRender();
            }
        });
        view.showExtrema.addActionListener(event -> {
            if (!suppressControls) {
                model.setShowExtrema(view.showExtrema.isSelected());
                requestFullRender();
            }
        });
        view.antiAlias.addActionListener(event -> {
            if (!suppressControls) {
                model.setAntiAlias(view.antiAlias.isSelected());
                requestFullRender();
            }
        });
        view.showLegend.addActionListener(event -> {
            if (!suppressControls) {
                model.setShowLegend(view.showLegend.isSelected());
                requestFullRender();
            }
        });
        view.saveSetup.addActionListener(event -> saveSetup());
        view.loadSetup.addActionListener(event -> loadSetup());
        view.exportImage.addActionListener(event -> exportImage());
        view.exportSvg.addActionListener(event -> exportSvg());

        view.addFocus.addActionListener(event -> {
            if (!commitPendingEdits()) {
                return;
            }
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
        view.removeFocus.addActionListener(event -> removeFocusAt(view.focusTable.getSelectedRow()));
        view.fitDistance.addActionListener(event -> {
            if (!commitPendingEdits()) {
                return;
            }
            markRangeAdjustment(RangeAdjustment.AUTO_FIT);
            requestFullRender(true);
        });
        view.resetView.addActionListener(event -> {
            if (!commitPendingEdits()) {
                return;
            }
            invalidateCursorInfo();
            model.resetViewport();
            markRangeAdjustment(RangeAdjustment.CLAMP);
            requestFullRender();
        });
    }

    private void installTableListeners() {
        view.focusTableModel.addTableModelListener(event -> {
            if (suppressTable || event.getType() != TableModelEvent.UPDATE) {
                return;
            }
            final int row = event.getFirstRow();
            final int column = event.getColumn();
            if (row < 0 || row >= model.getFocusCount() || column < 0 || column > 2) {
                return;
            }
            try {
                final double value = EditableNumbers.parseFinite(
                        view.focusTableModel.getValueAt(row, column), "Value");
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
        view.focusTable.getSelectionModel().addListSelectionListener(event -> {
            if (suppressTable || event.getValueIsAdjusting()) {
                return;
            }
            model.setSelectedFocusIndex(view.focusTable.getSelectedRow());
            requestFullRender();
        });
    }

    private void installMouseListeners() {
        final PlotCanvas canvas = view.canvas;
        canvas.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(final ComponentEvent event) {
                if (canvas.isShowing() && !panning) {
                    invalidateCursorInfo();
                    requestInteractiveRender();
                }
            }
        });

        final MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent event) {
                if (!commitPendingEdits()) {
                    return;
                }
                canvas.requestFocusInWindow();
                if (SwingUtilities.isMiddleMouseButton(event)) {
                    invalidateCursorInfo();
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
            public void mouseExited(final MouseEvent event) {
                invalidateCursorInfo();
            }

            @Override
            public void mouseDragged(final MouseEvent event) {
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
                // Capture the coordinates and field after the drag mutation.
                updateCursorInfo(event.getX(), event.getY());
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
        final PlotCanvas canvas = view.canvas;
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
            public void windowClosing(final WindowEvent event) {
                // Closing must always succeed: an unfinished invalid edit is
                // dropped instead of holding the window open.
                if (!commitPendingEdits()) {
                    discardPendingEdits();
                }
                saveLastSession();
                view.dispose();
            }

            @Override
            public void windowClosed(final WindowEvent event) {
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
        final Optional<Path> target = approvedSaveTarget(chooser.getSelectedFile().toPath(), "");
        if (target.isEmpty()) {
            return;
        }
        try {
            PlotConfigIO.save(target.orElseThrow(), model.snapshot());
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
        renderService.cancel();
        model.apply(config);
        suppressControls = true;
        view.syncControls(model.snapshot());
        suppressControls = false;
        refreshCursorField();
        syncTableFromModel();
        resetRangeStateAfterLoad();
        syncSlidersFromModel();
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
            requestFullRender(true);
            JOptionPane.showMessageDialog(view,
                    "The exact field-level range is still being updated. Retry after the render finishes.",
                    "Export not ready", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        final PlotCanvas canvas = view.canvas;
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
        final Optional<Path> approved = approvedSaveTarget(
                chooser.getSelectedFile().toPath(), extension);
        if (approved.isEmpty()) {
            return;
        }
        final Path target = approved.orElseThrow();
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
        view.renderInfo.setText("Exporting " + format + "…");
    }

    private void exportCompleted(final String format, final Path target,
            final RenderResult result) {
        final PlotCanvas canvas = view.canvas;
        result.renderPackage().ifPresent(completed -> {
            if (completed.snapshot().equals(model.snapshot())
                    && completed.width() == canvas.getWidth()
                    && completed.height() == canvas.getHeight()) {
                renderCompleted(result);
            }
        });
        view.renderInfo.setText(
                "Exported " + format + " · " + target.toAbsolutePath());
    }

    private void exportFailed(final String format, final Throwable failure) {
        final String message = failure.getMessage() == null
                ? failure.getClass().getSimpleName() : failure.getMessage();
        view.renderInfo.setText("Export failed");
        JOptionPane.showMessageDialog(view, message, "Export " + format + " failed",
                JOptionPane.ERROR_MESSAGE);
    }

    private Optional<Path> approvedSaveTarget(final Path selected, final String extension) {
        try {
            return SaveTargets.approve(selected, extension, target ->
                    JOptionPane.showConfirmDialog(view,
                            "Replace the existing file?\n" + target.toAbsolutePath(),
                            "Confirm replacement", JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION);
        } catch (final IllegalArgumentException invalid) {
            JOptionPane.showMessageDialog(view, invalid.getMessage(), "Invalid destination",
                    JOptionPane.ERROR_MESSAGE);
            return Optional.empty();
        }
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
        if (!commitPendingEdits()) {
            return;
        }
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
        final PlotCanvas canvas = view.canvas;
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
        updateCursorInfo(event.getX(), event.getY());
    }

    private int hitTest(final int pixelX, final int pixelY) {
        final PlotCanvas canvas = view.canvas;
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
        view.focusTable.setRowSelectionInterval(index, index);
        suppressTable = false;
        requestInteractiveRender();
    }

    private void removeFocusAt(final int index) {
        if (!commitPendingEdits() || !model.removeFocus(index)) {
            return;
        }
        refreshCursorField();
        syncTableFromModel();
        markRangeAdjustment(RangeAdjustment.CLAMP);
        requestFullRender();
    }

    /** Reverts rejected, uncommitted control text to the model state. */
    private void discardPendingEdits() {
        if (view.focusTable.isEditing()) {
            view.focusTable.getCellEditor().cancelCellEditing();
        }
        InputValidation.accept(view.familyParameter);
        InputValidation.accept(view.curveCount);
        view.setCurvePresentation(model.getCurveType(), model.getFamilyParameter());
        view.curveCount.setText(Integer.toString(model.getCurveCount()));
    }

    /** Validates without discarding invalid text; shared by actions and close. */
    boolean commitPendingEdits() {
        if (view.focusTable.isEditing()
                && !view.focusTable.getCellEditor().stopCellEditing()) {
            view.renderInfo.setText("Correct the highlighted focus value before continuing.");
            view.focusTable.getEditorComponent().requestFocusInWindow();
            return false;
        }
        if (!applyFamilyParameter()) {
            view.familyParameter.requestFocusInWindow();
            return false;
        }
        if (!applyCurveCount()) {
            view.curveCount.requestFocusInWindow();
            return false;
        }
        return true;
    }

    private boolean applyFamilyParameter() {
        if (suppressControls) {
            return true;
        }
        final CurveType type = model.getCurveType();
        if (!type.usesParameter()) {
            InputValidation.accept(view.familyParameter);
            view.setCurvePresentation(type, model.getFamilyParameter());
            return true;
        }
        try {
            final double parameter = type.parseParameter(view.familyParameter.getText());
            InputValidation.accept(view.familyParameter);
            if (Double.doubleToLongBits(parameter)
                    == Double.doubleToLongBits(model.getFamilyParameter())) {
                view.setCurvePresentation(type, parameter);
                return true;
            }
            model.setFamilyParameter(parameter);
            view.setCurvePresentation(type, parameter);
            refreshCursorField();
            markRangeAdjustment(RangeAdjustment.AUTO_FIT);
            requestFullRender(true);
            return true;
        } catch (final IllegalArgumentException exception) {
            InputValidation.reject(view.familyParameter, exception.getMessage());
            view.renderInfo.setText("Invalid parameter: " + exception.getMessage());
            return false;
        }
    }

    private boolean applyCurveCount() {
        if (suppressControls) {
            return true;
        }
        try {
            final int count = Integer.parseInt(view.curveCount.getText().trim());
            if (count < 1 || count > PlotConfig.MAX_CURVES) {
                throw new IllegalArgumentException("Count must be between 1 and "
                        + PlotConfig.MAX_CURVES);
            }
            if (count != model.getCurveCount()) {
                model.setCurveCount(count);
                requestFullRender();
            }
            InputValidation.accept(view.curveCount);
            view.curveCount.setText(Integer.toString(count));
            return true;
        } catch (final IllegalArgumentException exception) {
            final String message = "Enter an integer between 1 and " + PlotConfig.MAX_CURVES + ".";
            InputValidation.reject(view.curveCount, message);
            view.renderInfo.setText("Invalid curve count: " + message);
            return false;
        }
    }

    private void distanceSliderChanged(final boolean minimumChanged) {
        if (suppressSliders) {
            return;
        }
        final JSlider minimum = view.distanceMin;
        final JSlider maximum = view.distanceMax;
        final JSlider source = minimumChanged ? minimum : maximum;
        final double changedDistance = sliderToDistance(source.getValue());
        // The untouched bound is exact model state, not its quantized slider
        // position. Move it only when the changed bound actually crosses it;
        // comparing ticks alone misses crossings within the same tick.
        final double newMinimum = minimumChanged ? changedDistance
                : Math.min(model.getDistanceMin(), changedDistance);
        final double newMaximum = minimumChanged
                ? Math.max(model.getDistanceMax(), changedDistance) : changedDistance;
        model.setDistanceRange(newMinimum, newMaximum);
        pendingRangeAdjustment = RangeAdjustment.NONE;
        syncSlidersFromModel();
        if (source.getValueIsAdjusting()) {
            requestInteractiveRender();
        } else {
            requestFullRender();
        }
    }

    private void requestInteractiveRender() {
        // Invalidate a completed callback that may already be queued for Swing
        // before the debounce timers submit the replacement frame.
        renderService.cancel();
        view.canvas.setRendering(true);
        if (!previewTimer.isRunning()) {
            previewTimer.start();
        }
        fullTimer.restart();
    }

    private void requestFullRender() {
        requestFullRender(false);
    }

    private void requestFullRender(final boolean exactRequired) {
        previewTimer.stop();
        fullTimer.stop();
        submit(RenderQuality.FULL, exactRequired);
    }

    private void submit(final RenderQuality quality) {
        submit(quality, false);
    }

    private void submit(final RenderQuality quality, final boolean exactRequired) {
        final int width = view.canvas.getWidth();
        final int height = view.canvas.getHeight();
        if (width < 2 || height < 2 || !view.isDisplayable()) {
            return;
        }
        view.canvas.setRendering(true);
        RenderRequest request = new RenderRequest(model.snapshot(), width, height, quality);
        if (exactRequired) {
            request = request.requiringExact();
        }
        renderService.submitInteractive(request, this::renderCompleted, this::renderFailed);
    }

    private void renderCompleted(final RenderResult result) {
        final FieldExtrema extrema = result.extrema().orElse(null);
        sampledMin = extrema == null ? Double.NaN : extrema.minimum();
        sampledMax = extrema == null ? Double.NaN : extrema.maximum();
        final boolean trustedExtrema = trustsRangeExtrema(
                result.quality(), result.precisionLimited());
        sampledRangeApproximate = !trustedExtrema;

        final RangeResolution resolution = resolveRange(
                fullMin, fullMax, model.getDistanceMin(), model.getDistanceMax(),
                pendingRangeAdjustment, result.extrema(), trustedExtrema);
        fullMin = resolution.fullMin();
        fullMax = resolution.fullMax();
        if (!resolution.adjustmentDeferred()) {
            pendingRangeAdjustment = RangeAdjustment.NONE;
        }
        if (resolution.rangeChanged()) {
            model.setDistanceRange(resolution.levelMin(), resolution.levelMax());
        }

        syncSlidersFromModel();
        view.canvas.setRenderResult(result);
        view.renderInfo.setText(String.format(Locale.ROOT,
                "%s · %.1f ms · %d×%d · cache %s%s%s",
                result.quality() == RenderQuality.FULL ? "Full" : "Preview",
                result.renderNanos() / 1_000_000.0,
                result.image().getWidth(), result.image().getHeight(),
                renderer.cacheSummary(), extrema == null ? " · no finite samples" : "",
                result.precisionLimited()
                        ? resolution.adjustmentDeferred()
                                ? " · precision limited · resolving exact range"
                                : " · precision limited"
                        : ""));
        if (resolution.rangeChanged()) {
            requestFullRender();
        } else if (requiresExactRangeRetry(result.quality(), resolution)) {
            // Keep the limited frame responsive, but settle a pending fit or
            // clamp from exact extrema. Newer interaction can still cancel
            // this retry through the normal latest-wins scheduler.
            requestFullRender(true);
        }
    }

    private void renderFailed(final Throwable throwable) {
        final String message = throwable.getMessage() == null
                ? throwable.getClass().getSimpleName() : throwable.getMessage();
        view.canvas.setMessage("Rendering failed: " + message);
        view.renderInfo.setText("Rendering failed");
    }

    static RangeResolution resolveRange(final double previousFullMin,
            final double previousFullMax, final double oldMin, final double oldMax,
            final RangeAdjustment adjustment, final Optional<FieldExtrema> extrema,
            final boolean trustedExtrema) {
        if (!trustedExtrema) {
            return new RangeResolution(previousFullMin, previousFullMax, oldMin, oldMax,
                    false, adjustment != RangeAdjustment.NONE);
        }
        if (extrema.isEmpty()) {
            return new RangeResolution(previousFullMin, previousFullMax, oldMin, oldMax,
                    false, false);
        }

        double resolvedFullMin = extrema.orElseThrow().minimum();
        double resolvedFullMax = extrema.orElseThrow().maximum();
        if (resolvedFullMax <= resolvedFullMin) {
            final double centre = resolvedFullMin;
            final double padding = Math.max(1, Math.abs(centre) * 0.05);
            resolvedFullMin = centre - padding;
            resolvedFullMax = centre + padding;
            if (!Double.isFinite(resolvedFullMin) || !Double.isFinite(resolvedFullMax)) {
                if (centre >= 0) {
                    resolvedFullMin = Math.nextDown(centre);
                    resolvedFullMax = centre;
                } else {
                    resolvedFullMin = centre;
                    resolvedFullMax = Math.nextUp(centre);
                }
            }
        }

        double newMin = oldMin;
        double newMax = oldMax;
        if (adjustment != RangeAdjustment.NONE) {
            if (adjustment == RangeAdjustment.AUTO_FIT
                    || oldMax < resolvedFullMin || oldMin > resolvedFullMax || oldMin > oldMax) {
                newMin = ScalarRanges.interpolate(resolvedFullMin, resolvedFullMax, 0.05);
                newMax = ScalarRanges.interpolate(resolvedFullMin, resolvedFullMax, 0.95);
            } else {
                newMin = Math.max(resolvedFullMin, oldMin);
                newMax = Math.min(resolvedFullMax, oldMax);
                if (newMin > newMax) {
                    newMin = ScalarRanges.interpolate(resolvedFullMin, resolvedFullMax, 0.05);
                    newMax = ScalarRanges.interpolate(resolvedFullMin, resolvedFullMax, 0.95);
                }
            }
        }
        final boolean changed = !sameDouble(oldMin, newMin) || !sameDouble(oldMax, newMax);
        return new RangeResolution(resolvedFullMin, resolvedFullMax, newMin, newMax,
                changed, false);
    }

    static boolean trustsRangeExtrema(final RenderQuality quality,
            final boolean precisionLimited) {
        return quality == RenderQuality.FULL && !precisionLimited;
    }

    static boolean requiresExactRangeRetry(final RenderQuality quality,
            final RangeResolution resolution) {
        return quality == RenderQuality.FULL && resolution.adjustmentDeferred();
    }

    static boolean sameDouble(final double first, final double second) {
        // Configured levels are exact model state, not noisy measurements. Any
        // representable change can alter a contour and must be retained.
        return first == second;
    }

    static double initialFullMaximum(final double fullMinimum,
            final double levelMaximum) {
        final double unitExpanded = fullMinimum + 1;
        final double expanded = unitExpanded > fullMinimum
                ? unitExpanded : Math.nextUp(fullMinimum);
        return Math.max(expanded, levelMaximum);
    }

    private void resetRangeStateAfterLoad() {
        fullMin = Math.min(0, model.getDistanceMin());
        fullMax = initialFullMaximum(fullMin, model.getDistanceMax());
        sampledMin = Double.NaN;
        sampledMax = Double.NaN;
        sampledRangeApproximate = true;
        pendingRangeAdjustment = RangeAdjustment.NONE;
    }

    private void markRangeAdjustment(final RangeAdjustment adjustment) {
        if (adjustment == RangeAdjustment.AUTO_FIT
                || pendingRangeAdjustment == RangeAdjustment.NONE) {
            pendingRangeAdjustment = adjustment;
        }
    }

    /**
     * Pins the slider domain to {@code [minimum, maximum]} and resyncs both
     * sliders from the model, bypassing the sample-derived range resolution
     * a render would apply. This is the seam Swing tests use to drive slider
     * input against a known linear mapping; the range logic never calls it.
     */
    void pinSliderDomain(final double minimum, final double maximum) {
        if (!(minimum < maximum)) {
            throw new IllegalArgumentException("Slider domain requires minimum < maximum");
        }
        fullMin = minimum;
        fullMax = maximum;
        syncSlidersFromModel();
    }

    private void syncSlidersFromModel() {
        suppressSliders = true;
        view.distanceMin.setValue(distanceToSlider(model.getDistanceMin()));
        view.distanceMax.setValue(distanceToSlider(model.getDistanceMax()));
        suppressSliders = false;
        updateDistanceLabels();
    }

    private void updateDistanceLabels() {
        final String fieldLabel = sampledRangeApproximate ? "approx. field" : "field";
        view.distanceMinLabel.setText(String.format(Locale.ROOT,
                "Level min: %.5g   (%s min: %s)", model.getDistanceMin(), fieldLabel,
                formatFieldValue(sampledMin)));
        view.distanceMaxLabel.setText(String.format(Locale.ROOT,
                "Level max: %.5g   (%s max: %s)", model.getDistanceMax(), fieldLabel,
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
        view.focusTableModel.setValueAt(EditableNumbers.format(focus.x()), index, 0);
        view.focusTableModel.setValueAt(EditableNumbers.format(focus.y()), index, 1);
        view.focusTableModel.setValueAt(EditableNumbers.format(focus.weight()), index, 2);
        suppressTable = false;
    }

    private void refreshCursorField() {
        invalidateCursorInfo();
        final PlotSnapshot snapshot = model.snapshot();
        cursorField = DistanceFields.create(snapshot.curveType(), snapshot.foci(),
                snapshot.familyParameter());
    }

    private void invalidateCursorInfo() {
        cursorService.cancel();
        view.cursorInfo.setText("Move over plot for coordinates");
        view.cursorInfo.setToolTipText(null);
    }

    private void updateCursorInfo(final int pixelX, final int pixelY) {
        final PlotCanvas canvas = view.canvas;
        final int width = canvas.getWidth();
        final int height = canvas.getHeight();
        if (closed || width < 2 || height < 2 || cursorField == null) {
            return;
        }
        final Viewport viewport = model.getViewport();
        final DistanceField field = cursorField;
        final double x = viewport.worldX(pixelX, width);
        final double y = viewport.worldY(pixelY, height);
        view.cursorInfo.setText(String.format(Locale.ROOT, "(%.5g, %.5g)   f=…", x, y));
        view.cursorInfo.setToolTipText(null);
        cursorService.submit(field, x, y, value -> {
            if (cursorRequestMatches(field, viewport, width, height)) {
                view.cursorInfo.setText(String.format(Locale.ROOT,
                        "(%.5g, %.5g)   f=%.6g", x, y, value));
            }
        }, failure -> {
            if (cursorRequestMatches(field, viewport, width, height)) {
                view.cursorInfo.setText(String.format(Locale.ROOT,
                        "(%.5g, %.5g)   f=unavailable", x, y));
                view.cursorInfo.setToolTipText(failure.getMessage());
            }
        });
    }

    private boolean cursorRequestMatches(final DistanceField field, final Viewport viewport,
            final int width, final int height) {
        return !closed && field == cursorField && viewport.equals(model.getViewport())
                && width == view.canvas.getWidth() && height == view.canvas.getHeight();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        previewTimer.stop();
        fullTimer.stop();
        cursorService.close();
        renderService.close();
    }
}
