package Main;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import Main.PlotConfig.CurveType;
import Main.PlotConfig.FocusSpec;
import plotPane.NCassin;
import plotPane.NHyperb;
import plotPane.NLipse;
import plotPane.PlotDistanceCurve;
import plotPane.PlotDistanceCurve.Extrema;
import plotPane.PlotPane;
import simpleGeom.Point;

public final class PlotWindow {
	private static final double DPI_SCALE = getDpiScale();
	private static final double HIT_PIXELS = 10 * DPI_SCALE;
	private static final int SIDE_WIDTH = (int) (290 * DPI_SCALE);
	private static final int SLIDER_TICKS = 1000;
	private static final double ZOOM_STEP = 0.85;
	private static final double NUDGE_STEP = 0.1;
	private static final double NUDGE_FINE = 0.01;

	private final PlotConfig config;
	private final JFrame frame;
	private final PlotPane plotPane;
	private final DefaultTableModel fociTableModel;
	private final JTable fociTable;
	private final JComboBox<CurveType> cmbType;
	private final JSlider sliderDmin;
	private final JSlider sliderDmax;
	private final JLabel lblDmin;
	private final JLabel lblDmax;
	private final JLabel lblCursor;
	private final JTextField txtNCurves;
	private final JCheckBox chkLog;
	private int draggingIndex;
	private int selectedFocusIndex;
	private double fullMin;
	private double fullMax;
	private boolean suppressSliderEvents;
	private boolean suppressTableEvents;
	private PlotDistanceCurve probeCurve;

	private boolean panning;
	private int panStartPixelX;
	private int panStartPixelY;
	private double panStartXmin;
	private double panStartXmax;
	private double panStartYmin;
	private double panStartYmax;

	public PlotWindow(final PlotConfig config) {
		if (config == null)
			throw new IllegalArgumentException("Plot configuration must not be null");
		if (config.foci == null || config.foci.isEmpty())
			throw new IllegalArgumentException("At least one focus point is required");
		this.config = config;
		draggingIndex = -1;
		selectedFocusIndex = -1;

		fociTableModel = new DefaultTableModel(new String[]{"X", "Y", "Weight"}, 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public Class<?> getColumnClass(final int columnIndex) {
				return String.class;
			}
		};
		fociTable = new JTable(fociTableModel);
		fociTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		fociTable.setRowHeight(22);
		final DefaultCellEditor cellEditor = new DefaultCellEditor(new JTextField());
		cellEditor.setClickCountToStart(2);
		fociTable.setDefaultEditor(Object.class, cellEditor);

		cmbType = new JComboBox<>(CurveType.values());
		cmbType.setSelectedItem(config.curveType);
		sliderDmin = new JSlider(0, SLIDER_TICKS, 50);
		sliderDmax = new JSlider(0, SLIDER_TICKS, 950);
		lblDmin = new JLabel();
		lblDmax = new JLabel();
		lblCursor = new JLabel("(hover plot for value)");
		txtNCurves = new JTextField(String.valueOf(config.nCurves), 4);
		chkLog = new JCheckBox("Log spacing", config.logSpacing);

		frame = new JFrame(Main.PROGRAM_NAME + " " + Main.VERSION);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLayout(new BorderLayout());

		final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		frame.setMinimumSize(new Dimension(600, 400));
		final int plotSize = Math.max(300, Math.min(screenSize.width, screenSize.height) - 100);

		plotPane = new PlotPane(config.xmin, config.xmax, config.ymin, config.ymax, plotSize, plotSize);
		plotPane.setAntiAlias(config.antiAlias);

		frame.add(plotPane, BorderLayout.CENTER);
		frame.add(buildSidePanel(), BorderLayout.EAST);
		frame.setExtendedState(Frame.MAXIMIZED_BOTH);
		frame.setVisible(true);
		frame.toFront();

		installMouseHandler();
		installKeyboardShortcuts();
		installTableListeners();

		computeFullRange();
		if (config.dmin < fullMin || config.dmax > fullMax || config.dmin > config.dmax)
			autoFitDistRange();
		syncSliders();
		rebuildAndSyncTable();
	}

	private static double getDpiScale() {
		if (GraphicsEnvironment.isHeadless())
			return 1;
		return Math.max(1.0, Toolkit.getDefaultToolkit().getScreenResolution() / 96.0);
	}

