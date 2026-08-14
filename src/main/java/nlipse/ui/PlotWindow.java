package nlipse.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import nlipse.model.CurveType;
import nlipse.model.Focus;
import nlipse.model.PlotSnapshot;

/** Swing view only; interaction and rendering orchestration live in PlotController. */
public final class PlotWindow extends JFrame {
    private static final long serialVersionUID = 1L;

    private final PlotCanvas canvas = new PlotCanvas();
    private final JComboBox<CurveType> curveType = new JComboBox<>(CurveType.values());
    private final JLabel curveDescription = new JLabel();
    private final JLabel familyParameterLabel = new JLabel("Parameter:");
    private final JTextField familyParameter = new JTextField(9);
    private final JSlider distanceMin = new JSlider(0, 1000, 50);
    private final JSlider distanceMax = new JSlider(0, 1000, 950);
    private final JLabel distanceMinLabel = new JLabel();
    private final JLabel distanceMaxLabel = new JLabel();
    private final JTextField curveCount = new JTextField(4);
    private final JCheckBox logSpacing = new JCheckBox("Log spacing");
    private final JCheckBox showBackground = new JCheckBox("Background field");
    private final JCheckBox showExtrema = new JCheckBox("Min/max samples");
    private final JCheckBox antiAlias = new JCheckBox("Anti-alias lines and markers");
    private final JCheckBox showLegend = new JCheckBox("Level legend");
    private final DefaultTableModel focusTableModel = new DefaultTableModel(
            new String[]{"X", "Y", "Weight"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public Class<?> getColumnClass(final int columnIndex) {
            return String.class;
        }
    };
    private final JTable focusTable = new JTable(focusTableModel);
    private final JButton addFocus = new JButton("Add");
    private final JButton removeFocus = new JButton("Remove");
    private final JButton fitDistance = new JButton("Fit levels");
    private final JButton resetView = new JButton("Reset view");
    private final JButton saveSetup = new JButton("Save setup…");
    private final JButton loadSetup = new JButton("Load setup…");
    private final JButton exportImage = new JButton("Export PNG…");
    private final JButton exportSvg = new JButton("Export SVG…");
    private final JLabel cursorInfo = new JLabel("Move over plot for coordinates");
    private final JLabel renderInfo = new JLabel(" ", SwingConstants.LEFT);

    public PlotWindow(final PlotSnapshot snapshot, final String version) {
        super("nLipse " + version);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(canvas, BorderLayout.CENTER);
        add(buildSidePanel(), BorderLayout.EAST);
        setMinimumSize(new Dimension(720, 500));
        setExtendedState(Frame.MAXIMIZED_BOTH);
        initialise(snapshot);
    }

    private JScrollPane buildSidePanel() {
        final JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        side.setPreferredSize(new Dimension(390, 800));

        final JPanel typePanel = new JPanel();
        typePanel.setLayout(new BoxLayout(typePanel, BoxLayout.Y_AXIS));
        typePanel.setBorder(BorderFactory.createTitledBorder("Curve family"));
        final JPanel typeRow = rowPanel();
        typeRow.add(curveType);
        typePanel.add(typeRow);
        curveDescription.setBorder(BorderFactory.createEmptyBorder(0, 7, 5, 7));
        typePanel.add(curveDescription);
        final JPanel parameterRow = rowPanel();
        parameterRow.add(familyParameterLabel);
        parameterRow.add(familyParameter);
        typePanel.add(parameterRow);
        side.add(typePanel);
        side.add(Box.createVerticalStrut(6));

        final JPanel distancePanel = new JPanel();
        distancePanel.setLayout(new BoxLayout(distancePanel, BoxLayout.Y_AXIS));
        distancePanel.setBorder(BorderFactory.createTitledBorder("Field levels"));
        distancePanel.add(distanceMinLabel);
        distancePanel.add(distanceMin);
        distancePanel.add(distanceMaxLabel);
        distancePanel.add(distanceMax);
        final JPanel countRow = rowPanel();
        countRow.add(new JLabel("Count:"));
        countRow.add(curveCount);
        countRow.add(logSpacing);
        distancePanel.add(countRow);
        final JPanel distanceButtons = rowPanel();
        distanceButtons.add(fitDistance);
        distanceButtons.add(resetView);
        distancePanel.add(distanceButtons);
        side.add(distancePanel);
        side.add(Box.createVerticalStrut(8));

        final JPanel options = new JPanel();
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
        options.setBorder(BorderFactory.createTitledBorder("Display"));
        options.add(showBackground);
        options.add(showExtrema);
        options.add(antiAlias);
        options.add(showLegend);
        side.add(options);
        side.add(Box.createVerticalStrut(8));

        final JPanel session = new JPanel();
        session.setLayout(new BoxLayout(session, BoxLayout.Y_AXIS));
        session.setBorder(BorderFactory.createTitledBorder("Session"));
        final JPanel setupRow = rowPanel();
        setupRow.add(saveSetup);
        setupRow.add(loadSetup);
        session.add(setupRow);
        final JPanel exportRow = rowPanel();
        exportRow.add(exportImage);
        exportRow.add(exportSvg);
        session.add(exportRow);
        side.add(session);
        side.add(Box.createVerticalStrut(8));

        final JPanel cursorPanel = new JPanel(new BorderLayout());
        cursorPanel.setBorder(BorderFactory.createTitledBorder("Cursor"));
        cursorPanel.add(cursorInfo, BorderLayout.CENTER);
        side.add(cursorPanel);
        side.add(Box.createVerticalStrut(8));

        focusTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        focusTable.setRowHeight(22);
        final DefaultCellEditor editor = new DefaultCellEditor(new JTextField());
        editor.setClickCountToStart(2);
        focusTable.setDefaultEditor(Object.class, editor);
        final JPanel focusPanel = new JPanel(new BorderLayout(5, 5));
        focusPanel.setBorder(BorderFactory.createTitledBorder("Focus points"));
        final JScrollPane tableScroll = new JScrollPane(focusTable);
        tableScroll.setPreferredSize(new Dimension(275, 210));
        focusPanel.add(tableScroll, BorderLayout.CENTER);
        final JPanel focusButtons = rowPanel();
        focusButtons.add(addFocus);
        focusButtons.add(removeFocus);
        focusPanel.add(focusButtons, BorderLayout.SOUTH);
        side.add(focusPanel);
        side.add(Box.createVerticalStrut(8));

        final JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createTitledBorder("Controls"));
        controls.add(new JLabel("Left-click/drag: add or move focus"));
        controls.add(new JLabel("Right-click: remove focus"));
        controls.add(new JLabel("Middle-drag: pan"));
        controls.add(new JLabel("Mouse wheel: zoom"));
        controls.add(new JLabel("Plot-focused arrows: nudge"));
        controls.add(new JLabel("Shift+arrows: fine nudge"));
        controls.add(new JLabel("Delete: remove selected focus"));
        side.add(controls);
        side.add(Box.createVerticalStrut(8));
        side.add(renderInfo);
        side.add(Box.createVerticalGlue());

        final JScrollPane scroll = new JScrollPane(side);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(405, 0));
        return scroll;
    }

    private static JPanel rowPanel() {
        return new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
    }

    private void initialise(final PlotSnapshot snapshot) {
        syncControls(snapshot);
    }

    /** Align every non-slider control with the model (after a setup load).
     *  Callers must hold the controller's suppress flags: the combo and
     *  checkbox listeners fire on programmatic changes too. */
    public void syncControls(final PlotSnapshot snapshot) {
        curveType.setSelectedItem(snapshot.curveType());
        setCurvePresentation(snapshot.curveType(), snapshot.familyParameter());
        curveCount.setText(Integer.toString(snapshot.curveCount()));
        logSpacing.setSelected(snapshot.logSpacing());
        showBackground.setSelected(snapshot.showBackground());
        showExtrema.setSelected(snapshot.showExtrema());
        antiAlias.setSelected(snapshot.antiAlias());
        showLegend.setSelected(snapshot.showLegend());
        setFocusRows(snapshot.foci(), snapshot.selectedFocusIndex());
    }

    public void setCurvePresentation(final CurveType type, final double parameter) {
        final boolean enabled = type.usesParameter();
        curveDescription.setText(type.htmlDescription(345, parameter));
        curveType.setToolTipText(type.description());
        familyParameterLabel.setText(enabled ? type.parameterLabel() + ":" : "Parameter:");
        familyParameter.setEnabled(enabled);
        familyParameter.setEditable(enabled);
        familyParameter.setText(enabled ? type.formatParameter(parameter) : "not used");
        familyParameter.setToolTipText(enabled ? type.parameterDescription() : null);
    }

    public void setFocusRows(final List<Focus> foci, final int selectedIndex) {
        focusTableModel.setRowCount(0);
        for (final Focus focus : foci) {
            focusTableModel.addRow(new Object[]{EditableNumbers.format(focus.x()),
                    EditableNumbers.format(focus.y()), EditableNumbers.format(focus.weight())});
        }
        if (selectedIndex >= 0 && selectedIndex < foci.size()) {
            focusTable.setRowSelectionInterval(selectedIndex, selectedIndex);
        } else {
            focusTable.clearSelection();
        }
    }

    public PlotCanvas getCanvas() { return canvas; }
    public JComboBox<CurveType> getCurveType() { return curveType; }
    public JTextField getFamilyParameter() { return familyParameter; }
    public JSlider getDistanceMin() { return distanceMin; }
    public JSlider getDistanceMax() { return distanceMax; }
    public JLabel getDistanceMinLabel() { return distanceMinLabel; }
    public JLabel getDistanceMaxLabel() { return distanceMaxLabel; }
    public JTextField getCurveCount() { return curveCount; }
    public JCheckBox getLogSpacing() { return logSpacing; }
    public JCheckBox getShowBackground() { return showBackground; }
    public JCheckBox getShowExtrema() { return showExtrema; }
    public JCheckBox getAntiAlias() { return antiAlias; }
    public JCheckBox getShowLegend() { return showLegend; }
    public DefaultTableModel getFocusTableModel() { return focusTableModel; }
    public JTable getFocusTable() { return focusTable; }
    public JButton getAddFocus() { return addFocus; }
    public JButton getRemoveFocus() { return removeFocus; }
    public JButton getFitDistance() { return fitDistance; }
    public JButton getResetView() { return resetView; }
    public JButton getSaveSetup() { return saveSetup; }
    public JButton getLoadSetup() { return loadSetup; }
    public JButton getExportImage() { return exportImage; }
    public JButton getExportSvg() { return exportSvg; }
    public JLabel getCursorInfo() { return cursorInfo; }
    public JLabel getRenderInfo() { return renderInfo; }
}
