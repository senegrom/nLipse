package Main;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import Main.PlotConfig.CurveType;
import Main.PlotConfig.FocusSpec;

public final class SetupDialog extends JFrame {
	private static final long serialVersionUID = 1L;

	private final PlotConfig config;
	private final JComboBox<CurveType> cmbType;
	private final DefaultTableModel fociModel;
	private final JTable fociTable;
	private final JTextField txtDmin;
	private final JTextField txtDmax;
	private final JTextField txtNCurves;
	private final JTextField txtXmin;
	private final JTextField txtXmax;
	private final JTextField txtYmin;
	private final JTextField txtYmax;
	private final JCheckBox chkBackground;
	private final JCheckBox chkMinMax;
	private final JCheckBox chkAntiAlias;

	private Consumer<PlotConfig> onConfirm;
	private Runnable onCancel;

	public SetupDialog(final PlotConfig initial) {
		super(Main.PROGRAM_NAME + " Setup");
		if (initial == null)
			throw new IllegalArgumentException("Initial configuration must not be null");
		config = initial;
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		cmbType = new JComboBox<>(CurveType.values());
		cmbType.setSelectedItem(initial.curveType);

		fociModel = new DefaultTableModel(new String[]{"X", "Y", "Weight"}, 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public Class<?> getColumnClass(final int columnIndex) {
				return String.class;
			}
		};
		for (final FocusSpec focus : initial.foci)
			fociModel.addRow(new Object[]{formatNumber(focus.x), formatNumber(focus.y), formatNumber(focus.weight)});
		fociTable = new JTable(fociModel);
		final DefaultCellEditor setupCellEditor = new DefaultCellEditor(new JTextField());
		setupCellEditor.setClickCountToStart(2);
		fociTable.setDefaultEditor(Object.class, setupCellEditor);
		fociTable.setRowHeight(22);
		fociTable.setPreferredScrollableViewportSize(new Dimension(260, 160));

		txtDmin = new JTextField(formatNumber(initial.dmin), 8);
		txtDmax = new JTextField(formatNumber(initial.dmax), 8);
		txtNCurves = new JTextField(String.valueOf(initial.nCurves), 4);
		txtXmin = new JTextField(formatNumber(initial.xmin), 6);
		txtXmax = new JTextField(formatNumber(initial.xmax), 6);
		txtYmin = new JTextField(formatNumber(initial.ymin), 6);
		txtYmax = new JTextField(formatNumber(initial.ymax), 6);
		chkBackground = new JCheckBox("Show background (first curve)", initial.showBackground);
		chkMinMax = new JCheckBox("Show min/max points", initial.showMinMax);
		chkAntiAlias = new JCheckBox("Anti-alias axes and points", initial.antiAlias);

		buildUI();
		pack();
		setLocationRelativeTo(null);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(final WindowEvent event) {
				if (onCancel != null)
					onCancel.run();
			}
		});
	}

	public void setOnConfirm(final Consumer<PlotConfig> onConfirm) {
		this.onConfirm = onConfirm;
	}

	public void setOnCancel(final Runnable onCancel) {
		this.onCancel = onCancel;
	}

	private static String formatNumber(final double value) {
		if (value == Math.floor(value) && Double.isFinite(value) && Math.abs(value) < 1e15)
			return String.valueOf((long) value);
		return Double.toString(value);
	}

	private void buildUI() {
		final JPanel root = new JPanel(new BorderLayout(10, 10));
		root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		final JPanel form = new JPanel();
		form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
		form.add(labeledRow("Curve type:", cmbType));
		form.add(Box.createVerticalStrut(8));

		final JPanel fociPanel = new JPanel(new BorderLayout(5, 5));
		fociPanel.setBorder(BorderFactory.createTitledBorder("Focus points"));
		fociPanel.add(new JScrollPane(fociTable), BorderLayout.CENTER);
		final JPanel fociButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		final JButton addButton = new JButton("Add");
		final JButton removeButton = new JButton("Remove selected");
		addButton.addActionListener(event -> fociModel.addRow(new Object[]{"0", "0", "1"}));
		removeButton.addActionListener(event -> {
			final int row = fociTable.getSelectedRow();
			if (row >= 0)
				fociModel.removeRow(row);
		});
		fociButtons.add(addButton);
		fociButtons.add(removeButton);
		fociPanel.add(fociButtons, BorderLayout.SOUTH);
		form.add(fociPanel);
		form.add(Box.createVerticalStrut(8));

		final JPanel distancePanel = new JPanel(new GridBagLayout());
		distancePanel.setBorder(BorderFactory.createTitledBorder("Distance range"));
		final GridBagConstraints constraints = new GridBagConstraints();
		constraints.insets = new Insets(2, 4, 2, 4);
		constraints.anchor = GridBagConstraints.WEST;
		constraints.gridy = 0;
		constraints.gridx = 0;
		distancePanel.add(new JLabel("Min:"), constraints);
		constraints.gridx++;
		distancePanel.add(txtDmin, constraints);
		constraints.gridx++;
		distancePanel.add(new JLabel("Max:"), constraints);
		constraints.gridx++;
		distancePanel.add(txtDmax, constraints);
		constraints.gridx++;
		distancePanel.add(new JLabel("Count:"), constraints);
		constraints.gridx++;
		distancePanel.add(txtNCurves, constraints);
		form.add(distancePanel);
		form.add(Box.createVerticalStrut(8));

		final JPanel rangePanel = new JPanel(new GridBagLayout());
		rangePanel.setBorder(BorderFactory.createTitledBorder("Plot range"));
		constraints.gridy = 0;
		constraints.gridx = 0;
		rangePanel.add(new JLabel("X:"), constraints);
		constraints.gridx++;
		rangePanel.add(txtXmin, constraints);
		constraints.gridx++;
		rangePanel.add(new JLabel("to"), constraints);
		constraints.gridx++;
		rangePanel.add(txtXmax, constraints);
		constraints.gridy++;
		constraints.gridx = 0;
		rangePanel.add(new JLabel("Y:"), constraints);
		constraints.gridx++;
		rangePanel.add(txtYmin, constraints);
		constraints.gridx++;
		rangePanel.add(new JLabel("to"), constraints);
		constraints.gridx++;
		rangePanel.add(txtYmax, constraints);
		form.add(rangePanel);
		form.add(Box.createVerticalStrut(8));

		final JPanel optionPanel = new JPanel();
		optionPanel.setLayout(new BoxLayout(optionPanel, BoxLayout.Y_AXIS));
		optionPanel.setBorder(BorderFactory.createTitledBorder("Options"));
		optionPanel.add(chkBackground);
		optionPanel.add(chkMinMax);
		optionPanel.add(chkAntiAlias);
		form.add(optionPanel);
		root.add(form, BorderLayout.CENTER);

		final JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		final JButton okButton = new JButton("OK");
		final JButton cancelButton = new JButton("Cancel");
		okButton.addActionListener(event -> onOK());
		cancelButton.addActionListener(event -> {
			dispose();
			if (onCancel != null)
				onCancel.run();
		});
		buttonRow.add(okButton);
		buttonRow.add(cancelButton);
		root.add(buttonRow, BorderLayout.SOUTH);

		getRootPane().setDefaultButton(okButton);
		setContentPane(root);
	}

	private static JPanel labeledRow(final String label, final java.awt.Component component) {
		final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		panel.add(new JLabel(label));
		panel.add(component);
		return panel;
	}

	private void onOK() {
		if (fociTable.isEditing())
			fociTable.getCellEditor().stopCellEditing();

		try {
			final CurveType curveType = (CurveType) cmbType.getSelectedItem();
			if (curveType == null)
				throw new NumberFormatException("A curve type is required");

			final double dmin = parseFiniteDouble(txtDmin.getText(), "Distance min");
			final double dmax = parseFiniteDouble(txtDmax.getText(), "Distance max");
			if (dmin > dmax)
				throw new NumberFormatException("Distance min must be ≤ max");

			final int nCurves = parseInt(txtNCurves.getText(), "Curve count", 1, 200);
			final double xmin = parseFiniteDouble(txtXmin.getText(), "Plot X min");
			final double xmax = parseFiniteDouble(txtXmax.getText(), "Plot X max");
			final double ymin = parseFiniteDouble(txtYmin.getText(), "Plot Y min");
			final double ymax = parseFiniteDouble(txtYmax.getText(), "Plot Y max");
			if (xmin >= xmax || ymin >= ymax)
				throw new NumberFormatException("Plot range must have min < max");

			final List<FocusSpec> parsedFoci = new ArrayList<>();
			for (int i = 0; i < fociModel.getRowCount(); i++) {
				final double x = parseFiniteDouble(String.valueOf(fociModel.getValueAt(i, 0)), "Focus " + (i + 1) + " X");
				final double y = parseFiniteDouble(String.valueOf(fociModel.getValueAt(i, 1)), "Focus " + (i + 1) + " Y");
				final double weight = parseFiniteDouble(String.valueOf(fociModel.getValueAt(i, 2)),
						"Focus " + (i + 1) + " weight");
				parsedFoci.add(new FocusSpec(x, y, weight));
			}
			if (parsedFoci.isEmpty())
				throw new NumberFormatException("At least one focus point is required");

			config.curveType = curveType;
			config.dmin = dmin;
			config.dmax = dmax;
			config.nCurves = nCurves;
			config.xmin = xmin;
			config.xmax = xmax;
			config.ymin = ymin;
			config.ymax = ymax;
			config.showBackground = chkBackground.isSelected();
			config.showMinMax = chkMinMax.isSelected();
			config.antiAlias = chkAntiAlias.isSelected();
			config.foci.clear();
			config.foci.addAll(parsedFoci);
		} catch (final NumberFormatException exception) {
			JOptionPane.showMessageDialog(this, exception.getMessage(), "Invalid input", JOptionPane.ERROR_MESSAGE);
			return;
		}

		dispose();
		if (onConfirm != null)
			onConfirm.accept(config);
	}

	private static double parseFiniteDouble(final String text, final String field) {
		final double value;
		try {
			value = Double.parseDouble(text.trim());
		} catch (final RuntimeException exception) {
			throw new NumberFormatException(field + ": '" + text + "' is not a valid number");
		}
		if (!Double.isFinite(value))
			throw new NumberFormatException(field + " must be finite");
		return value;
	}

	private static int parseInt(final String text, final String field, final int min, final int max) {
		final int value;
		try {
			value = Integer.parseInt(text.trim());
		} catch (final RuntimeException exception) {
			throw new NumberFormatException(field + ": '" + text + "' is not a valid integer");
		}
		if (value < min || value > max)
			throw new NumberFormatException(field + " must be between " + min + " and " + max);
		return value;
	}
}
