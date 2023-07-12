/*
 * Copyright (C) 2015 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package eu.esa.sar.analysis.rcp.toolviews.timeseries.actions;

import eu.esa.sar.analysis.rcp.toolviews.timeseries.GraphData;
import eu.esa.sar.analysis.rcp.toolviews.timeseries.TimeSeriesSettings;
import eu.esa.sar.analysis.rcp.toolviews.timeseries.TimeSeriesToolView;
import org.esa.snap.graphbuilder.rcp.utils.DialogUtils;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.ui.ModelessDialog;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

/**
 * Parameter settings for the time series
 */
public class TimeSeriesSettingsDlg extends ModelessDialog {

    private final JCheckBox showGridCB = new JCheckBox("Show Grid");
    private final JCheckBox showLegendCB = new JCheckBox("Show Legend");
    private final GridBagConstraints glGbc = DialogUtils.createGridBagConstraints();
    private final JPanel graphListPanel = new JPanel(new GridBagLayout());

    private final List<GraphProductSetPanel> graphList = new ArrayList<>(2);

    private final TimeSeriesToolView view;
    private final TimeSeriesSettings settings;

    public TimeSeriesSettingsDlg(final Window parent, final String title, final String helpID,
                                 final TimeSeriesSettings settings, final TimeSeriesToolView view) {
        super(parent, title, ModelessDialog.ID_APPLY_CLOSE, helpID);
        this.settings = settings;
        this.view = view;
        this.getJDialog().setResizable(true);

        final List<GraphData> graphDataLists = settings.getGraphDataList();
        int cnt = 1;
        for (GraphData graphData : graphDataLists) {
            final boolean addDeleteButton = cnt > 1;
            final GraphProductSetPanel productListPanel = new GraphProductSetPanel(SnapApp.getDefault().getAppContext(),
                                                                                   this, graphData, addDeleteButton);
            productListPanel.setProductFileList(graphData.getFileList());
            graphList.add(productListPanel);
            ++cnt;
        }

        initUI();

        showGridCB.setSelected(settings.isShowingGrid());
        showLegendCB.setSelected(settings.isShowingLegend());
    }

    private void initUI() {
        final JPanel content = new JPanel(new BorderLayout());
        final JPanel optionsPanel = new JPanel();

        final AbstractButton addGraphBtn = DialogUtils.createButton("addGraphBtn", "Add Graph", null, content, DialogUtils.ButtonStyle.Text);
        final TimeSeriesSettingsDlg settingsDlg = this;
        addGraphBtn.addActionListener(e -> {
            final int cnt = graphList.size() + 1;
            final GraphProductSetPanel productListPanel = new GraphProductSetPanel(SnapApp.getDefault().getAppContext(),
                                                                                   settingsDlg, new GraphData("Graph " + cnt), true);
            graphList.add(productListPanel);
            graphListPanel.add(productListPanel, glGbc);

            glGbc.gridy++;
            graphListPanel.revalidate();
        });

        //optionsPanel.add(addGraphBtn);
        optionsPanel.add(showGridCB);
        optionsPanel.add(showLegendCB);

        final JScrollPane scrollPane = new JScrollPane(graphListPanel);

        content.add(optionsPanel, BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);

        for (GraphProductSetPanel productListPanel : graphList) {
            graphListPanel.add(productListPanel, glGbc);
            glGbc.gridy++;
        }

        setContent(content);
    }

    public TimeSeriesToolView getToolView() {
        return view;
    }

    public void removeGraphPanel(final GraphProductSetPanel productListPanel) {
        graphList.remove(productListPanel);
        graphListPanel.remove(productListPanel);
        graphListPanel.revalidate();
    }

    public List<GraphData> getProductLists() {
        final List<GraphData> graphDataList = new ArrayList<>(graphList.size());
        for (GraphProductSetPanel panel : graphList) {
            graphDataList.add(new GraphData(panel.getTitle(), panel.getFileList(), panel.getColor()));
        }
        return graphDataList;
    }

    @Override
    public void onApply() {
        settings.setGridShown(showGridCB.isSelected());
        settings.setLegendShown(showLegendCB.isSelected());
        settings.setGraphDataList(getProductLists());
        view.refresh();
    }

    @Override
    public void onClose() {
        onApply();
        super.onClose();
    }
}
