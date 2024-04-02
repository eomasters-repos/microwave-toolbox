/*
 * Copyright (C) 2014 by Array Systems Computing Inc. http://www.array.ca
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
package eu.esa.sar.sentinel1.gpf.experimental;

import com.bc.ceres.core.ProgressMonitor;
import eu.esa.sar.commons.Sentinel1Utils;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.engine_utilities.eo.Constants;
import org.esa.snap.engine_utilities.gpf.InputProductValidator;
import org.esa.snap.engine_utilities.gpf.OperatorUtils;
import org.esa.snap.engine_utilities.gpf.TileIndex;

import java.awt.*;
import java.util.Map;

/**
 * Compute deramp and demodulation phases.
 */
@OperatorMetadata(alias = "Deramp-Demod-Phase",
        category = "Radar/Coregistration/S-1 TOPS Coregistration",
        authors = "Jun Lu, Luis Veci",
        version = "1.0",
        copyright = "Copyright (C) 2014 by Array Systems Computing Inc.",
        description = "Compute Deramp and Demodulation Phases",
        internal = true)
public final class DerampDemodPhaseOp extends Operator {

    @SourceProduct(alias = "source")
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    private Sentinel1Utils su = null;
    private Sentinel1Utils.SubSwathInfo[] subSwath = null;

    private int subSwathIndex = 0;
    private int sourceImageWidth = 0;
    private int sourceImageHeight = 0;

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public DerampDemodPhaseOp() {
    }

    /**
     * Compute range dependent Doppler rate Kt(r) for given burst.
     * @return The Doppler rate array.
     */
    public float[] computeDopplerRate(final int burstIndex) throws Exception {
        float[] kt = new float[sourceImageWidth];
        for (int x = 0; x < sourceImageWidth; x++) {
            kt[x] = (float)subSwath[0].dopplerRate[burstIndex][x];
        }
        return kt;
    }

    /**
     * Compute range dependent Doppler centroid fDC(r) for given burst.
     * @return The Doppler centroid array.
     */
    public float[] computeDopplerCentroid(final int burstIndex) throws Exception {
        float[] fdc = new float[sourceImageWidth];
        for (int x = 0; x < sourceImageWidth; x++) {
            fdc[x] = (float)subSwath[0].dopplerCentroid[burstIndex][x];
        }
        return fdc;
    }

    /**
     * Compute slant range.
     * @return The slant range array.
     */
    public float[] computeSlantRange() throws Exception {
        float[] slr = new float[sourceImageWidth];
        for (int x = 0; x < sourceImageWidth; x++) {
            slr[x] = (float)(subSwath[0].slrTimeToFirstPixel * Constants.lightSpeed +
                    x * subSwath[subSwathIndex - 1].rangePixelSpacing);

        }
        return slr;
    }

    /**
     * Compute reference time.
     * @return The reference time array.
     */
    public float[] computeReferenceTime(final int burstIndex) throws Exception {
        float[] tref = new float[sourceImageWidth];
        for (int x = 0; x < sourceImageWidth; x++) {
            tref[x] = (float)subSwath[0].referenceTime[burstIndex][x];
        }
        return tref;
    }


    /**
     * Initializes this operator and sets the one and only target product.
     * <p>The target product can be either defined by a field of type {@link Product} annotated with the
     * {@link TargetProduct TargetProduct} annotation or
     * by calling {@link #setTargetProduct} method.</p>
     * <p>The framework calls this method after it has created this operator.
     * Any client code that must be performed before computation of tile data
     * should be placed here.</p>
     *
     * @throws OperatorException If an error occurs during operator initialisation.
     * @see #getTargetProduct()
     */
    @Override
    public void initialize() throws OperatorException {

        try {
            final InputProductValidator validator = new InputProductValidator(sourceProduct);
            validator.checkIfSARProduct();
            validator.checkIfSentinel1Product();
            validator.checkIfSLC();

            su = new Sentinel1Utils(sourceProduct);
            su.computeDopplerRate();
            su.computeReferenceTime();
            subSwath = su.getSubSwath();

            final String[] subSwathNames = su.getSubSwathNames();
            if (subSwathNames.length != 1) {
                throw new OperatorException("Split product is expected.");
            }

            subSwathIndex = 1; // subSwathIndex is always 1 because of split product

            final String[] polarizations = su.getPolarizations();
            if (polarizations.length != 1) {
                throw new OperatorException("Split product with one polarization is expected.");
            }

            createTargetProduct();

        } catch (Throwable e) {
            throw new OperatorException(e.getMessage());
        }
    }

