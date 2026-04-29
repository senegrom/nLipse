package Main;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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

public class SetupDialog extends JFrame {
	private static final long		serialVersionUID	= 1L;

	private final PlotConfig		config;

	private final JComboBox<String>	cmbType;
	private final DefaultTableModel	fociModel;
	private final JTable			fociTable;
	private final JTextField		txtDmin, txtDmax, txtNCurves;
	private final JTextField		txtXmin, txtXmax, txtYmin, txtYmax;
	private final JCheckBox			chkBackground, chkMinMax, chkAntiAlias;

	private Consumer<PlotConfig>	onConfirm;
	private Runnable				onCancel;

	public SetupDialog(final PlotConfig initial) {
		super("nLipse Setup");
		config = initial;
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		cmbType = new JComboBox<>(new String[]{"n-Ellipse (sum)", "Cassini oval (product)", "n-Hyperbola (avg diff)" });
		cmbType.setSelectedIndex(initial.curveType.ordinal());

		fociModel = new DefaultTableModel(new String[]{"X", "Y", "Weight" }, 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public Class<?> getColumnClass(final int columnIndex) {
				return String.class;
			}
		};
		for (final FocusSpec f : initial.foci)
			fociModel.addRow(new Object[]{str(f.x), str(f.y), str(f.weight) });
		fociTable = new JTable(fociModel);
		fociTable.setDefaultEditor(Object.class, new DefaultCellEditor(new JTextField()));
		fociTable.setRowHeight(22);
		fociTable.setPreferredScrollableViewportSize(new Dimension(260, 160));

		txtDmin = new JTextField(str(initial.dmin), 8);
		txtDmax = new JTextField(str(initial.dmax), 8);
		txtNCurves = new JTextField(String.valueOf(initial.nCurves), 4);
		txtXmin = new JTextField(str(initial.xmin), 6);
		txtXmax = new JTextField(str(initial.xmax), 6);
		txtYmin = new JTextField(str(initial.ymin), 6);
		txtYmax = new JTextField(str(initial.ymax), 6);
		chkBackground = new JCheckBox("Show background (first curve)", initial.showBackground);
		chkMinMax = new JCheckBox("Show min/max points", initial.showMinMax);
		chkAntiAlias = new JCheckBox("Anti-alias", initial.antiAlias);

		buildUI();
		pack();
		setLocationRelativeTo(null);
		setAlwaysOnTop(true);
		setAlwaysOnTop(false);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(final WindowEvent e) {
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

	private static String str(final double d) {
		if (d == Math.floor(d) && !Double.isInfinite(d))
			return String.valueOf((long) d);
		return String.valueOf(d);
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
		final JPanel fociBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		final JButton btnAdd = new JButton("Add");
		final JButton btnRemove = new JButton("Remove selected");
		btnAdd.addActionListener(e -> fociModel.addRow(new Object[]{"0", "0", "1" }));
		btnRemove.addActionListener(e -> {
			final int row = fociTable.getSelectedRow();
			if (row >= 0)
				fociModel.removeRow(row);
		});
		fociBtns.add(btnAdd);
		fociBtns.add(btnRemove);
		fociPanel.add(fociBtns, BorderLayout.SOUTH);
		form.add(fociPanel);
		form.add(Box.createVerticalStrut(8));

		final JPanel distPanel = new JPanel(new GridBagLayout());
		distPanel.setBorder(BorderFactory.createTitledBorder("Distance range"));
		final GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(2, 4, 2, 4);
		gc.anchor = GridBagConstraints.WEST;
		gc.gridy = 0;
		gc.gridx = 0;
		distPanel.add(new JLabel("Min:"), gc);
		gc.gridx++;
		distPanel.add(txtDmin, gc);
		gc.gridx++;
		distPanel.add(new JLabel("Max:"), gc);
		gc.gridx++;
		distPanel.add(txtDmax, gc);
		gc.gridx++;
		distPanel.add(new JLabel("Count:"), gc);
		gc.gridx++;
		distPanel.add(txtNCurves, gc);
		form.add(distPanel);
		form.add(Box.createVerticalStrut(8));

		final JPanel rangePanel = new JPanel(new GridBagLayout());
		rangePanel.setBorder(BorderFactory.createTitledBorder("Plot range"));
		gc.gridy = 0;
		gc.gridx = 0;
		rangePanel.add(new JLabel("X:"), gc);
		gc.gridx++;
		rangePanel.add(txtXmin, gc);
		gc.gridx++;
		rangePanel.add(new JLabel("to"), gc);
		gc.gridx++;
		rangePanel.add(txtXmax, gc);
		gc.gridy++;
		gc.gridx = 0;
		rangePanel.add(new JLabel("Y:"), gc);
		gc.gridx++;
		rangePanel.add(txtYmin, gc);
		gc.gridx++;
		rangePanel.add(new JLabel("to"), gc);
		gc.gridx++;
		rangePanel.add(txtYmax, gc);
		form.add(rangePanel);
		form.add(Box.createVerticalStrut(8));

		final JPanel optPanel = new JPanel();
		optPanel.setLayout(new BoxLayout(optPanel, BoxLayout.Y_AXIS));
		optPanel.setBorder(BorderFactory.createTitledBorder("Options"));
		optPanel.add(chkBackground);
		optPanel.add(chkMinMax);
		optPanel.add(chkAntiAlias);
		form.add(optPanel);

		root.add(form, BorderLayout.CENTER);

		final JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		final JButton btnOK = new JButton("OK");
		final JButton btnCancel = new JButton("Cancel");
		btnOK.addActionListener(e -> onOK());
		btnCancel.addActionListener(e -> {
			dispose();
			if (onCancel != null)
				onCancel.run();
		});
		btnRow.add(btnOK);
		btnRow.add(btnCancel);
		root.add(btnRow, BorderLayout.SOUTH);

		getRootPane().setDefaultButton(btnOK);
		setContentPane(root);
	}

	private JPanel labeledRow(final String label, final java.awt.Component c) {
		final JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		p.add(new JLabel(label));
		p.add(c);
		return p;
	}

	private void onOK() {
		if (fociTable.isEditing())
			fociTable.getCellEditor().stopCellEditing();
		try {
			config.curveType = CurveType.values()[cmbType.getSelectedIndex()];
			config.dmin = parseDouble(txtDmin.getText(), "Distance min");
			config.dmax = parseDouble(txtDmax.getText(), "Distance max");
			config.nCurves = parseInt(txtNCurves.getText(), "Curve count", 1, 200);
			config.xmin = parseDouble(txtXmin.getText(), "Plot X min");
			config.xmax = parseDouble(txtXmax.getText(), "Plot X max");
			config.ymin = parseDouble(txtYmin.getText(), "Plot Y min");
			config.ymax = parseDouble(txtYmax.getText(), "Plot Y max");
			if (config.xmin >= config.xmax || config.ymin >= config.ymax)
				throw new NumberFormatException("Plot range must have min < max");
			if (config.dmin > config.dmax)
				throw new NumberFormatException("Distance min must be ≤ max");
			config.showBackground = chkBackground.isSelected();
			config.showMinMax = chkMinMax.isSelected();
			config.antiAlias = chkAntiAlias.isSelected();

			config.foci.clear();
			final int rows = fociModel.getRowCount();
			for (int i = 0; i < rows; i++) {
				final double x = parseDouble(String.valueOf(fociModel.getValueAt(i, 0)), "Focus " + (i + 1) + " X");
				final double y = parseDouble(String.valueOf(fociModel.getValueAt(i, 1)), "Focus " + (i + 1) + " Y");
				final double w = parseDouble(String.valueOf(fociModel.getValueAt(i, 2)), "Focus " + (i + 1) + " weight");
				config.foci.add(new FocusSpec(x, y, w));
			}
			if (config.foci.isEmpty())
				throw new NumberFormatException("At least one focus point is required");
		} catch (final NumberFormatException ex) {
			JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid input", JOptionPane.ERROR_MESSAGE);
			return;
		}
		dispose();
		if (onConfirm != null)
			onConfirm.accept(config);
	}

	private static double parseDouble(final String s, final String field) {
		try {
			return Double.parseDouble(s.trim());
		} catch (final Exception e) {
			throw new NumberFormatException(field + ": '" + s + "' is not a valid number");
		}
	}

	private static int parseInt(final String s, final String field, final int min, final int max) {
		final int v;
		try {
			v = Integer.parseInt(s.trim());
		} catch (final Exception e) {
			throw new NumberFormatException(field + ": '" + s + "' is not a valid integer");
		}
		if (v < min || v > max)
			throw new NumberFormatException(field + " must be between " + min + " and " + max);
		return v;
	}
}
