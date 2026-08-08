package Main;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
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
import plotPane.PlotPane;
import simpleGeom.Point;

public class PlotWindow {
	private final static double		DPI_SCALE		= Math.max(1.0, Toolkit.getDefaultToolkit().getScreenResolution() / 96.0);
	private final static double		HIT_PIXELS		= 10 * DPI_SCALE;
	private final static int		SIDE_WIDTH		= (int) (290 * DPI_SCALE);
	private final static int		SLIDER_TICKS	= 1000;
	private final static double		ZOOM_STEP		= 0.85;
	private final static double		NUDGE_STEP		= 0.1;
	private final static double		NUDGE_FINE		= 0.01;

	private final PlotConfig		config;
	private final JFrame			frame;
	private final PlotPane			pl;
	private final DefaultTableModel	fociTableModel;
	private final JTable			fociTable;
	private final JComboBox<String>	cmbType;
	private final JSlider			sliderDmin, sliderDmax;
	private final JLabel			lblDmin, lblDmax;
	private final JLabel			lblCursor;
	private final JTextField		txtNCurves;
	private final JCheckBox			chkLog;
	private int						draggingIndex;
	private int						selectedFocusIndex;
	private double					fullMin, fullMax;
	private boolean					suppressSliderEvents;
	private boolean					suppressTableEvents;
	private PlotDistanceCurve		probeCurve;

	private boolean					panning;
	private int						panStartPixelX, panStartPixelY;
	private double					panStartXmin, panStartXmax, panStartYmin, panStartYmax;

