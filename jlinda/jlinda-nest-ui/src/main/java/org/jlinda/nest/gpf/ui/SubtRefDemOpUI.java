package org.jlinda.nest.gpf.ui;

import org.esa.snap.core.dataop.dem.ElevationModelDescriptor;
import org.esa.snap.core.dataop.dem.ElevationModelRegistry;
import org.esa.snap.dem.dataio.DEMFactory;
import org.esa.snap.graphbuilder.gpf.ui.BaseOperatorUI;
import org.esa.snap.graphbuilder.gpf.ui.OperatorUIUtils;
import org.esa.snap.graphbuilder.gpf.ui.UIValidation;
import org.esa.snap.graphbuilder.rcp.utils.DialogUtils;
import org.esa.snap.rcp.util.Dialogs;
import org.esa.snap.ui.AppContext;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.Map;

/**
 * User interface for SubtRefDemOp
 */
public class SubtRefDemOpUI extends BaseOperatorUI {

    private static final String[] demValueSet = DEMFactory.getDEMNameList();

    private final JTextField orbitDegree = new JTextField("");
    private final JComboBox<String> demName = new JComboBox<>(demValueSet);

    private static final String externalDEMStr = "External DEM";
    private final JTextField externalDEMFile = new JTextField("");
    private final JTextField externalDEMNoDataValue = new JTextField("");
    private final JButton externalDEMBrowseButton = new JButton("...");
    private final JLabel externalDEMFileLabel = new JLabel("External DEM:");
    private final JLabel externalDEMNoDataValueLabel = new JLabel("DEM No Data Value:");

    private final JCheckBox outputTopoPhaseBand = new JCheckBox("Output topographic phase band");
    private final JCheckBox outputElevationBand = new JCheckBox("Output elevation band");
    private final JCheckBox outputLatLonBands = new JCheckBox("Output orthorectified Lat/Lon bands");

    private Double extNoDataValue = 0.0;
    private final DialogUtils.TextAreaKeyListener textAreaKeyListener = new DialogUtils.TextAreaKeyListener();

    private final JComboBox<String> tileExtensionPercent = new JComboBox<>(new String[]{"20", "40", "60", "80", "100", "150", "200"});

    @Override
    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        demName.addItem(externalDEMStr);

        initializeOperatorUI(operatorName, parameterMap);

        final JComponent panel = createPanel();
        initParameters();

        demName.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent event) {
                final String item = ((String)demName.getSelectedItem()).replace(DEMFactory.AUTODEM, "");
                if(item.equals(externalDEMStr)) {
                    enableExternalDEM(true);
                } else {
                    externalDEMFile.setText("");
                    enableExternalDEM(false);
                }
            }
        });
        externalDEMFile.setColumns(30);
        final String demItem = ((String)demName.getSelectedItem()).replace(DEMFactory.AUTODEM, "");
        enableExternalDEM(demItem.equals(externalDEMStr));

        externalDEMBrowseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final File file = Dialogs.requestFileForOpen("External DEM File", false, null, DEMFactory.LAST_EXTERNAL_DEM_DIR_KEY);
                if(file != null) {
                    externalDEMFile.setText(file.getAbsolutePath());
                    extNoDataValue = OperatorUIUtils.getNoDataValue(file);
                }
                externalDEMNoDataValue.setText(String.valueOf(extNoDataValue));
            }
        });

        externalDEMNoDataValue.addKeyListener(textAreaKeyListener);

        return new JScrollPane(panel);
    }

    @Override
    public void initParameters() {
        orbitDegree.setText(String.valueOf(paramMap.get("orbitDegree")));
        final String demNameParam = (String) paramMap.get("demName");
        if (demNameParam != null) {
            ElevationModelDescriptor descriptor = ElevationModelRegistry.getInstance().getDescriptor(demNameParam);
            if(descriptor != null) {
                demName.setSelectedItem(DEMFactory.getDEMDisplayName(descriptor));
            } else {
                demName.setSelectedItem(demNameParam);
            }
        }
        final File extFile = (File)paramMap.get("externalDEMFile");
        if(extFile != null) {
            externalDEMFile.setText(extFile.getAbsolutePath());
            extNoDataValue =  (Double)paramMap.get("externalDEMNoDataValue");
            if(extNoDataValue != null && !textAreaKeyListener.isChangedByUser()) {
                externalDEMNoDataValue.setText(String.valueOf(extNoDataValue));
            }
        }
        tileExtensionPercent.setSelectedItem(paramMap.get("tileExtensionPercent"));

        Boolean outputTopoPhase = (Boolean)paramMap.get("outputTopoPhaseBand");
        if(outputTopoPhase != null) {
            outputTopoPhaseBand.setSelected(outputTopoPhase);
        }

        Boolean outputElevation = (Boolean)paramMap.get("outputElevationBand");
        if(outputElevation != null) {
            outputElevationBand.setSelected(outputElevation);
        }

        Boolean outputLatLon = (Boolean)paramMap.get("outputLatLonBands");
        if (outputLatLon != null) {
            outputLatLonBands.setSelected(outputLatLon);
        }
    }

    @Override
    public UIValidation validateParameters() {
        return new UIValidation(UIValidation.State.OK, "");
    }

    @Override
    public void updateParameters() {
        paramMap.put("orbitDegree", Integer.parseInt(orbitDegree.getText()));
        paramMap.put("demName", (DEMFactory.getProperDEMName((String) demName.getSelectedItem())));
        final String extFileStr = externalDEMFile.getText();
        if(!extFileStr.isEmpty()) {
            paramMap.put("externalDEMFile", new File(extFileStr));
            paramMap.put("externalDEMNoDataValue", Double.parseDouble(externalDEMNoDataValue.getText()));
        }
        paramMap.put("tileExtensionPercent", tileExtensionPercent.getSelectedItem());

        paramMap.put("outputTopoPhaseBand", outputTopoPhaseBand.isSelected());
        paramMap.put("outputElevationBand", outputElevationBand.isSelected());
        paramMap.put("outputLatLonBands", outputLatLonBands.isSelected());
    }

    private JComponent createPanel() {

        final JPanel contentPane = new JPanel();
        contentPane.setLayout(new GridBagLayout());
        final GridBagConstraints gbc = DialogUtils.createGridBagConstraints();

        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Orbit Interpolation Degree:", orbitDegree);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Digital Elevation Model:", demName);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, externalDEMFileLabel, externalDEMFile);
        gbc.gridx = 2;
        contentPane.add(externalDEMBrowseButton, gbc);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, externalDEMNoDataValueLabel, externalDEMNoDataValue);
        gbc.gridy++;

        gbc.gridx = 0;
        DialogUtils.addComponent(contentPane, gbc, "Tile Extension [%]", tileExtensionPercent);
        gbc.gridy++;

        DialogUtils.addComponent(contentPane, gbc, "", outputTopoPhaseBand);
        gbc.gridy++;

        DialogUtils.addComponent(contentPane, gbc, "", outputElevationBand);
        gbc.gridy++;

        DialogUtils.addComponent(contentPane, gbc, "", outputLatLonBands);
        gbc.gridy++;

        DialogUtils.fillPanel(contentPane, gbc);

        return contentPane;
    }

    private void enableExternalDEM(boolean flag) {
        DialogUtils.enableComponents(externalDEMFileLabel, externalDEMFile, flag);
        DialogUtils.enableComponents(externalDEMNoDataValueLabel, externalDEMNoDataValue, flag);
        externalDEMBrowseButton.setVisible(flag);
    }
}
