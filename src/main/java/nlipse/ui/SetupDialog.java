package nlipse.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import nlipse.model.CurveType;
import nlipse.model.Focus;
import nlipse.model.PlotConfig;
import nlipse.render.Viewport;

/** Modal initial-configuration dialog. */
public final class SetupDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private final JComboBox<CurveType> curveType = new JComboBox<>(CurveType.values());
    private final DefaultTableModel focusModel = new DefaultTableModel(
            new String[]{"X", "Y", "Weight"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public Class<?> getColumnClass(final int columnIndex) {
            return String.class;
        }
    };
    private final JTable focusTable = new JTable(focusModel);
    private final JTextField distanceMin = new JTextField(8);
    private final JTextField distanceMax = new JTextField(8);
    private final JTextField curveCount = new JTextField(4);
    private final JTextField xMin = new JTextField(7);
    private final JTextField xMax = new JTextField(7);
    private final JTextField yMin = new JTextField(7);
    private final JTextField yMax = new JTextField(7);
    private final JCheckBox showBackground = new JCheckBox("Show background field");
    private final JCheckBox showExtrema = new JCheckBox("Show sampled min/max");
    private final JCheckBox antiAlias = new JCheckBox("Anti-alias lines and markers");
    private final JCheckBox logSpacing = new JCheckBox("Use logarithmic level spacing");

    private transient PlotConfig result;

    private SetupDialog(final Frame owner, final PlotConfig initial) {
        super(owner, "nLipse Setup", true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        initialise(initial);
        buildUi();
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    public static PlotConfig showDialog(final Frame owner, final PlotConfig initial) {
        final SetupDialog dialog = new SetupDialog(owner, initial);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void initialise(final PlotConfig initial) {
        curveType.setSelectedItem(initial.getCurveType());
        for (final Focus focus : initial.getFoci()) {
            focusModel.addRow(new Object[]{format(focus.getX()), format(focus.getY()),
                    format(focus.getWeight())});
        }
        focusTable.setRowHeight(22);
        final DefaultCellEditor editor = new DefaultCellEditor(new JTextField());
        editor.setClickCountToStart(2);
        focusTable.setDefaultEditor(Object.class, editor);
        focusTable.setPreferredScrollableViewportSize(new Dimension(285, 165));

        distanceMin.setText(format(initial.getDistanceMin()));
        distanceMax.setText(format(initial.getDistanceMax()));
        curveCount.setText(Integer.toString(initial.getCurveCount()));
        xMin.setText(format(initial.getViewport().getXMin()));
        xMax.setText(format(initial.getViewport().getXMax()));
        yMin.setText(format(initial.getViewport().getYMin()));
        yMax.setText(format(initial.getViewport().getYMax()));
        showBackground.setSelected(initial.isShowBackground());
        showExtrema.setSelected(initial.isShowExtrema());
        antiAlias.setSelected(initial.isAntiAlias());
        logSpacing.setSelected(initial.isLogSpacing());
    }

    private void buildUi() {
        final JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        final JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        form.add(labelledRow("Curve family:", curveType));
        form.add(Box.createVerticalStrut(7));

        final JPanel focusPanel = new JPanel(new BorderLayout(5, 5));
        focusPanel.setBorder(BorderFactory.createTitledBorder("Focus points"));
        focusPanel.add(new JScrollPane(focusTable), BorderLayout.CENTER);
        final JPanel focusButtons = rowPanel();
        final JButton add = new JButton("Add");
        final JButton remove = new JButton("Remove selected");
        add.addActionListener(event -> focusModel.addRow(new Object[]{"0", "0", "1"}));
        remove.addActionListener(event -> {
            final int row = focusTable.getSelectedRow();
            if (row >= 0) {
                focusModel.removeRow(row);
            }
        });
        focusButtons.add(add);
        focusButtons.add(remove);
        focusPanel.add(focusButtons, BorderLayout.SOUTH);
        form.add(focusPanel);
        form.add(Box.createVerticalStrut(7));

        final JPanel distancePanel = new JPanel(new GridBagLayout());
        distancePanel.setBorder(BorderFactory.createTitledBorder("Distance levels"));
        final GridBagConstraints constraints = constraints();
        addGrid(distancePanel, constraints, 0, 0, new JLabel("Min:"));
        addGrid(distancePanel, constraints, 1, 0, distanceMin);
        addGrid(distancePanel, constraints, 2, 0, new JLabel("Max:"));
        addGrid(distancePanel, constraints, 3, 0, distanceMax);
        addGrid(distancePanel, constraints, 4, 0, new JLabel("Count:"));
        addGrid(distancePanel, constraints, 5, 0, curveCount);
        form.add(distancePanel);
        form.add(Box.createVerticalStrut(7));

        final JPanel viewportPanel = new JPanel(new GridBagLayout());
        viewportPanel.setBorder(BorderFactory.createTitledBorder("Initial viewport"));
        addGrid(viewportPanel, constraints, 0, 0, new JLabel("X:"));
        addGrid(viewportPanel, constraints, 1, 0, xMin);
        addGrid(viewportPanel, constraints, 2, 0, new JLabel("to"));
        addGrid(viewportPanel, constraints, 3, 0, xMax);
        addGrid(viewportPanel, constraints, 0, 1, new JLabel("Y:"));
        addGrid(viewportPanel, constraints, 1, 1, yMin);
        addGrid(viewportPanel, constraints, 2, 1, new JLabel("to"));
        addGrid(viewportPanel, constraints, 3, 1, yMax);
        form.add(viewportPanel);
        form.add(Box.createVerticalStrut(7));

        final JPanel options = new JPanel();
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
        options.setBorder(BorderFactory.createTitledBorder("Display"));
        options.add(showBackground);
        options.add(showExtrema);
        options.add(antiAlias);
        options.add(logSpacing);
        form.add(options);
        root.add(form, BorderLayout.CENTER);

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        final JButton ok = new JButton("Open plot");
        final JButton cancel = new JButton("Cancel");
        ok.addActionListener(event -> accept());
        cancel.addActionListener(event -> dispose());
        buttons.add(ok);
        buttons.add(cancel);
        root.add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
        setContentPane(root);
    }

    private void accept() {
        if (focusTable.isEditing()) {
            focusTable.getCellEditor().stopCellEditing();
        }
        try {
            final CurveType selectedType = (CurveType) curveType.getSelectedItem();
            if (selectedType == null) {
                throw new IllegalArgumentException("Select a curve family");
            }
            final List<Focus> foci = new ArrayList<>();
            for (int row = 0; row < focusModel.getRowCount(); row++) {
                foci.add(new Focus(
                        parseFinite(focusModel.getValueAt(row, 0), "Focus " + (row + 1) + " X"),
                        parseFinite(focusModel.getValueAt(row, 1), "Focus " + (row + 1) + " Y"),
                        parseFinite(focusModel.getValueAt(row, 2), "Focus " + (row + 1) + " weight")));
            }
            if (foci.isEmpty()) {
                throw new IllegalArgumentException("At least one focus point is required");
            }
            final double parsedDistanceMin = parseFinite(distanceMin.getText(), "Distance min");
            final double parsedDistanceMax = parseFinite(distanceMax.getText(), "Distance max");
            final int parsedCount = parseInteger(curveCount.getText(), "Curve count", 1, 200);
            final Viewport viewport = new Viewport(
                    parseFinite(xMin.getText(), "X min"),
                    parseFinite(xMax.getText(), "X max"),
                    parseFinite(yMin.getText(), "Y min"),
                    parseFinite(yMax.getText(), "Y max"));
            result = new PlotConfig(selectedType, foci, parsedDistanceMin, parsedDistanceMax,
                    parsedCount, viewport, showBackground.isSelected(), showExtrema.isSelected(),
                    antiAlias.isSelected(), logSpacing.isSelected());
            dispose();
        } catch (final IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Invalid input",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static double parseFinite(final Object value, final String field) {
        final String text = String.valueOf(value).trim();
        final double parsed;
        try {
            parsed = Double.parseDouble(text);
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException(field + ": '" + text + "' is not a number");
        }
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
        return parsed;
    }

    private static int parseInteger(final Object value, final String field,
            final int minimum, final int maximum) {
        final String text = String.valueOf(value).trim();
        final int parsed;
        try {
            parsed = Integer.parseInt(text);
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException(field + ": '" + text + "' is not an integer");
        }
        if (parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException(field + " must be between " + minimum + " and " + maximum);
        }
        return parsed;
    }

    private static String format(final double value) {
        if (value == Math.floor(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static JPanel labelledRow(final String label, final Component component) {
        final JPanel row = rowPanel();
        row.add(new JLabel(label));
        row.add(component);
        return row;
    }

    private static JPanel rowPanel() {
        return new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    }

    private static GridBagConstraints constraints() {
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(2, 4, 2, 4);
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }

    private static void addGrid(final JPanel panel, final GridBagConstraints constraints,
            final int x, final int y, final Component component) {
        constraints.gridx = x;
        constraints.gridy = y;
        panel.add(component, constraints);
    }
}
