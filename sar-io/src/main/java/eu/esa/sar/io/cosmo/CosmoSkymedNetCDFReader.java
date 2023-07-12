/*
 * Copyright (C) 2023 by SkyWatch Space Applications Inc. http://www.skywatch.com
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
package eu.esa.sar.io.cosmo;

import com.bc.ceres.core.ProgressMonitor;
import eu.esa.sar.commons.io.SARReader;
import eu.esa.sar.io.netcdf.NcAttributeMap;
import eu.esa.sar.io.netcdf.NcRasterDim;
import eu.esa.sar.io.netcdf.NcVariableMap;
import eu.esa.sar.io.netcdf.NetCDFReader;
import eu.esa.sar.io.netcdf.NetCDFUtils;
import eu.esa.sar.io.netcdf.NetcdfConstants;
import org.apache.commons.math3.util.FastMath;
import org.esa.snap.core.dataio.IllegalFileFormatException;
import org.esa.snap.core.dataio.ProductReaderPlugIn;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.MetadataAttribute;
import org.esa.snap.core.datamodel.MetadataElement;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.TiePointGrid;
import org.esa.snap.core.util.Guardian;
import org.esa.snap.engine_utilities.datamodel.AbstractMetadata;
import org.esa.snap.engine_utilities.datamodel.Unit;
import org.esa.snap.engine_utilities.eo.Constants;
import org.esa.snap.engine_utilities.gpf.OperatorUtils;
import org.esa.snap.engine_utilities.gpf.ReaderUtils;
import ucar.ma2.Array;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.IOException;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The product reader for CosmoSkymed products.
 */
public class CosmoSkymedNetCDFReader implements CosmoSkymedReader.CosmoReader {

    private NetcdfFile netcdfFile = null;
    private Product product = null;
    private NcVariableMap variableMap = null;
    private boolean yFlipped = false;
    private boolean isComplex = false;
    private final ProductReaderPlugIn readerPlugIn;
    private final CosmoSkymedReader reader;

    private final Map<Band, Variable> bandMap = new HashMap<>(10);

    private final DateFormat standardDateFormat = ProductData.UTC.createDateFormat("yyyy-MM-dd HH:mm:ss");

    public CosmoSkymedNetCDFReader(final ProductReaderPlugIn readerPlugIn, final CosmoSkymedReader reader) {
        this.readerPlugIn = readerPlugIn;
        this.reader = reader;
    }

    private void initReader() {
        product = null;
        netcdfFile = null;
        variableMap = null;
    }

    @Override
    public Product createProduct(final Path inputPath) throws IOException {
        initReader();

        final NetcdfFile netcdfFile = NetcdfFile.open(inputPath.toFile().getAbsolutePath());
        if (netcdfFile == null) {
            close();
            throw new IllegalFileFormatException(inputPath.getFileName().toString() +
                    " Could not be interpreted by the reader.");
        }

        final Map<NcRasterDim, List<Variable>> variableListMap = NetCDFUtils.getVariableListMap(netcdfFile.getRootGroup());
        if (variableListMap.isEmpty()) {
            close();
            throw new IllegalFileFormatException("No netCDF variables found which could\n" +
                    "be interpreted as remote sensing bands.");  /*I18N*/
        }
        removeQuickLooks(variableListMap);

        final NcRasterDim rasterDim = NetCDFUtils.getBestRasterDim(variableListMap);
        final Variable[] rasterVariables = getRasterVariables(variableListMap, rasterDim);
        final Variable[] tiePointGridVariables = NetCDFUtils.getTiePointGridVariables(variableListMap, rasterVariables);

        this.netcdfFile = netcdfFile;
        variableMap = new NcVariableMap(rasterVariables);
        yFlipped = false;

        final NcAttributeMap globalAttributes = NcAttributeMap.create(this.netcdfFile);

        final String productType = NetCDFUtils.getProductType(globalAttributes, readerPlugIn.getFormatNames()[0]);
        final int rasterWidth = rasterDim.getDimX().getLength();
        final int rasterHeight = rasterDim.getDimY().getLength();

        product = new Product(inputPath.getFileName().toString(),
                productType, rasterWidth, rasterHeight, reader);
        product.setFileLocation(inputPath.toFile());

        addMetadataToProduct();
        addBandsToProduct(rasterVariables);
        addTiePointGridsToProduct(tiePointGridVariables);
        addGeoCodingToProduct(rasterDim);
        addSlantRangeToFirstPixel();
        addFirstLastLineTimes(rasterHeight);
        addSRGRCoefficients();
        addDopplerCentroidCoefficients();

        return product;
    }