    /**
     * Create target product.
     */
    private void createTargetProduct() {

        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(
                sourceProduct.getName(),
                sourceProduct.getProductType(),
                sourceImageWidth,
                sourceImageHeight);

        final Band derampPhaseBand = new Band(
                "derampPhase",
                ProductData.TYPE_FLOAT32,
                sourceImageWidth,
                sourceImageHeight);

        derampPhaseBand.setUnit("radian");
        targetProduct.addBand(derampPhaseBand);

        final Band demodPhaseBand = new Band(
                "demodPhase",
                ProductData.TYPE_FLOAT32,
                sourceImageWidth,
                sourceImageHeight);

        demodPhaseBand.setUnit("radian");
        targetProduct.addBand(demodPhaseBand);
    }

    /**
     * Called by the framework in order to compute a tile for the given target band.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetTileMap   The target tiles associated with all target bands to be computed.
     * @param targetRectangle The rectangle of target tile.
     * @param pm              A progress monitor which should be used to determine computation cancelation requests.
     * @throws OperatorException
     *          If an error occurs during computation of the target raster.
     */
    @Override
     public void computeTileStack(Map<Band, Tile> targetTileMap, Rectangle targetRectangle, ProgressMonitor pm)
             throws OperatorException {

        try {
            final int tx0 = targetRectangle.x;
            final int ty0 = targetRectangle.y;
            final int tw = targetRectangle.width;
            final int th = targetRectangle.height;
            final int tyMax = ty0 + th;
            //System.out.println("tx0 = " + tx0 + ", ty0 = " + ty0 + ", tw = " + tw + ", th = " + th);

            for (int burstIndex = 0; burstIndex < subSwath[subSwathIndex - 1].numOfBursts; burstIndex++) {
                final int firstLineIdx = burstIndex*subSwath[subSwathIndex - 1].linesPerBurst;
                final int lastLineIdx = firstLineIdx + subSwath[subSwathIndex - 1].linesPerBurst - 1;

                if (tyMax <= firstLineIdx || ty0 > lastLineIdx) {
                    continue;
                }

                final int ntx0 = tx0;
                final int ntw = tw;
                final int nty0 = Math.max(ty0, firstLineIdx);
                final int ntyMax = Math.min(tyMax, lastLineIdx + 1);
                final int nth = ntyMax - nty0;
                final Rectangle subTargetRectangle = new Rectangle(ntx0, nty0, ntw, nth);
                //System.out.println("burstIndex = " + burstIndex + ": ntx0 = " + ntx0 + ", nty0 = " + nty0 + ", ntw = " + ntw + ", nth = " + nth);

                computeDerampDemodPhase(
                        subSwathIndex, burstIndex, subTargetRectangle, targetTileMap, pm);
            }

        } catch (Throwable e) {
            throw new OperatorException(e.getMessage());
        }
    }

    private void computeDerampDemodPhase(final int subSwathIndex, final int burstIndex, final Rectangle rectangle,
                                         final Map<Band, Tile> targetTileMap, ProgressMonitor pm)
            throws Exception {

        try {
            final int x0 = rectangle.x;
            final int y0 = rectangle.y;
            final int w = rectangle.width;
            final int h = rectangle.height;
            final int xMax = x0 + w;
            final int yMax = y0 + h;

            final double[][] derampPhase = su.computeDerampPhase(subSwath, subSwathIndex, burstIndex, rectangle);
            final double[][] demodPhase = su.computeDemodPhase(subSwath, subSwathIndex, burstIndex, rectangle);

            final Band derampPhaseBand = targetProduct.getBand("derampPhase");
            final Band demodPhaseBand = targetProduct.getBand("demodPhase");
            final Tile tgtTileDerampPhase = targetTileMap.get(derampPhaseBand);
            final Tile tgtTileDemodPhase = targetTileMap.get(demodPhaseBand);
            final ProductData tgtBufferDerampPhase = tgtTileDerampPhase.getDataBuffer();
            final ProductData tgtBufferDemodPhase = tgtTileDemodPhase.getDataBuffer();
            final TileIndex tgtIndex = new TileIndex(tgtTileDerampPhase);

            for (int y = y0; y < yMax; y++) {
                final int yy = y - y0;
                tgtIndex.calculateStride(y);
                for (int x = x0; x < xMax; x++) {
                    final int xx = x - x0;
                    final int idx = tgtIndex.getIndex(x);
                    tgtBufferDerampPhase.setElemFloatAt(idx, (float)derampPhase[yy][xx]);
                    tgtBufferDemodPhase.setElemFloatAt(idx, (float)demodPhase[yy][xx]);
                }
            }
        } catch (Throwable e) {
            OperatorUtils.catchOperatorException("computeDerampDemodPhase", e);
        }
    }


    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.snap.core.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     *
     * @see OperatorSpi#createOperator()
     * @see OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(DerampDemodPhaseOp.class);
        }
    }
}