	public PlotWindow(final PlotConfig config) {
		this.config = config;
		draggingIndex = -1;
		selectedFocusIndex = -1;

		fociTableModel = new DefaultTableModel(new String[]{"X", "Y", "Weight" }, 0) {
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

		cmbType = new JComboBox<>(new String[]{"n-Ellipse (sum)", "Cassini oval (product)", "n-Hyperbola (avg diff)" });
		cmbType.setSelectedIndex(config.curveType.ordinal());
		sliderDmin = new JSlider(0, SLIDER_TICKS, 50);
		sliderDmax = new JSlider(0, SLIDER_TICKS, 950);
		lblDmin = new JLabel();
		lblDmax = new JLabel();
		lblCursor = new JLabel("(hover plot for value)");
		txtNCurves = new JTextField(String.valueOf(config.nCurves), 4);
		chkLog = new JCheckBox("Log spacing", config.logSpacing);

		frame = new JFrame("nLipse " + Main.version);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLayout(new BorderLayout());

		final Dimension screenRes = Toolkit.getDefaultToolkit().getScreenSize();
		frame.setMinimumSize(new Dimension(600, 400));
		final int plotSize = Math.max(300, Math.min(screenRes.width, screenRes.height) - 100);

		pl = new PlotPane(config.xmin, config.xmax, config.ymin, config.ymax, plotSize, plotSize);
		pl.setAntiAlias(config.antiAlias);

		frame.add(pl, BorderLayout.CENTER);
		frame.add(buildSidePanel(), BorderLayout.EAST);
		frame.setExtendedState(Frame.MAXIMIZED_BOTH);
		frame.setVisible(true);
		frame.toFront();

		installMouseHandler();
		installKeyboardShortcuts();
		installTableListeners();

		// Apply the Cassini log-spacing default on startup, not only on type switch
		final boolean shouldLog = config.curveType == CurveType.CASSIN;
		if (config.logSpacing != shouldLog) {
			config.logSpacing = shouldLog;
			chkLog.setSelected(shouldLog);
		}
		computeFullRange();
		if (config.dmin < fullMin || config.dmax > fullMax || config.dmin >= config.dmax)
			autoFitDistRange();
		syncSliders();
		rebuildAndSyncTable();
	}

	private JPanel buildSidePanel() {
		final JPanel side = new JPanel();
		side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
		side.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		side.setPreferredSize(new Dimension(SIDE_WIDTH, 0));

		final JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		typePanel.setBorder(BorderFactory.createTitledBorder("Curve type"));
		typePanel.add(cmbType);
		cmbType.addActionListener(e -> {
			config.curveType = CurveType.values()[cmbType.getSelectedIndex()];
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

		final JPanel distPanel = new JPanel();
		distPanel.setLayout(new BoxLayout(distPanel, BoxLayout.Y_AXIS));
		distPanel.setBorder(BorderFactory.createTitledBorder("Distance range"));
		distPanel.add(lblDmin);
		distPanel.add(sliderDmin);
		distPanel.add(lblDmax);
		distPanel.add(sliderDmax);

		final JPanel countRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		countRow.add(new JLabel("Count:"));
		countRow.add(txtNCurves);
		countRow.add(chkLog);
		distPanel.add(countRow);

		final JPanel viewBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		final JButton btnFit = new JButton("Fit dist");
		final JButton btnReset = new JButton("Reset view");
		btnFit.addActionListener(e -> {
			computeFullRange();
			autoFitDistRange();
			syncSliders();
			rebuild();
		});
		btnReset.addActionListener(e -> {
			pl.setDim(config.xmin, config.xmax, config.ymin, config.ymax);
			computeFullRange();
			syncSliders();
			rebuild();
		});
		viewBtns.add(btnFit);
		viewBtns.add(btnReset);
		distPanel.add(viewBtns);

		sliderDmin.addChangeListener(e -> onSliderChange(true));
		sliderDmax.addChangeListener(e -> onSliderChange(false));
		txtNCurves.addActionListener(e -> applyNCurves());
		txtNCurves.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(final FocusEvent e) {
				applyNCurves();
			}
		});
		chkLog.addActionListener(e -> {
			config.logSpacing = chkLog.isSelected();
			rebuild();
		});

		side.add(distPanel);
		side.add(Box.createVerticalStrut(10));

		final JPanel cursorPanel = new JPanel(new BorderLayout());
		cursorPanel.setBorder(BorderFactory.createTitledBorder("Cursor info"));
		cursorPanel.add(lblCursor, BorderLayout.CENTER);
		side.add(cursorPanel);
		side.add(Box.createVerticalStrut(10));

		final JPanel fociPanel = new JPanel(new BorderLayout());
		fociPanel.setBorder(BorderFactory.createTitledBorder("Focus points (click to select; edit cells)"));
		fociPanel.add(new JScrollPane(fociTable), BorderLayout.CENTER);
		final JPanel fociBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		final JButton btnAdd = new JButton("Add");
		final JButton btnRemove = new JButton("Remove");
		btnAdd.addActionListener(e -> {
			config.foci.add(new FocusSpec(0, 0, 1));
			selectedFocusIndex = config.foci.size() - 1;
			rebuildAndSyncTable();
		});
		btnRemove.addActionListener(e -> {
			final int row = fociTable.getSelectedRow();
			if (row >= 0)
				removeFocusAt(row);
		});
		fociBtns.add(btnAdd);
		fociBtns.add(btnRemove);
		fociPanel.add(fociBtns, BorderLayout.SOUTH);
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
		fociTableModel.addTableModelListener(e -> {
			if (suppressTableEvents)
				return;
			if (e.getType() != TableModelEvent.UPDATE)
				return;
			final int row = e.getFirstRow();
			final int col = e.getColumn();
			if (row < 0 || row >= config.foci.size() || col < 0)
				return;
			try {
				final double v = Double.parseDouble(String.valueOf(fociTableModel.getValueAt(row, col)).trim());
				final FocusSpec f = config.foci.get(row);
				if (col == 0)
					f.x = v;
				else if (col == 1)
					f.y = v;
				else if (col == 2)
					f.weight = v;
				rebuild();
			} catch (final NumberFormatException ex) {
				syncTableFromConfig();
			}
		});
		fociTable.getSelectionModel().addListSelectionListener(e -> {
			if (suppressTableEvents || e.getValueIsAdjusting())
				return;
			selectedFocusIndex = fociTable.getSelectedRow();
			rebuild();
		});
	}

	private void onSliderChange(final boolean isDmin) {
		if (suppressSliderEvents)
			return;
		// Push the OTHER slider along instead of pinning the active one (smoother drag).
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
		final JSlider src = isDmin ? sliderDmin : sliderDmax;
		if (!src.getValueIsAdjusting())
			rebuild();
	}

	private void applyNCurves() {
		try {
			final int v = Integer.parseInt(txtNCurves.getText().trim());
			if (v >= 1 && v <= 200 && v != config.nCurves) {
				config.nCurves = v;
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
		final PlotDistanceCurve probe = makeProbeCurve();
		final Point pMin = probe.getMinPoint(pl);
		final Point pMax = probe.getMaxPoint(pl);
		fullMin = probe.getCumultDistance(pMin);
		fullMax = probe.getCumultDistance(pMax);
		if (fullMax <= fullMin)
			fullMax = fullMin + 1;
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
		if (config.dmin >= config.dmax)
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
		lblDmin.setText(String.format("Dmin: %.3f  (full min: %.3f)", config.dmin, fullMin));
		lblDmax.setText(String.format("Dmax: %.3f  (full max: %.3f)", config.dmax, fullMax));
	}

	private double sliderToDist(final int v) {
		return fullMin + (fullMax - fullMin) * v / (double) SLIDER_TICKS;
	}

	private int distToSlider(final double d) {
		if (fullMax == fullMin)
			return SLIDER_TICKS / 2;
		final int v = (int) Math.round(SLIDER_TICKS * (d - fullMin) / (fullMax - fullMin));
		return Math.max(0, Math.min(SLIDER_TICKS, v));
	}

	private void installMouseHandler() {
		final JPanel draw = pl.getDrawPanel();
		draw.setFocusable(true);
		final MouseAdapter press = new MouseAdapter() {
			@Override
			public void mousePressed(final MouseEvent e) {
				draw.requestFocusInWindow();
				if (SwingUtilities.isMiddleMouseButton(e)) {
					panStartPixelX = e.getX();
					panStartPixelY = e.getY();
					panStartXmin = pl.getXmin();
					panStartXmax = pl.getXmax();
					panStartYmin = pl.getYmin();
					panStartYmax = pl.getYmax();
					panning = true;
					return;
				}
				final int idx = hitTest(e.getX(), e.getY());
				if (SwingUtilities.isRightMouseButton(e)) {
					if (idx >= 0)
						removeFocusAt(idx);
				} else if (SwingUtilities.isLeftMouseButton(e)) {
					if (idx >= 0) {
						draggingIndex = idx;
						selectedFocusIndex = idx;
						suppressTableEvents = true;
						fociTable.setRowSelectionInterval(idx, idx);
						suppressTableEvents = false;
						rebuild();
					} else {
						final double wx = pl.unfitx(e.getX());
						final double wy = pl.unfity(e.getY());
						config.foci.add(new FocusSpec(wx, wy, 1));
						final int newIdx = config.foci.size() - 1;
						selectedFocusIndex = newIdx;
						draggingIndex = newIdx;
						rebuildAndSyncTable();
					}
				}
			}

			@Override
			public void mouseReleased(final MouseEvent e) {
				if (panning) {
					panning = false;
					computeFullRange();
					clampDistToFullRange();
					syncSliders();
					rebuild();
					return;
				}
				if (draggingIndex >= 0) {
					draggingIndex = -1;
					rebuildAndSyncTable();
				}
			}
		};
		draw.addMouseListener(press);
		draw.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(final MouseEvent e) {
				updateCursorInfo(e.getX(), e.getY());
				if (panning) {
					applyPan(e);
					return;
				}
				if (draggingIndex < 0 || draggingIndex >= config.foci.size())
					return;
				final FocusSpec f = config.foci.get(draggingIndex);
				f.x = pl.unfitx(e.getX());
				f.y = pl.unfity(e.getY());
				rebuild();
			}

			@Override
			public void mouseMoved(final MouseEvent e) {
				updateCursorInfo(e.getX(), e.getY());
			}
		});
		final MouseWheelListener wheel = (final MouseWheelEvent e) -> applyZoom(e);
		draw.addMouseWheelListener(wheel);
	}

	private void installKeyboardShortcuts() {
		// Bind to the (focusable, click-to-focus) draw panel only: window-global
		// bindings would steal arrows/Delete from text fields and table editors
		final JPanel draw = pl.getDrawPanel();
		final InputMap im = draw.getInputMap(JComponent.WHEN_FOCUSED);
		final ActionMap am = draw.getActionMap();

		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteFocus");
		am.put("deleteFocus", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(final ActionEvent e) {
				deleteSelectedFocus();
			}
		});

		bindNudge(im, am, KeyEvent.VK_LEFT, 0, -NUDGE_STEP, 0);
		bindNudge(im, am, KeyEvent.VK_RIGHT, 0, NUDGE_STEP, 0);
		bindNudge(im, am, KeyEvent.VK_UP, 0, 0, NUDGE_STEP);
		bindNudge(im, am, KeyEvent.VK_DOWN, 0, 0, -NUDGE_STEP);
		bindNudge(im, am, KeyEvent.VK_LEFT, KeyEvent.SHIFT_DOWN_MASK, -NUDGE_FINE, 0);
		bindNudge(im, am, KeyEvent.VK_RIGHT, KeyEvent.SHIFT_DOWN_MASK, NUDGE_FINE, 0);
		bindNudge(im, am, KeyEvent.VK_UP, KeyEvent.SHIFT_DOWN_MASK, 0, NUDGE_FINE);
		bindNudge(im, am, KeyEvent.VK_DOWN, KeyEvent.SHIFT_DOWN_MASK, 0, -NUDGE_FINE);
	}

