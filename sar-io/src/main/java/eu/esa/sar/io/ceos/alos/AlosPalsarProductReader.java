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
package eu.esa.sar.io.ceos.alos;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.VirtualDir;
import eu.esa.sar.io.ceos.CEOSProductDirectory;
import eu.esa.sar.io.ceos.CEOSProductReader;
import org.esa.snap.core.dataio.DecodeQualification;
import org.esa.snap.core.dataio.ProductReaderPlugIn;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.engine_utilities.datamodel.AbstractMetadata;
import org.esa.snap.engine_utilities.datamodel.Unit;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The product reader for AlosPalsar products.
 */
public class AlosPalsarProductReader extends CEOSProductReader {

    /**
     * Constructs a new abstract product reader.
     *
     * @param readerPlugIn the reader plug-in which created this reader, can be <code>null</code> for internal reader
     *                     implementations
     */
    public AlosPalsarProductReader(final ProductReaderPlugIn readerPlugIn) {
        super(readerPlugIn);
    }

    @Override
    protected CEOSProductDirectory createProductDirectory(final VirtualDir productDir) {
        return new AlosPalsarProductDirectory(productDir);
    }

    DecodeQualification checkProductQualification(final Path path) {

        try {
            dataDir = createProductDirectory(createProductDir(path));

            final AlosPalsarProductDirectory alosDataDir = (AlosPalsarProductDirectory) this.dataDir;
            if (alosDataDir.isALOS()) {
                return DecodeQualification.INTENDED;
            }
            return DecodeQualification.UNABLE;

        } catch (Exception e) {
            return DecodeQualification.UNABLE;
        }
    }

    public void readTiePointGridRasterData(final TiePointGrid tpg,
                                           final int destOffsetX, final int destOffsetY,
                                           final int destWidth, final int destHeight,
                                           final ProductData destBuffer, final ProgressMonitor pm)
            throws IOException {
        final AlosPalsarProductDirectory alosDataDir = (AlosPalsarProductDirectory) this.dataDir;
        Rectangle destRect = new Rectangle(destOffsetX, destOffsetY, destWidth, destHeight);
        alosDataDir.readTiePointGridRasterData(tpg, destRect, destBuffer, pm);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readBandRasterDataImpl(int sourceOffsetX, int sourceOffsetY, int sourceWidth, int sourceHeight,
                                          int sourceStepX, int sourceStepY, Band destBand, int destOffsetX,
                                          int destOffsetY, int destWidth, int destHeight, ProductData destBuffer,
                                          ProgressMonitor pm) throws IOException {
        try {
            final Product product = dataDir.createProduct();
            final MetadataElement absMeta = AbstractMetadata.getAbstractedMetadata(product);
            final boolean isAscending = absMeta.getAttributeString(AbstractMetadata.PASS).equals("ASCENDING");
            final boolean isESAProduct = absMeta.getAttributeString(AbstractMetadata.ProcessingSystemIdentifier).contains("ESA");
            final AlosPalsarProductDirectory dataDir = (AlosPalsarProductDirectory) this.dataDir;
            final boolean flipToSARGeometry = !isESAProduct && isAscending && dataDir.getProductLevel() == AlosPalsarConstants.LEVEL1_5;
            final AlosPalsarImageFile imageFile = (AlosPalsarImageFile) dataDir.getImageFile(destBand);
            final int imageHeight = imageFile.getRasterHeight();

            if (dataDir.isSLC()) {
                boolean oneOf2 = destBand.getUnit().equals(Unit.REAL) || !destBand.getName().startsWith("q");

                if (dataDir.getProductLevel() == AlosPalsarConstants.LEVEL1_0) {
                    imageFile.readBandRasterDataSLCByte(sourceOffsetX, sourceOffsetY,
                            sourceWidth, sourceHeight,
                            sourceStepX, sourceStepY,
                            destWidth,
                            destBuffer, oneOf2, pm);
                } else {
                    imageFile.readBandRasterDataSLCFloat(sourceOffsetX, sourceOffsetY,
                            sourceWidth, sourceHeight,
                            sourceStepX, sourceStepY,
                            destWidth,
                            destBuffer, oneOf2, pm);
                }
            } else {
                if (flipToSARGeometry) {
                    imageFile.readBandRasterDataShort(sourceOffsetX, imageHeight - sourceOffsetY - destHeight,
                            sourceWidth, sourceHeight,
                            sourceStepX, sourceStepY,
                            destWidth, destBuffer, flipToSARGeometry, pm);
                } else {
                    imageFile.readBandRasterDataShort(sourceOffsetX, sourceOffsetY,
                            sourceWidth, sourceHeight,
                            sourceStepX, sourceStepY,
                            destWidth,
                            destBuffer, pm);
                }
            }

        } catch (Exception e) {
            handleReaderException(e);
        }
    }
}