    @Override
    public void close() throws IOException {
        if (netcdfFile != null) {
            variableMap.clear();
            variableMap = null;
            netcdfFile.close();
            netcdfFile = null;
        }
    }

    private static Variable[] getRasterVariables(Map<NcRasterDim, List<Variable>> variableLists,
                                                 NcRasterDim rasterDim) {
        final List<Variable> varList = variableLists.get(rasterDim);
        final List<Variable> list = new ArrayList<>(5);
        for (Variable var : varList) {
            if (!var.getShortName().equals("GIM")) {
                list.add(var);
            }
        }
        return list.toArray(new Variable[0]);
    }

    private static void removeQuickLooks(Map<NcRasterDim, List<Variable>> variableListMap) {
        final String[] excludeList = {"qlk"};
        final NcRasterDim[] keys = variableListMap.keySet().toArray(new NcRasterDim[0]);
        final List<NcRasterDim> removeList = new ArrayList<>();

        for (final NcRasterDim rasterDim : keys) {
            final List<Variable> varList = variableListMap.get(rasterDim);
            boolean found = false;
            for (Variable v : varList) {
                if (found) break;

                final String vName = v.getShortName().toLowerCase();
                for (String str : excludeList) {
                    if (vName.contains(str)) {
                        removeList.add(rasterDim);
                        found = true;
                        break;
                    }
                }
            }
        }
        for (NcRasterDim key : removeList) {
            variableListMap.remove(key);
        }
    }

    private void addMetadataToProduct() throws IOException {

        final MetadataElement origMetadataRoot = AbstractMetadata.addOriginalProductMetadata(product.getMetadataRoot());
        NetCDFUtils.addAttributes(origMetadataRoot, NetcdfConstants.GLOBAL_ATTRIBUTES_NAME,
                netcdfFile.getGlobalAttributes());

        addDeliveryNote(origMetadataRoot, product.getFileLocation().getParentFile());

        for (final Variable variable : variableMap.getAll()) {
            NetCDFUtils.addAttributes(origMetadataRoot, variable.getShortName(), variable.getAttributes());
        }

        //final Group rootGroup = netcdfFile.getRootGroup();
        //NetCDFUtils.addGroups(product.getMetadataRoot(), rootGroup);

        addAbstractedMetadataHeader(product, product.getMetadataRoot());
    }

    private void addAbstractedMetadataHeader(Product product, MetadataElement root) throws IOException {

        final MetadataElement absRoot = AbstractMetadata.addAbstractedMetadataHeader(root);

        final String defStr = AbstractMetadata.NO_METADATA_STRING;
        final int defInt = AbstractMetadata.NO_METADATA;

        final MetadataElement origMeta = AbstractMetadata.getOriginalProductMetadata(product);
        final MetadataElement globalElem = origMeta.getElement(NetcdfConstants.GLOBAL_ATTRIBUTES_NAME);

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT, product.getName());
        final String productType = globalElem.getAttributeString("Product_Type", defStr);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT_TYPE, productType);
        final String mode = globalElem.getAttributeString("Acquisition_Mode", defStr);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SPH_DESCRIPTOR, mode);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ACQUISITION_MODE, mode);

        if (mode.contains("HUGE") && productType.contains("SCS")) {
            throw new IOException("Complex " + mode + " products are not supported for Cosmo-Skymed");
        }

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.MISSION, "CSK");
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PROC_TIME,
                ReaderUtils.getTime(globalElem, "Product_Generation_UTC", standardDateFormat));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ProcessingSystemIdentifier,
                globalElem.getAttributeString("Processing_Centre", defStr));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.antenna_pointing,
                globalElem.getAttributeString("Look_Side", defStr).toLowerCase());

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ABS_ORBIT, globalElem.getAttributeInt("Orbit_Number", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PASS, globalElem.getAttributeString("Orbit_Direction"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SAMPLE_TYPE, getSampleType(productType));
        /*
        final ProductData.UTC startTime = ReaderUtils.getTime(globalElem, "Scene_Sensing_Start_UTC", timeFormat);
        final ProductData.UTC stopTime = ReaderUtils.getTime(globalElem, "Scene_Sensing_Stop_UTC", timeFormat);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_line_time, startTime);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_line_time, stopTime);
        product.setStartTime(startTime);
        product.setEndTime(stopTime);
        */
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_output_lines,
                product.getSceneRasterHeight());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_samples_per_line,
                product.getSceneRasterWidth());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.TOT_SIZE, ReaderUtils.getTotalSize(product));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.radar_frequency,
                globalElem.getAttributeDouble("Radar_Frequency", defInt) / Constants.oneMillion);