	private void bindNudge(final InputMap im, final ActionMap am, final int keyCode, final int modifiers, final double dx,
			final double dy) {
		final String name = "nudge_" + keyCode + "_" + modifiers;
		im.put(KeyStroke.getKeyStroke(keyCode, modifiers), name);
		am.put(name, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(final ActionEvent e) {
				nudge(dx, dy);
			}
		});
	}

	private void deleteSelectedFocus() {
		removeFocusAt(selectedFocusIndex);
	}

	private void removeFocusAt(final int idx) {
		if (idx < 0 || idx >= config.foci.size() || config.foci.size() <= 1)
			return;
		config.foci.remove(idx);
		// Keep "nothing selected" instead of side-selecting focus 0
		if (selectedFocusIndex == idx)
			selectedFocusIndex = -1;
		else if (selectedFocusIndex > idx)
			selectedFocusIndex--;
		if (selectedFocusIndex >= config.foci.size())
			selectedFocusIndex = config.foci.size() - 1;
		rebuildAndSyncTable();
	}

	private void nudge(final double dx, final double dy) {
		if (selectedFocusIndex < 0 || selectedFocusIndex >= config.foci.size())
			return;
		final FocusSpec f = config.foci.get(selectedFocusIndex);
		f.x += dx;
		f.y += dy;
		rebuildAndSyncTable();
	}

	private void updateCursorInfo(final int px, final int py) {
		final double wx = pl.unfitx(px);
		final double wy = pl.unfity(py);
		if (probeCurve == null) {
			lblCursor.setText(String.format("(%.3f, %.3f)", wx, wy));
		} else {
			final double f = probeCurve.getCumultDistance(wx, wy);
			lblCursor.setText(String.format("(%.3f, %.3f)  f=%.4g", wx, wy, f));
		}
	}

	private void applyPan(final MouseEvent e) {
		final int dxPx = e.getX() - panStartPixelX;
		final int dyPx = e.getY() - panStartPixelY;
		final double rx = panStartXmax - panStartXmin;
		final double ry = panStartYmax - panStartYmin;
		final double dxWorld = dxPx * rx / pl.getXres();
		final double dyWorld = dyPx * ry / pl.getYres();
		pl.setDim(panStartXmin - dxWorld, panStartXmax - dxWorld, panStartYmin + dyWorld, panStartYmax + dyWorld);
	}

	private void applyZoom(final MouseWheelEvent e) {
		final double cx = pl.unfitx(e.getX());
		final double cy = pl.unfity(e.getY());
		final double scale = e.getWheelRotation() < 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP;
		pl.setDim(cx + (pl.getXmin() - cx) * scale, cx + (pl.getXmax() - cx) * scale, cy + (pl.getYmin() - cy) * scale,
				cy + (pl.getYmax() - cy) * scale);
		computeFullRange();
		clampDistToFullRange();
		syncSliders();
		rebuild();
	}

	private int hitTest(final int px, final int py) {
		int best = -1;
		double bestDistSq = HIT_PIXELS * HIT_PIXELS;
		for (int i = 0; i < config.foci.size(); i++) {
			final FocusSpec f = config.foci.get(i);
			final double dx = pl.fitx(f.x) - px;
			final double dy = pl.fity(f.y) - py;
			final double d2 = dx * dx + dy * dy;
			if (d2 < bestDistSq) {
				bestDistSq = d2;
				best = i;
			}
		}
		return best;
	}

	private PlotDistanceCurve makeProbeCurve() {
		final Point[] ps = new Point[config.foci.size()];
		final double[] ws = new double[config.foci.size()];
		for (int i = 0; i < ps.length; i++) {
			ps[i] = new Point(config.foci.get(i).x, config.foci.get(i).y);
			ws[i] = config.foci.get(i).weight;
		}
		return makeCurve(ps, ws, 0);
	}

	private void rebuildAndSyncTable() {
		rebuild();
		syncTableFromConfig();
	}

	private void rebuild() {
		pl.clearPlots();
		pl.clearPoints();

		final Point[] ps = new Point[config.foci.size()];
		final double[] ws = new double[config.foci.size()];
		for (int i = 0; i < ps.length; i++) {
			final FocusSpec f = config.foci.get(i);
			ps[i] = new Point(f.x, f.y);
			ws[i] = f.weight;
			final Color c = (i == selectedFocusIndex) ? Color.ORANGE : Color.BLUE;
			pl.addPoint(ps[i], c);
		}

		probeCurve = config.foci.isEmpty() ? null : makeCurve(ps, ws, 0);

		final int n = Math.max(1, config.nCurves);
		final boolean useLog = config.logSpacing && config.dmin > 0 && config.dmax > 0;
		final double logMin = useLog ? Math.log(config.dmin) : 0;
		final double logMax = useLog ? Math.log(config.dmax) : 0;
		final PlotDistanceCurve[] curves = new PlotDistanceCurve[n];
		for (int i = 0; i < n; i++) {
			final double d;
			if (n == 1)
				d = config.dmin;
			else if (useLog)
				d = Math.exp(logMin + (logMax - logMin) * i / (n - 1));
			else
				d = config.dmin + (config.dmax - config.dmin) * i / (n - 1);
			curves[i] = makeCurve(ps, ws, d);
		}

		if (config.showBackground && curves.length > 0)
			curves[0].setBkgrdOn(true);

		if (config.showMinMax && curves.length > 0) {
			if (config.curveType != CurveType.CASSIN) {
				final Point pMin = curves[0].getMinPoint(pl);
				pl.addPoint(pMin, Color.RED);
			}
			final Point pMax = curves[0].getMaxPoint(pl);
			pl.addPoint(pMax, Color.CYAN);
		}

		for (int i = 0; i < curves.length; i++)
			pl.addPlot(curves[i], curveColor(i, curves.length));
		pl.refresh();
	}

	private void syncTableFromConfig() {
		suppressTableEvents = true;
		fociTableModel.setRowCount(0);
		for (final FocusSpec f : config.foci)
			fociTableModel.addRow(new Object[]{fmt(f.x), fmt(f.y), fmt(f.weight) });
		if (selectedFocusIndex >= 0 && selectedFocusIndex < fociTableModel.getRowCount())
			fociTable.setRowSelectionInterval(selectedFocusIndex, selectedFocusIndex);
		else
			fociTable.clearSelection();
		suppressTableEvents = false;
	}

	private static String fmt(final double d) {
		if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15)
			return String.valueOf((long) d);
		return String.format("%.4f", d);
	}

	private Color curveColor(final int i, final int n) {
		if (n <= 1)
			return Color.BLACK;
		final float hue = (float) (0.66 * (1.0 - (double) i / (n - 1)));
		return Color.getHSBColor(hue, 0.85f, 0.7f);
	}

	private PlotDistanceCurve makeCurve(final Point[] ps, final double[] ws, final double dist) {
		switch (config.curveType) {
			case CASSIN:
				return new NCassin(ps, dist, ws);
			case HYPERB:
				return new NHyperb(ps, dist, ws);
			case LIPSE:
			default:
				return new NLipse(ps, dist, ws);
		}
	}
}