	private JPanel buildSidePanel() {
		final JPanel side = new JPanel();
		side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
		side.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		side.setPreferredSize(new Dimension(SIDE_WIDTH, 0));

		final JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		typePanel.setBorder(BorderFactory.createTitledBorder("Curve type"));
		typePanel.add(cmbType);
		cmbType.addActionListener(event -> {
			final CurveType selectedType = (CurveType) cmbType.getSelectedItem();
			if (selectedType == null)
				return;
			config.curveType = selectedType;
			final boolean shouldLog = config.curveType == CurveType.CASSIN;
			if (config.logSpacing != shouldLog) {
				config.logSpacing = shouldLog;
				chkLog.setSelected(shouldLog);
			}
			computeFullRange();
			autoFitDistRange();
			syncSliders();
			rebuild();
		});
		side.add(typePanel);
		side.add(Box.createVerticalStrut(6));

		final JPanel distancePanel = new JPanel();
		distancePanel.setLayout(new BoxLayout(distancePanel, BoxLayout.Y_AXIS));
		distancePanel.setBorder(BorderFactory.createTitledBorder("Distance range"));
		distancePanel.add(lblDmin);
		distancePanel.add(sliderDmin);
		distancePanel.add(lblDmax);
		distancePanel.add(sliderDmax);

		final JPanel countRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		countRow.add(new JLabel("Count:"));
		countRow.add(txtNCurves);
		countRow.add(chkLog);
		distancePanel.add(countRow);

		final JPanel viewButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		final JButton fitButton = new JButton("Fit dist");
		final JButton resetButton = new JButton("Reset view");
		fitButton.addActionListener(event -> {
			computeFullRange();
			autoFitDistRange();
			syncSliders();
			rebuild();
		});
		resetButton.addActionListener(event -> {
			plotPane.setDim(config.xmin, config.xmax, config.ymin, config.ymax);
			computeFullRange();
			clampDistToFullRange();
			syncSliders();
			rebuild();
		});
		viewButtons.add(fitButton);
		viewButtons.add(resetButton);
		distancePanel.add(viewButtons);

		sliderDmin.addChangeListener(event -> onSliderChange(true));
		sliderDmax.addChangeListener(event -> onSliderChange(false));
		txtNCurves.addActionListener(event -> applyNCurves());
		txtNCurves.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(final FocusEvent event) {
				applyNCurves();
			}
		});
		chkLog.addActionListener(event -> {
			config.logSpacing = chkLog.isSelected();
			rebuild();
		});

		side.add(distancePanel);
		side.add(Box.createVerticalStrut(10));

		final JPanel cursorPanel = new JPanel(new BorderLayout());
		cursorPanel.setBorder(BorderFactory.createTitledBorder("Cursor info"));
		cursorPanel.add(lblCursor, BorderLayout.CENTER);
		side.add(cursorPanel);
		side.add(Box.createVerticalStrut(10));

		final JPanel fociPanel = new JPanel(new BorderLayout());
		fociPanel.setBorder(BorderFactory.createTitledBorder("Focus points (click to select; edit cells)"));
		fociPanel.add(new JScrollPane(fociTable), BorderLayout.CENTER);
		final JPanel fociButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		final JButton addButton = new JButton("Add");
		final JButton removeButton = new JButton("Remove");
		addButton.addActionListener(event -> {
			config.foci.add(new FocusSpec(0, 0, 1));
			selectedFocusIndex = config.foci.size() - 1;
			refreshAfterGeometryChange(true);
		});
		removeButton.addActionListener(event -> {
			final int row = fociTable.getSelectedRow();
			if (row >= 0)
				removeFocusAt(row);
		});
		fociButtons.add(addButton);
		fociButtons.add(removeButton);
		fociPanel.add(fociButtons, BorderLayout.SOUTH);
		side.add(fociPanel);
		side.add(Box.createVerticalStrut(10));

		final JPanel help = new JPanel();
		help.setLayout(new BoxLayout(help, BoxLayout.Y_AXIS));
		help.setBorder(BorderFactory.createTitledBorder("Controls"));
		help.add(new JLabel("Left-click empty: add focus"));
		help.add(new JLabel("Left-drag focus: move it"));
		help.add(new JLabel("Right-click focus: remove"));
		help.add(new JLabel("Middle-drag: pan"));
		help.add(new JLabel("Mouse wheel: zoom"));
		help.add(new JLabel("Click plot then arrows: nudge"));
		help.add(new JLabel("(Shift+arrows = fine 0.01)"));
		help.add(new JLabel("Delete: remove selected"));
		side.add(help);

		side.add(Box.createVerticalGlue());
		return side;
	}

	private void installTableListeners() {
		fociTableModel.addTableModelListener(event -> {
			if (suppressTableEvents || event.getType() != TableModelEvent.UPDATE)
				return;
			final int row = event.getFirstRow();
			final int column = event.getColumn();
			if (row < 0 || row >= config.foci.size() || column < 0 || column > 2)
				return;
			try {
				final double value = parseFiniteTableValue(fociTableModel.getValueAt(row, column));
				final FocusSpec focus = config.foci.get(row);
				if (column == 0)
					focus.x = value;
				else if (column == 1)
					focus.y = value;
				else
					focus.weight = value;
				refreshAfterGeometryChange(false);
			} catch (final NumberFormatException exception) {
				syncTableFromConfig();
			}
		});
		fociTable.getSelectionModel().addListSelectionListener(event -> {
			if (suppressTableEvents || event.getValueIsAdjusting())
				return;
			selectedFocusIndex = fociTable.getSelectedRow();
			rebuild();
		});
	}

	private static double parseFiniteTableValue(final Object value) {
		final double parsed = Double.parseDouble(String.valueOf(value).trim());
		if (!Double.isFinite(parsed))
			throw new NumberFormatException("Value must be finite");
		return parsed;
	}

	private void onSliderChange(final boolean isDmin) {
		if (suppressSliderEvents)
			return;
		if (isDmin && sliderDmin.getValue() > sliderDmax.getValue()) {
			suppressSliderEvents = true;
			sliderDmax.setValue(sliderDmin.getValue());
			suppressSliderEvents = false;
		} else if (!isDmin && sliderDmax.getValue() < sliderDmin.getValue()) {
			suppressSliderEvents = true;
			sliderDmin.setValue(sliderDmax.getValue());
			suppressSliderEvents = false;
		}
		config.dmin = sliderToDist(sliderDmin.getValue());
		config.dmax = sliderToDist(sliderDmax.getValue());
		updateDistLabels();
		final JSlider source = isDmin ? sliderDmin : sliderDmax;
		if (!source.getValueIsAdjusting())
			rebuild();
	}

	private void applyNCurves() {
		try {
			final int value = Integer.parseInt(txtNCurves.getText().trim());
			if (value >= 1 && value <= 200 && value != config.nCurves) {
				config.nCurves = value;
				rebuild();
				return;
			}
		} catch (final NumberFormatException ignored) {
		}
		txtNCurves.setText(String.valueOf(config.nCurves));
	}

	private void computeFullRange() {
		if (config.foci.isEmpty()) {
			fullMin = 0;
			fullMax = 1;
			return;
		}
		final Extrema extrema = makeProbeCurve().getExtrema(plotPane);
		fullMin = extrema.getMinValue();
		fullMax = extrema.getMaxValue();
		if (!Double.isFinite(fullMin) || !Double.isFinite(fullMax) || fullMax <= fullMin) {
			fullMin = Double.isFinite(fullMin) ? fullMin : 0;
			fullMax = fullMin + 1;
		}
	}

	private void autoFitDistRange() {
		final double range = fullMax - fullMin;
		config.dmin = fullMin + range * 0.05;
		config.dmax = fullMin + range * 0.95;
	}

	private void clampDistToFullRange() {
		if (config.dmax < fullMin || config.dmin > fullMax) {
			autoFitDistRange();
			return;
		}
		config.dmin = Math.max(fullMin, config.dmin);
		config.dmax = Math.min(fullMax, config.dmax);
		if (config.dmin > config.dmax)
			autoFitDistRange();
	}

	private void syncSliders() {
		suppressSliderEvents = true;
		sliderDmin.setValue(distToSlider(config.dmin));
		sliderDmax.setValue(distToSlider(config.dmax));
		suppressSliderEvents = false;
		updateDistLabels();
	}

	private void updateDistLabels() {
		lblDmin.setText(String.format(Locale.ROOT, "Dmin: %.3f  (full min: %.3f)", config.dmin, fullMin));
		lblDmax.setText(String.format(Locale.ROOT, "Dmax: %.3f  (full max: %.3f)", config.dmax, fullMax));
	}

	private double sliderToDist(final int value) {
		return fullMin + (fullMax - fullMin) * value / (double) SLIDER_TICKS;
	}

	private int distToSlider(final double distance) {
		if (fullMax == fullMin)
			return SLIDER_TICKS / 2;
		final int value = (int) Math.round(SLIDER_TICKS * (distance - fullMin) / (fullMax - fullMin));
		return Math.max(0, Math.min(SLIDER_TICKS, value));
	}

	private void installMouseHandler() {
		final JPanel drawPanel = plotPane.getDrawPanel();
		drawPanel.setFocusable(true);
		final MouseAdapter mouseAdapter = new MouseAdapter() {
			@Override
			public void mousePressed(final MouseEvent event) {
				drawPanel.requestFocusInWindow();
				if (SwingUtilities.isMiddleMouseButton(event)) {
					panStartPixelX = event.getX();
					panStartPixelY = event.getY();
					panStartXmin = plotPane.getXmin();
					panStartXmax = plotPane.getXmax();
					panStartYmin = plotPane.getYmin();
					panStartYmax = plotPane.getYmax();
					panning = true;
					return;
				}

				final int index = hitTest(event.getX(), event.getY());
				if (SwingUtilities.isRightMouseButton(event)) {
					if (index >= 0)
						removeFocusAt(index);
				} else if (SwingUtilities.isLeftMouseButton(event)) {
					if (index >= 0) {
						draggingIndex = index;
						selectedFocusIndex = index;
						suppressTableEvents = true;
						fociTable.setRowSelectionInterval(index, index);
						suppressTableEvents = false;
						rebuild();
					} else {
						final double worldX = plotPane.unfitx(event.getX());
						final double worldY = plotPane.unfity(event.getY());
						config.foci.add(new FocusSpec(worldX, worldY, 1));
						selectedFocusIndex = config.foci.size() - 1;
						draggingIndex = selectedFocusIndex;
						refreshAfterGeometryChange(true);
					}
				}
			}

			@Override
			public void mouseReleased(final MouseEvent event) {
				if (panning) {
					panning = false;
					refreshAfterGeometryChange(false);
					return;
				}
				if (draggingIndex >= 0) {
					draggingIndex = -1;
					refreshAfterGeometryChange(true);
				}
			}
		};
		drawPanel.addMouseListener(mouseAdapter);
		drawPanel.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(final MouseEvent event) {
				updateCursorInfo(event.getX(), event.getY());
				if (panning) {
					applyPan(event);
					return;
				}
				if (draggingIndex < 0 || draggingIndex >= config.foci.size())
					return;
				final FocusSpec focus = config.foci.get(draggingIndex);
				focus.x = plotPane.unfitx(event.getX());
				focus.y = plotPane.unfity(event.getY());
				rebuild();
			}

			@Override
			public void mouseMoved(final MouseEvent event) {
				updateCursorInfo(event.getX(), event.getY());
			}
		});
		final MouseWheelListener wheelListener = this::applyZoom;
		drawPanel.addMouseWheelListener(wheelListener);
	}

	private void installKeyboardShortcuts() {
		final JRootPane rootPane = frame.getRootPane();
		final InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		final ActionMap actionMap = rootPane.getActionMap();

		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteFocus");
		actionMap.put("deleteFocus", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(final ActionEvent event) {
				deleteSelectedFocus();
			}
		});

		bindNudge(inputMap, actionMap, KeyEvent.VK_LEFT, 0, -NUDGE_STEP, 0);
		bindNudge(inputMap, actionMap, KeyEvent.VK_RIGHT, 0, NUDGE_STEP, 0);
		bindNudge(inputMap, actionMap, KeyEvent.VK_UP, 0, 0, NUDGE_STEP);
		bindNudge(inputMap, actionMap, KeyEvent.VK_DOWN, 0, 0, -NUDGE_STEP);
		bindNudge(inputMap, actionMap, KeyEvent.VK_LEFT, KeyEvent.SHIFT_DOWN_MASK, -NUDGE_FINE, 0);
		bindNudge(inputMap, actionMap, KeyEvent.VK_RIGHT, KeyEvent.SHIFT_DOWN_MASK, NUDGE_FINE, 0);
		bindNudge(inputMap, actionMap, KeyEvent.VK_UP, KeyEvent.SHIFT_DOWN_MASK, 0, NUDGE_FINE);
		bindNudge(inputMap, actionMap, KeyEvent.VK_DOWN, KeyEvent.SHIFT_DOWN_MASK, 0, -NUDGE_FINE);
	}

	private void bindNudge(final InputMap inputMap, final ActionMap actionMap, final int keyCode, final int modifiers,
			final double dx, final double dy) {
		final String actionName = "nudge_" + keyCode + "_" + modifiers;
		inputMap.put(KeyStroke.getKeyStroke(keyCode, modifiers), actionName);
		actionMap.put(actionName, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(final ActionEvent event) {
				nudge(dx, dy);
			}
		});
	}

	private void deleteSelectedFocus() {
		removeFocusAt(selectedFocusIndex);
	}

	private void removeFocusAt(final int index) {
		if (index < 0 || index >= config.foci.size() || config.foci.size() <= 1)
			return;

		final int previousSelectedIndex = selectedFocusIndex;
		config.foci.remove(index);
		if (previousSelectedIndex > index)
			selectedFocusIndex = previousSelectedIndex - 1;
		else if (previousSelectedIndex == index)
			selectedFocusIndex = Math.min(index, config.foci.size() - 1);

		if (draggingIndex > index)
			draggingIndex--;
		else if (draggingIndex == index)
			draggingIndex = -1;

		refreshAfterGeometryChange(true);
	}

	private void nudge(final double dx, final double dy) {
		if (selectedFocusIndex < 0 || selectedFocusIndex >= config.foci.size())
			return;
		final FocusSpec focus = config.foci.get(selectedFocusIndex);
		focus.x += dx;
		focus.y += dy;
		refreshAfterGeometryChange(true);
	}

	private void updateCursorInfo(final int pixelX, final int pixelY) {
		final double worldX = plotPane.unfitx(pixelX);
		final double worldY = plotPane.unfity(pixelY);
		if (probeCurve == null) {
			lblCursor.setText(String.format(Locale.ROOT, "(%.3f, %.3f)", worldX, worldY));
		} else {
			final double value = probeCurve.getCumultDistance(worldX, worldY);
			lblCursor.setText(String.format(Locale.ROOT, "(%.3f, %.3f)  f=%.4g", worldX, worldY, value));
		}
	}

	private void applyPan(final MouseEvent event) {
		final int dxPixels = event.getX() - panStartPixelX;
		final int dyPixels = event.getY() - panStartPixelY;
		final double xRange = panStartXmax - panStartXmin;
		final double yRange = panStartYmax - panStartYmin;
		final double dxWorld = dxPixels * xRange / (plotPane.getXres() - 1.0);
		final double dyWorld = dyPixels * yRange / (plotPane.getYres() - 1.0);
		plotPane.setDim(panStartXmin - dxWorld, panStartXmax - dxWorld,
				panStartYmin + dyWorld, panStartYmax + dyWorld);
	}

	private void applyZoom(final MouseWheelEvent event) {
		final double centerX = plotPane.unfitx(event.getX());
		final double centerY = plotPane.unfity(event.getY());
		final double scale = Math.pow(ZOOM_STEP, -event.getPreciseWheelRotation());
		plotPane.setDim(centerX + (plotPane.getXmin() - centerX) * scale,
				centerX + (plotPane.getXmax() - centerX) * scale,
				centerY + (plotPane.getYmin() - centerY) * scale,
				centerY + (plotPane.getYmax() - centerY) * scale);
		refreshAfterGeometryChange(false);
	}

	private int hitTest(final int pixelX, final int pixelY) {
		int bestIndex = -1;
		double bestDistanceSquared = HIT_PIXELS * HIT_PIXELS;
		for (int i = 0; i < config.foci.size(); i++) {
			final FocusSpec focus = config.foci.get(i);
			final double dx = plotPane.fitx(focus.x) - pixelX;
			final double dy = plotPane.fity(focus.y) - pixelY;
			final double distanceSquared = dx * dx + dy * dy;
			if (distanceSquared < bestDistanceSquared) {
				bestDistanceSquared = distanceSquared;
				bestIndex = i;
			}
		}
		return bestIndex;
	}

	private PlotDistanceCurve makeProbeCurve() {
		final Point[] points = new Point[config.foci.size()];
		final double[] weights = new double[config.foci.size()];
		for (int i = 0; i < points.length; i++) {
			final FocusSpec focus = config.foci.get(i);
			points[i] = new Point(focus.x, focus.y);
			weights[i] = focus.weight;
		}
		return makeCurve(points, weights, 0);
	}

	private void refreshAfterGeometryChange(final boolean syncTable) {
		computeFullRange();
		clampDistToFullRange();
		syncSliders();
		if (syncTable)
			rebuildAndSyncTable();
		else
			rebuild();
	}

	private void rebuildAndSyncTable() {
		rebuild();
		syncTableFromConfig();
	}

	private void rebuild() {
		plotPane.clearPlots();
		plotPane.clearPoints();

		final Point[] points = new Point[config.foci.size()];
		final double[] weights = new double[config.foci.size()];
		for (int i = 0; i < points.length; i++) {
			final FocusSpec focus = config.foci.get(i);
			points[i] = new Point(focus.x, focus.y);
			weights[i] = focus.weight;
			final Color color = i == selectedFocusIndex ? Color.ORANGE : Color.BLUE;
			plotPane.addPoint(points[i], color);
		}

		probeCurve = config.foci.isEmpty() ? null : makeCurve(points, weights, 0);

		final int curveCount = Math.max(1, config.nCurves);
		final boolean useLog = config.logSpacing && config.dmin > 0 && config.dmax > 0;
		final double logMin = useLog ? Math.log(config.dmin) : 0;
		final double logMax = useLog ? Math.log(config.dmax) : 0;
		final PlotDistanceCurve[] curves = new PlotDistanceCurve[curveCount];
		for (int i = 0; i < curveCount; i++) {
			final double distance;
			if (curveCount == 1)
				distance = config.dmin;
			else if (useLog)
				distance = Math.exp(logMin + (logMax - logMin) * i / (curveCount - 1));
			else
				distance = config.dmin + (config.dmax - config.dmin) * i / (curveCount - 1);
			curves[i] = makeCurve(points, weights, distance);
		}

		if (config.showBackground && curves.length > 0)
			curves[0].setBkgrdOn(true);

		if (config.showMinMax && curves.length > 0 && draggingIndex < 0 && !panning) {
			final Extrema extrema = curves[0].getExtrema(plotPane);
			if (config.curveType != CurveType.CASSIN)
				plotPane.addPoint(extrema.getMinPoint(), Color.RED);
			plotPane.addPoint(extrema.getMaxPoint(), Color.CYAN);
		}

		for (int i = 0; i < curves.length; i++)
			plotPane.addPlot(curves[i], curveColor(i, curves.length));
		plotPane.refresh();
	}

	private void syncTableFromConfig() {
		suppressTableEvents = true;
		fociTableModel.setRowCount(0);
		for (final FocusSpec focus : config.foci)
			fociTableModel.addRow(new Object[]{formatTableNumber(focus.x), formatTableNumber(focus.y),
					formatTableNumber(focus.weight)});
		if (selectedFocusIndex >= 0 && selectedFocusIndex < fociTableModel.getRowCount())
			fociTable.setRowSelectionInterval(selectedFocusIndex, selectedFocusIndex);
		else
			fociTable.clearSelection();
		suppressTableEvents = false;
	}

	private static String formatTableNumber(final double value) {
		if (value == Math.floor(value) && !Double.isInfinite(value) && Math.abs(value) < 1e15)
			return String.valueOf((long) value);
		return String.format(Locale.ROOT, "%.4f", value);
	}

	private static Color curveColor(final int index, final int count) {
		if (count <= 1)
			return Color.BLACK;
		final float hue = (float) (0.66 * (1.0 - (double) index / (count - 1)));
		return Color.getHSBColor(hue, 0.85f, 0.7f);
	}

	private PlotDistanceCurve makeCurve(final Point[] points, final double[] weights, final double distance) {
		switch (config.curveType) {
			case CASSIN:
				return new NCassin(points, distance, weights);
			case HYPERB:
				return new NHyperb(points, distance, weights);
			case LIPSE:
			default:
				return new NLipse(points, distance, weights);
		}
	}
}