//        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.line_time_interval,
//                ReaderUtils.getLineTimeInterval(startTime, stopTime, product.getSceneRasterHeight()));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.algorithm,
                globalElem.getAttributeString("Focusing_Algorithm_ID", defStr));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.geo_ref_system,
                globalElem.getAttributeString("Ellipsoid_Designator", defStr));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_looks,
                globalElem.getAttributeDouble("Range_Processing_Number_of_Looks", 1));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_looks,
                globalElem.getAttributeDouble("Azimuth_Processing_Number_of_Looks", 1));

        if (productType.contains("GEC")) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.map_projection,
                    globalElem.getAttributeString("Projection_ID", defStr));
        }

        final String rngSpreadComp = globalElem.getAttributeString(
                "Range_Spreading_Loss_Compensation_Geometry", defStr);
        if (rngSpreadComp.equals("NONE"))
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spread_comp_flag, 0);
        else
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spread_comp_flag, 1);

        final String incAngComp = globalElem.getAttributeString(
                "Incidence_Angle_Compensation_Geometry", defStr);
        if (incAngComp.equals("NONE"))
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.inc_angle_comp_flag, 0);
        else
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.inc_angle_comp_flag, 1);

        final String antElevComp = globalElem.getAttributeString(
                "Range_Antenna_Pattern_Compensation_Geometry", defStr);
        if (antElevComp.equals("NONE"))
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ant_elev_corr_flag, 0);
        else
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ant_elev_corr_flag, 1);

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ref_inc_angle,
                globalElem.getAttributeDouble("Reference_Incidence_Angle", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ref_slant_range,
                globalElem.getAttributeDouble("Reference_Slant_Range", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ref_slant_range_exp,
                globalElem.getAttributeDouble("Reference_Slant_Range_Exponent", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.rescaling_factor,
                globalElem.getAttributeDouble("Rescaling_Factor", defInt));

        final MetadataElement s01Elem = globalElem.getElement("S01");
        if (s01Elem != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.pulse_repetition_frequency,
                    s01Elem.getAttributeDouble("PRF", defInt));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_sampling_rate,
                    s01Elem.getAttributeDouble("Sampling_Rate", defInt) / Constants.oneMillion);
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.mds1_tx_rx_polar,
                    s01Elem.getAttributeString("Polarisation", defStr));

            // add Range and Azimuth bandwidth
            final double rangeBW = s01Elem.getAttributeDouble("Range_Focusing_Bandwidth"); // Hz
            final double azimuthBW = s01Elem.getAttributeDouble("Azimuth_Focusing_Bandwidth"); // Hz

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_bandwidth, rangeBW / Constants.oneMillion);
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_bandwidth, azimuthBW);

            // Calibration constant read from Global_Metadata during calibration initialization
        } else {
            final String prefix = "S01_";
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.pulse_repetition_frequency,
                    globalElem.getAttributeDouble(prefix+"PRF", defInt));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_sampling_rate,
                    globalElem.getAttributeDouble(prefix+"Sampling_Rate", defInt) / Constants.oneMillion);
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.mds1_tx_rx_polar,
                    globalElem.getAttributeString(prefix+"Polarisation", defStr));

            // add Range and Azimuth bandwidth
            final double rangeBW = globalElem.getAttributeDouble(prefix+"Range_Focusing_Bandwidth"); // Hz
            final double azimuthBW = globalElem.getAttributeDouble(prefix+"Azimuth_Focusing_Bandwidth"); // Hz

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_bandwidth, rangeBW / Constants.oneMillion);
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_bandwidth, azimuthBW);
        }

        final MetadataElement s02Elem = globalElem.getElement("S02");
        if (s02Elem != null) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.mds2_tx_rx_polar,
                    s02Elem.getAttributeString("Polarisation", defStr));
        } else {
            final String prefix = "S02_";
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.mds2_tx_rx_polar,
                    globalElem.getAttributeString(prefix+"Polarisation", defStr));
        }

        if (isComplex) {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.srgr_flag, 0);
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_looks, 1);
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_looks, 1);
        } else {
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.srgr_flag, 1);
            if(origMeta.containsElement("MBI")) {
                final MetadataElement mbiElem = origMeta.getElement("MBI");
                AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_looks, 1);
                AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_looks, mbiElem.getAttributeDouble("Equivalent_Number_of_Looks"));
            }
        }

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.bistatic_correction_applied, 1);

        addOrbitStateVectors(absRoot, globalElem);
    }

    private void addSlantRangeToFirstPixel() {
        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);
        final MetadataElement bandElem = getBandElement(product.getBandAt(0));
        if (bandElem != null) {
            final double slantRangeTime = bandElem.getAttributeDouble("Zero_Doppler_Range_First_Time", 0); //s
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.slant_range_to_first_pixel,
                    slantRangeTime * Constants.halfLightSpeed);
        }
    }

    private void addOrbitStateVectors(final MetadataElement absRoot, final MetadataElement globalElem) {

        final MetadataElement orbitVectorListElem = absRoot.getElement(AbstractMetadata.orbit_state_vectors);
        final ProductData.UTC referenceUTC = ReaderUtils.getTime(globalElem, "Reference_UTC", standardDateFormat);

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.STATE_VECTOR_TIME, referenceUTC);

        ProductData stateVectorTimes = globalElem.getAttribute("State_Vectors_Times").getData();
        final int numPoints = stateVectorTimes.getNumElems();

        final ProductData pos = globalElem.getAttribute("ECEF_Satellite_Position").getData();
        final ProductData vel = globalElem.getAttribute("ECEF_Satellite_Velocity").getData();

        for (int i = 0; i < numPoints; i++) {
            final double stateVectorTime = stateVectorTimes.getElemDoubleAt(i);
            final ProductData.UTC orbitTime =
                    new ProductData.UTC(referenceUTC.getMJD() + stateVectorTime / Constants.secondsInDay);

            final double satellitePositionX = pos.getElemDoubleAt(3 * i);
            final double satellitePositionY = pos.getElemDoubleAt(3 * i + 1);
            final double satellitePositionZ = pos.getElemDoubleAt(3 * i + 2);
            final double satelliteVelocityX = vel.getElemDoubleAt(3 * i);
            final double satelliteVelocityY = vel.getElemDoubleAt(3 * i + 1);
            final double satelliteVelocityZ = vel.getElemDoubleAt(3 * i + 2);

            final MetadataElement orbitVectorElem = new MetadataElement(AbstractMetadata.orbit_vector + (i + 1));

            orbitVectorElem.setAttributeUTC(AbstractMetadata.orbit_vector_time, orbitTime);
            orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_x_pos, satellitePositionX);
            orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_y_pos, satellitePositionY);
            orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_z_pos, satellitePositionZ);
            orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_x_vel, satelliteVelocityX);
            orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_y_vel, satelliteVelocityY);
            orbitVectorElem.setAttributeDouble(AbstractMetadata.orbit_vector_z_vel, satelliteVelocityZ);

            orbitVectorListElem.addElement(orbitVectorElem);
        }
    }

    private void addDopplerCentroidCoefficients() {

        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);
        final MetadataElement root = AbstractMetadata.getOriginalProductMetadata(product);
        final MetadataElement globalElem = root.getElement(NetcdfConstants.GLOBAL_ATTRIBUTES_NAME);

        final MetadataElement dopplerCentroidCoefficientsElem = absRoot.getElement(AbstractMetadata.dop_coefficients);
        final MetadataElement dopplerListElem = new MetadataElement(AbstractMetadata.dop_coef_list + ".1");
        dopplerCentroidCoefficientsElem.addElement(dopplerListElem);

        final ProductData.UTC utcTime = absRoot.getAttributeUTC(AbstractMetadata.first_line_time, AbstractMetadata.NO_METADATA_UTC);
        dopplerListElem.setAttributeUTC(AbstractMetadata.dop_coef_time, utcTime);

        final double refTime = globalElem.getAttributeDouble("Range_Polynomial_Reference_Time", 0) * 1e9; // s to ns
        AbstractMetadata.addAbstractedAttribute(dopplerListElem, AbstractMetadata.slant_range_time,
                ProductData.TYPE_FLOAT64, "ns", "Slant Range Time");
        AbstractMetadata.setAttribute(dopplerListElem, AbstractMetadata.slant_range_time, refTime);

        ProductData centroid = null;
        if(globalElem.containsAttribute("Centroid_vs_Range_Time_Polynomial")) {
            centroid = globalElem.getAttribute("Centroid_vs_Range_Time_Polynomial").getData();
        } else if(globalElem.containsAttribute("S01_Doppler_Centroid_vs_Range_Time_Polynomial")) {
            centroid = globalElem.getAttribute("S01_Doppler_Centroid_vs_Range_Time_Polynomial").getData();
        } else if(globalElem.containsAttribute("S02_Doppler_Centroid_vs_Range_Time_Polynomial")) {
            centroid = globalElem.getAttribute("S02_Doppler_Centroid_vs_Range_Time_Polynomial").getData();
        }

        if(centroid != null) {
            for (int i = 0; i < centroid.getNumElems(); i++) {
                final double coefValue = centroid.getElemDoubleAt(i);
                final MetadataElement coefElem = new MetadataElement(AbstractMetadata.coefficient + '.' + (i + 1));
                dopplerListElem.addElement(coefElem);
                AbstractMetadata.addAbstractedAttribute(coefElem, AbstractMetadata.dop_coef,
                        ProductData.TYPE_FLOAT64, "", "Doppler Centroid Coefficient");
                AbstractMetadata.setAttribute(coefElem, AbstractMetadata.dop_coef, coefValue);
            }
        }
    }

    private void addFirstLastLineTimes(final int rasterHeight) {
        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);
        final MetadataElement root = AbstractMetadata.getOriginalProductMetadata(product);
        final MetadataElement globalElem = root.getElement(NetcdfConstants.GLOBAL_ATTRIBUTES_NAME);
        final MetadataElement bandElem = getBandElement(product.getBandAt(0));

        final double referenceUTC = ReaderUtils.getTime(globalElem, "Reference_UTC", standardDateFormat).getMJD(); // in days
        double firstLineTime = bandElem.getAttributeDouble("Zero_Doppler_Azimuth_First_Time", 0) / (24 * 3600); // in days
        if (firstLineTime == 0) {
            final MetadataElement s01Elem = globalElem.getElement("S01");
            if(s01Elem != null) {
                firstLineTime = s01Elem.getElement("B001").getAttributeDouble("Azimuth_First_Time") / (24 * 3600); // in days
            } else {
                firstLineTime = globalElem.getAttributeDouble("S01_B001_Azimuth_First_Time") / (24 * 3600); // in days
            }
        }
        double lastLineTime = bandElem.getAttributeDouble("Zero_Doppler_Azimuth_Last_Time", 0) / (24 * 3600); // in days
        if (lastLineTime == 0) {
            final MetadataElement s01Elem = globalElem.getElement("S01");
            if(s01Elem != null) {
                lastLineTime = s01Elem.getElement("B001").getAttributeDouble("Azimuth_Last_Time") / (24 * 3600); // in days
            } else {
                lastLineTime = globalElem.getAttributeDouble("S01_B001_Azimuth_Last_Time") / (24 * 3600); // in days
            }
        }
        double lineTimeInterval = bandElem.getAttributeDouble("Line_Time_Interval", 0); // in s
        final ProductData.UTC startTime = new ProductData.UTC(referenceUTC + firstLineTime);
        final ProductData.UTC stopTime = new ProductData.UTC(referenceUTC + lastLineTime);

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_line_time, startTime);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_line_time, stopTime);
        product.setStartTime(startTime);
        product.setEndTime(stopTime);
        if (lineTimeInterval == 0) {
            lineTimeInterval = ReaderUtils.getLineTimeInterval(startTime, stopTime, rasterHeight);
        }
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.line_time_interval, lineTimeInterval);
    }

    private void addSRGRCoefficients() {

        // For detail of ground range to slant range conversion, please see P80 in COSMO-SkyMed SAR Products Handbook.
        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);
        final MetadataElement root = AbstractMetadata.getOriginalProductMetadata(product);
        final MetadataElement globalElem = root.getElement(NetcdfConstants.GLOBAL_ATTRIBUTES_NAME);

        final MetadataAttribute attribute = globalElem.getAttribute("Ground_Projection_Polynomial_Reference_Range");
        if (attribute == null) {
            return;
        }

        final double referenceRange = attribute.getData().getElemDouble();

        final MetadataElement bandElem = getBandElement(product.getBandAt(0));
        final double rangeSpacing = bandElem.getAttributeDouble("Column_Spacing", AbstractMetadata.NO_METADATA);

        final MetadataElement srgrCoefficientsElem = absRoot.getElement(AbstractMetadata.srgr_coefficients);
        final MetadataElement srgrListElem = new MetadataElement(AbstractMetadata.srgr_coef_list);
        srgrCoefficientsElem.addElement(srgrListElem);

        final ProductData.UTC utcTime = absRoot.getAttributeUTC(AbstractMetadata.first_line_time, AbstractMetadata.NO_METADATA_UTC);
        srgrListElem.setAttributeUTC(AbstractMetadata.srgr_coef_time, utcTime);
        AbstractMetadata.addAbstractedAttribute(srgrListElem, AbstractMetadata.ground_range_origin,
                ProductData.TYPE_FLOAT64, "m", "Ground Range Origin");
        AbstractMetadata.setAttribute(srgrListElem, AbstractMetadata.ground_range_origin, 0.0);

        final int numCoeffs = 6;
        for (int i = 0; i < numCoeffs; i++) {
            double srgrCoeff = globalElem.getAttribute("Ground_to_Slant_Polynomial").getData().getElemDoubleAt(i);
            if (i == 0) {
                srgrCoeff += referenceRange;
            } else {
                srgrCoeff /= FastMath.pow(rangeSpacing, i);
            }

            final MetadataElement coefElem = new MetadataElement(AbstractMetadata.coefficient + '.' + (i + 1));
            srgrListElem.addElement(coefElem);

            AbstractMetadata.addAbstractedAttribute(coefElem, AbstractMetadata.srgr_coef,
                    ProductData.TYPE_FLOAT64, "", "SRGR Coefficient");

            AbstractMetadata.setAttribute(coefElem, AbstractMetadata.srgr_coef, srgrCoeff);
        }
    }

    private String getSampleType(final String productType) {
        if (productType.toUpperCase().contains("SCS")) {
            isComplex = true;
            return "COMPLEX";
        }
        isComplex = false;
        return "DETECTED";
    }

    private void addBandsToProduct(final Variable[] variables) {
        int cnt = 1;
        for (Variable variable : variables) {
            final int height = variable.getDimension(0).getLength();
            final int width = variable.getDimension(1).getLength();
            String cntStr = "";
            if (variables.length > 1) {
                final String polStr = getPolarization(product, cnt);
                if (polStr != null) {
                    cntStr = "_" + polStr;
                } else {
                    cntStr = "_" + cnt;
                }
                ++cnt;
            }

            if (isComplex) {     // add i and q
                final Band bandI = NetCDFUtils.createBand(variable, width, height);
                createUniqueBandName(product, bandI, "i" + cntStr);
                bandI.setUnit(Unit.REAL);
                bandI.setNoDataValue(0);
                bandI.setNoDataValueUsed(true);
                product.addBand(bandI);
                bandMap.put(bandI, variable);

                final Band bandQ = NetCDFUtils.createBand(variable, width, height);
                createUniqueBandName(product, bandQ, "q" + cntStr);
                bandQ.setUnit(Unit.IMAGINARY);
                bandQ.setNoDataValue(0);
                bandQ.setNoDataValueUsed(true);
                product.addBand(bandQ);
                bandMap.put(bandQ, variable);

                ReaderUtils.createVirtualIntensityBand(product, bandI, bandQ, cntStr);
            } else {
                final Band band = NetCDFUtils.createBand(variable, width, height);
                createUniqueBandName(product, band, "Amplitude" + cntStr);
                band.setUnit(Unit.AMPLITUDE);
                band.setNoDataValue(0);
                band.setNoDataValueUsed(true);
                product.addBand(band);
                bandMap.put(band, variable);

                SARReader.createVirtualIntensityBand(product, band, cntStr);
            }
        }
    }

    private static String getPolarization(final Product product, final int cnt) {

        final MetadataElement globalElem = AbstractMetadata.getOriginalProductMetadata(product).getElement(NetcdfConstants.GLOBAL_ATTRIBUTES_NAME);
        if (globalElem != null) {
            final MetadataElement s01Elem = globalElem.getElement("S0" + cnt);
            if (s01Elem != null) {
                final String polStr = s01Elem.getAttributeString("Polarisation", "");
                if (!polStr.isEmpty())
                    return polStr;
            } else {
                final String prefix = "S0" + cnt + '_';
                final String polStr = globalElem.getAttributeString(prefix+"Polarisation", "");
                if (!polStr.isEmpty())
                    return polStr;
            }
        }
        return null;
    }

    private static void createUniqueBandName(final Product product, final Band band, final String origName) {
        int cnt = 1;
        band.setName(origName);
        while (product.getBand(band.getName()) != null) {
            band.setName(origName + cnt);
            ++cnt;
        }
    }

    private void addTiePointGridsToProduct(final Variable[] variables) throws IOException {
//        for (Variable variable : variables) {
//            final int rank = variable.getRank();
//            final int gridWidth = variable.getDimension(rank - 1).getLength();
//            int gridHeight = variable.getDimension(rank - 2).getLength();
//            if (rank >= 3 && gridHeight <= 1)
//                gridHeight = variable.getDimension(rank - 3).getLength();
//            final TiePointGrid tpg = NetCDFUtils.createTiePointGrid(variable, gridWidth, gridHeight,
//                    product.getSceneRasterWidth(), product.getSceneRasterHeight());
//
//            product.addTiePointGrid(tpg);
//        }

        final MetadataElement bandElem = getBandElement(product.getBandAt(0));
        addIncidenceAnglesSlantRangeTime(product, bandElem);
        addGeocodingFromMetadata(product, bandElem);
    }

    private static void addIncidenceAnglesSlantRangeTime(final Product product, final MetadataElement bandElem) {
        if (bandElem == null) return;

        final int gridWidth = 11;
        final int gridHeight = 11;
        final float subSamplingX = product.getSceneRasterWidth() / (float) (gridWidth - 1);
        final float subSamplingY = product.getSceneRasterHeight() / (float) (gridHeight - 1);

        final double nearRangeAngle = bandElem.getAttributeDouble("Near_Incidence_Angle", 0);
        final double farRangeAngle = bandElem.getAttributeDouble("Far_Incidence_Angle", 0);

        final double firstRangeTime = bandElem.getAttributeDouble("Zero_Doppler_Range_First_Time", 0) * Constants.oneBillion;
        final double lastRangeTime = bandElem.getAttributeDouble("Zero_Doppler_Range_Last_Time", 0) * Constants.oneBillion;

        final float[] incidenceCorners = new float[]{(float)nearRangeAngle, (float)farRangeAngle, (float)nearRangeAngle, (float)farRangeAngle};
        final float[] slantRange = new float[]{(float)firstRangeTime, (float)lastRangeTime, (float)firstRangeTime, (float)lastRangeTime};

        final float[] fineAngles = new float[gridWidth * gridHeight];
        final float[] fineTimes = new float[gridWidth * gridHeight];

        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, incidenceCorners, fineAngles);
        ReaderUtils.createFineTiePointGrid(2, 2, gridWidth, gridHeight, slantRange, fineTimes);

        final TiePointGrid incidentAngleGrid = new TiePointGrid(OperatorUtils.TPG_INCIDENT_ANGLE, gridWidth, gridHeight, 0, 0,
                subSamplingX, subSamplingY, fineAngles);
        incidentAngleGrid.setUnit(Unit.DEGREES);
        product.addTiePointGrid(incidentAngleGrid);

        final TiePointGrid slantRangeGrid = new TiePointGrid(OperatorUtils.TPG_SLANT_RANGE_TIME, gridWidth, gridHeight, 0, 0,
                subSamplingX, subSamplingY, fineTimes);
        slantRangeGrid.setUnit(Unit.NANOSECONDS);
        product.addTiePointGrid(slantRangeGrid);
    }

    private MetadataElement getBandElement(final Band band) {
        final MetadataElement root = AbstractMetadata.getOriginalProductMetadata(product);
        final Variable variable = bandMap.get(band);
        final String varName = variable.getShortName();
        MetadataElement bandElem = null;
        for (MetadataElement elem : root.getElements()) {
            if (elem.getName().equalsIgnoreCase(varName)) {
                bandElem = elem;
                break;
            }
        }
        return bandElem;
    }

    private static void addGeocodingFromMetadata(final Product product, final MetadataElement bandElem) {
        if (bandElem == null) return;

        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);

        try {
            String str = bandElem.getAttributeString("Top_Left_Geodetic_Coordinates");
            final float latUL = Float.parseFloat(str.substring(0, str.indexOf(',')));
            final float lonUL = Float.parseFloat(str.substring(str.indexOf(',') + 1, str.lastIndexOf(',')));
            str = bandElem.getAttributeString("Top_Right_Geodetic_Coordinates");
            final float latUR = Float.parseFloat(str.substring(0, str.indexOf(',')));
            final float lonUR = Float.parseFloat(str.substring(str.indexOf(',') + 1, str.lastIndexOf(',')));
            str = bandElem.getAttributeString("Bottom_Left_Geodetic_Coordinates");
            final float latLL = Float.parseFloat(str.substring(0, str.indexOf(',')));
            final float lonLL = Float.parseFloat(str.substring(str.indexOf(',') + 1, str.lastIndexOf(',')));
            str = bandElem.getAttributeString("Bottom_Right_Geodetic_Coordinates");
            final float latLR = Float.parseFloat(str.substring(0, str.indexOf(',')));
            final float lonLR = Float.parseFloat(str.substring(str.indexOf(',') + 1, str.lastIndexOf(',')));

            absRoot.setAttributeDouble(AbstractMetadata.first_near_lat, latUL);
            absRoot.setAttributeDouble(AbstractMetadata.first_near_long, lonUL);
            absRoot.setAttributeDouble(AbstractMetadata.first_far_lat, latUR);
            absRoot.setAttributeDouble(AbstractMetadata.first_far_long, lonUR);
            absRoot.setAttributeDouble(AbstractMetadata.last_near_lat, latLL);
            absRoot.setAttributeDouble(AbstractMetadata.last_near_long, lonLL);
            absRoot.setAttributeDouble(AbstractMetadata.last_far_lat, latLR);
            absRoot.setAttributeDouble(AbstractMetadata.last_far_long, lonLR);

            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spacing,
                    bandElem.getAttributeDouble("Column_Spacing", AbstractMetadata.NO_METADATA));
            AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_spacing,
                    bandElem.getAttributeDouble("Line_Spacing", AbstractMetadata.NO_METADATA));

            final float[] latCorners = new float[]{latUL, latUR, latLL, latLR};
            final float[] lonCorners = new float[]{lonUL, lonUR, lonLL, lonLR};

            ReaderUtils.addGeoCoding(product, latCorners, lonCorners);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            // continue
        }
    }

    private void addGeoCodingToProduct(final NcRasterDim rasterDim) throws IOException {
        if (product.getSceneGeoCoding() == null) {
            NetCDFReader.setTiePointGeoCoding(product);
        }
        if (product.getSceneGeoCoding() == null) {
            NetCDFReader.setPixelGeoCoding(product);
        }
        if (product.getSceneGeoCoding() == null) {
            yFlipped = NetCDFReader.setMapGeoCoding(rasterDim, product, netcdfFile, yFlipped);
        }
    }

    @Override
    public void readBandRasterDataImpl(int sourceOffsetX, int sourceOffsetY, int sourceWidth, int sourceHeight,
                                          int sourceStepX, int sourceStepY, Band destBand, int destOffsetX,
                                          int destOffsetY, int destWidth, int destHeight, ProductData destBuffer,
                                          ProgressMonitor pm) throws IOException {

        Guardian.assertTrue("sourceStepX == 1 && sourceStepY == 1", sourceStepX == 1 && sourceStepY == 1);
        Guardian.assertTrue("sourceWidth == destWidth", sourceWidth == destWidth);
        Guardian.assertTrue("sourceHeight == destHeight", sourceHeight == destHeight);

        final int sceneHeight = product.getSceneRasterHeight();
        final int sceneWidth = product.getSceneRasterWidth();
        destHeight = Math.min(destHeight, sceneHeight-sourceOffsetY);
        destWidth = Math.min(destWidth, sceneWidth-destOffsetX);

        final int y0 = yFlipped ? (sceneHeight - 1) - sourceOffsetY : sourceOffsetY;

        final Variable variable = bandMap.get(destBand);
        final int rank = variable.getRank();
        final int[] origin = new int[rank];
        final int[] shape = new int[rank];
        for (int i = 0; i < rank; i++) {
            shape[i] = 1;
            origin[i] = 0;
        }
        shape[0] = 1;
        shape[1] = destWidth;
        origin[1] = sourceOffsetX;
        if (isComplex && destBand.getUnit().equals(Unit.IMAGINARY)) {
            origin[2] = 1;
        }

        pm.beginTask("Reading data from band " + destBand.getName(), destHeight);
        try {
            for (int y = 0; y < destHeight; y++) {
                origin[0] = yFlipped ? y0 - y : y0 + y;
                final Array array;
                synchronized (netcdfFile) {
                    array = variable.read(origin, shape);
                }
                System.arraycopy(array.getStorage(), 0, destBuffer.getElems(), y * destWidth, destWidth);
                pm.worked(1);
            }
        } catch (InvalidRangeException e) {
            final IOException ioException = new IOException(e.getMessage());
            ioException.initCause(e);
            throw ioException;
        } finally {
            pm.done();
        }
    }

}
