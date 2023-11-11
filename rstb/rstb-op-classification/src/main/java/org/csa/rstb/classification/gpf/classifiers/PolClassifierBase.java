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
package org.csa.rstb.classification.gpf.classifiers;

import org.csa.rstb.classification.gpf.PolarimetricClassificationOp;
import eu.esa.sar.commons.polsar.PolBandUtils;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.IndexCoding;
import org.esa.snap.engine_utilities.eo.Constants;

import java.awt.*;
import java.util.Map;

/**
 * Base class for polarimetric classifiers
 */
public abstract class PolClassifierBase {

    public final static int NODATACLASS = 0;
    protected final PolBandUtils.MATRIX sourceProductType;
    protected final int srcWidth;
    protected final int srcHeight;
    protected final int windowSizeX;
    protected final int windowSizeY;
    protected final int halfWindowSizeX;
    protected final int halfWindowSizeY;
    protected final Map<Band, PolBandUtils.PolSourceBand> bandMap;
    protected final PolarimetricClassificationOp op;

    protected PolClassifierBase(final PolBandUtils.MATRIX srcProductType,
                                final int srcWidth, final int srcHeight, final int windowSizeX, final int windowSizeY,
                                final Map<Band, PolBandUtils.PolSourceBand> bandMap,
                                final PolarimetricClassificationOp op) {
        this.sourceProductType = srcProductType;
        this.srcWidth = srcWidth;
        this.srcHeight = srcHeight;
        this.windowSizeX = windowSizeX;
        this.windowSizeY = windowSizeY;
        this.halfWindowSizeX = windowSizeX / 2;
        this.halfWindowSizeY = windowSizeY / 2;
        this.bandMap = bandMap;
        this.op = op;
    }

    public boolean canProcessStacks() {
        return true;
    }

    /**
     * returns the number of classes
     *
     * @return num classes
     */
    public abstract int getNumClasses();

    /**
     * Get source tile rectangle.
     *
     * @param tx0 X coordinate for the upper left corner pixel in the target tile.
     * @param ty0 Y coordinate for the upper left corner pixel in the target tile.
     * @param tw  The target tile width.
     * @param th  The target tile height.
     * @return The source tile rectangle.
     */
    protected Rectangle getSourceRectangle(final int tx0, final int ty0, final int tw, final int th) {

        final int x0 = Math.max(0, tx0 - halfWindowSizeX);
        final int y0 = Math.max(0, ty0 - halfWindowSizeY);
        final int xMax = Math.min(tx0 + tw - 1 + halfWindowSizeX, srcWidth);
        final int yMax = Math.min(ty0 + th - 1 + halfWindowSizeY, srcHeight);
        final int w = xMax - x0 + 1;
        final int h = yMax - y0 + 1;
        return new Rectangle(x0, y0, w, h);
    }

    public static Rectangle getSourceRectangle(final int tx0, final int ty0, final int tw, final int th,
                                               final int windowSizeX, final int windowSizeY,
                                               final int srcWidth, final int srcHeight) {

        final int halfWindowSizeX = windowSizeX / 2;
        final int halfWindowSizeY = windowSizeY / 2;
        final int x0 = Math.max(0, tx0 - halfWindowSizeX);
        final int y0 = Math.max(0, ty0 - halfWindowSizeY);
        final int xMax = Math.min(tx0 + tw - 1 + halfWindowSizeX, srcWidth - 1);
        final int yMax = Math.min(ty0 + th - 1 + halfWindowSizeY, srcHeight - 1);
        final int w = xMax - x0 + 1;
        final int h = yMax - y0 + 1;
        return new Rectangle(x0, y0, w, h);
    }

    protected static void computeSummationOfC2(final int zoneIdx, final double[][] Cr, final double[][] Ci,
                                               double[][][] sumRe, double[][][] sumIm) {

        for (int i = 0; i < 2; ++i) {
            for (int j = 0; j < 2; ++j) {
                sumRe[zoneIdx - 1][i][j] += Cr[i][j];
                sumIm[zoneIdx - 1][i][j] += Ci[i][j];
            }
        }
    }

    protected static void computeSummationOfT3(final int zoneIdx, final double[][] Tr, final double[][] Ti,
                                               double[][][] sumRe, double[][][] sumIm) {
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                sumRe[zoneIdx - 1][i][j] += Tr[i][j];
                sumIm[zoneIdx - 1][i][j] += Ti[i][j];
            }
        }
    }

    /**
     * Compute determinant of a 2x2 Hermitian matrix
     *
     * @param Cr Real part of the 2x2 Hermitian matrix
     * @param Ci Imaginary part of the 2x2 Hermitian matrix
     * @return The determinant
     */
    private static double determinantCmplxMatrix2(final double[][] Cr, final double[][] Ci) {

        double detR = Cr[0][0] * Cr[1][1] - Cr[0][1] * Cr[0][1] - Ci[0][1] * Ci[0][1];

        if (detR < Constants.EPS) {
            detR = Constants.EPS;
        }
        return detR;
    }

    /**
     * Compute determinant of a 3x3 Hermitian matrix
     *
     * @param Tr Real part of the 3x3 Hermitian matrix
     * @param Ti Imaginary part of the 3x3 Hermitian matrix
     * @return The determinant
     */
    private static double determinantCmplxMatrix3(final double[][] Tr, final double[][] Ti) {

        final double cof00R = Tr[1][1] * Tr[2][2] - Ti[1][1] * Ti[2][2] - Tr[1][2] * Tr[2][1] + Ti[1][2] * Ti[2][1];
        final double cof00I = Tr[1][1] * Ti[2][2] + Ti[1][1] * Tr[2][2] - Tr[1][2] * Ti[2][1] - Ti[1][2] * Tr[2][1];

        final double cof01R = Tr[1][0] * Tr[2][2] - Ti[1][0] * Ti[2][2] - Tr[1][2] * Tr[2][0] + Ti[1][2] * Ti[2][0];
        final double cof01I = Tr[1][0] * Ti[2][2] + Ti[1][0] * Tr[2][2] - Tr[1][2] * Ti[2][0] - Ti[1][2] * Tr[2][0];

        final double cof02R = Tr[1][0] * Tr[2][1] - Ti[1][0] * Ti[2][1] - Tr[1][1] * Tr[2][0] + Ti[1][1] * Ti[2][0];
        final double cof02I = Tr[1][0] * Ti[2][1] + Ti[1][0] * Tr[2][1] - Tr[1][1] * Ti[2][0] - Ti[1][1] * Tr[2][0];

        final double detR = Tr[0][0] * cof00R - Ti[0][0] * cof00I - Tr[0][1] * cof01R +
                Ti[0][1] * cof01I + Tr[0][2] * cof02R + Ti[0][2] * cof02I;

        final double detI = Tr[0][0] * cof00I + Ti[0][0] * cof00R - Tr[0][1] * cof01I -
                Ti[0][1] * cof01R + Tr[0][2] * cof02I + Ti[0][2] * cof02R;

        double det = Math.sqrt(detR * detR + detI * detI);
        if (det < Constants.EPS) {
            det = Constants.EPS;
        }
        return det;
    }

    /**
     * Compute inverse of a 2x2 Hermitian matrix
     *
     * @param Cr  Real part of the 2x2 Hermitian matrix
     * @param Ci  Imaginary part of the 2x2 Hermitian matrix
     * @param iCr Real part of the inversed 2x2 Hermitian matrix
     * @param iCi Imaginary part of the inversed 2x2 Hermitian matrix
     */
    private static void inverseCmplxMatrix2(final double[][] Cr, final double[][] Ci, double[][] iCr, double[][] iCi) {

        iCr[0][0] = Cr[1][1];
        iCi[0][0] = 0.0;

        iCr[0][1] = -Cr[0][1];
        iCi[0][1] = -Ci[0][1];

        iCr[1][0] = -Cr[0][1];
        iCi[1][0] = Ci[0][1];

        iCr[1][1] = Cr[0][0];
        iCi[1][1] = 0.0;

        final double det = determinantCmplxMatrix2(Cr, Ci);

        for (int i = 0; i < 2; ++i) {
            for (int j = 0; j < 2; ++j) {
                iCr[i][j] /= det;
                iCi[i][j] /= det;
            }
        }
    }

    /**
     * Compute inverse of a 3x3 Hermitian matrix
     *
     * @param Tr  Real part of the 3x3 Hermitian matrix
     * @param Ti  Imaginary part of the 3x3 Hermitian matrix
     * @param iTr Real part of the inversed 3x3 Hermitian matrix
     * @param iTi Imaginary part of the inversed 3x3 Hermitian matrix
     */
    private static void inverseCmplxMatrix3(final double[][] Tr, final double[][] Ti, double[][] iTr, double[][] iTi) {

        iTr[0][0] = Tr[1][1] * Tr[2][2] - Ti[1][1] * Ti[2][2] - Tr[1][2] * Tr[2][1] + Ti[1][2] * Ti[2][1];
        iTi[0][0] = Tr[1][1] * Ti[2][2] + Ti[1][1] * Tr[2][2] - Tr[1][2] * Ti[2][1] - Ti[1][2] * Tr[2][1];

        iTr[0][1] = Tr[2][1] * Tr[0][2] - Ti[2][1] * Ti[0][2] - Tr[2][2] * Tr[0][1] + Ti[2][2] * Ti[0][1];
        iTi[0][1] = Tr[2][1] * Ti[0][2] + Ti[2][1] * Tr[0][2] - Tr[2][2] * Ti[0][1] - Ti[2][2] * Tr[0][1];

        iTr[0][2] = Tr[0][1] * Tr[1][2] - Ti[0][1] * Ti[1][2] - Tr[1][1] * Tr[0][2] + Ti[1][1] * Ti[0][2];
        iTi[0][2] = Tr[0][1] * Ti[1][2] + Ti[0][1] * Tr[1][2] - Tr[1][1] * Ti[0][2] - Ti[1][1] * Tr[0][2];

        iTr[1][0] = Tr[2][0] * Tr[1][2] - Ti[2][0] * Ti[1][2] - Tr[1][0] * Tr[2][2] + Ti[1][0] * Ti[2][2];
        iTi[1][0] = Tr[2][0] * Ti[1][2] + Ti[2][0] * Tr[1][2] - Tr[1][0] * Ti[2][2] - Ti[1][0] * Tr[2][2];

        iTr[1][1] = Tr[0][0] * Tr[2][2] - Ti[0][0] * Ti[2][2] - Tr[2][0] * Tr[0][2] + Ti[2][0] * Ti[0][2];
        iTi[1][1] = Tr[0][0] * Ti[2][2] + Ti[0][0] * Tr[2][2] - Tr[2][0] * Ti[0][2] - Ti[2][0] * Tr[0][2];

        iTr[1][2] = Tr[1][0] * Tr[0][2] - Ti[1][0] * Ti[0][2] - Tr[0][0] * Tr[1][2] + Ti[0][0] * Ti[1][2];
        iTi[1][2] = Tr[1][0] * Ti[0][2] + Ti[1][0] * Tr[0][2] - Tr[0][0] * Ti[1][2] - Ti[0][0] * Tr[1][2];

        iTr[2][0] = Tr[1][0] * Tr[2][1] - Ti[1][0] * Ti[2][1] - Tr[2][0] * Tr[1][1] + Ti[2][0] * Ti[1][1];
        iTi[2][0] = Tr[1][0] * Ti[2][1] + Ti[1][0] * Tr[2][1] - Tr[2][0] * Ti[1][1] - Ti[2][0] * Tr[1][1];

        iTr[2][1] = Tr[2][0] * Tr[0][1] - Ti[2][0] * Ti[0][1] - Tr[0][0] * Tr[2][1] + Ti[0][0] * Ti[2][1];
        iTi[2][1] = Tr[2][0] * Ti[0][1] + Ti[2][0] * Tr[0][1] - Tr[0][0] * Ti[2][1] - Ti[0][0] * Tr[2][1];

        iTr[2][2] = Tr[0][0] * Tr[1][1] - Ti[0][0] * Ti[1][1] - Tr[1][0] * Tr[0][1] + Ti[1][0] * Ti[0][1];
        iTi[2][2] = Tr[0][0] * Ti[1][1] + Ti[0][0] * Tr[1][1] - Tr[1][0] * Ti[0][1] - Ti[1][0] * Tr[0][1];

        final double detR = Tr[0][0] * iTr[0][0] - Ti[0][0] * iTi[0][0] + Tr[1][0] * iTr[0][1] -
                Ti[1][0] * iTi[0][1] + Tr[2][0] * iTr[0][2] - Ti[2][0] * iTi[0][2];

        final double detI = Tr[0][0] * iTi[0][0] + Ti[0][0] * iTr[0][0] + Tr[1][0] * iTi[0][1] +
                Ti[1][0] * iTr[0][1] + Tr[2][0] * iTi[0][2] + Ti[2][0] * iTr[0][2];

        final double det = Math.sqrt(detR * detR + detI * detI);

        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                iTr[i][j] /= det;
                iTi[i][j] /= det;
            }
        }
    }

    public IndexCoding createIndexCoding() {
        final IndexCoding indexCoding = new IndexCoding("Cluster_classes");
        indexCoding.addIndex("no data", HAlphaWishart.NODATACLASS, "no data");
        for (int i = 1; i <= getNumClasses(); i++) {
            switch (i) {
                case 1:
                    indexCoding.addIndex("class_" + i, i, "Dihedral Reflector");
                    break;
                case 2:
                    indexCoding.addIndex("class_" + i, i, "Dipole");
                    break;
                case 3:
                    indexCoding.addIndex("class_" + i, i, "Bragg Surface");
                    break;
                case 4:
                    indexCoding.addIndex("class_" + i, i, "Double Reflection");
                    break;
                case 5:
                    indexCoding.addIndex("class_" + i, i, "Anisotropic Particles");
                    break;
                case 6:
                    indexCoding.addIndex("class_" + i, i, "Random Surface");
                    break;
                case 7:
                    indexCoding.addIndex("class_" + i, i, "Complex Structures");
                    break;
                case 8:
                    indexCoding.addIndex("class_" + i, i, "Random Anisotropic Scatterers");
                    break;
                case 9:
                    indexCoding.addIndex("class_" + i, i, "Non-feasible");
                    break;
                default:
                    break;
            }
        }
        return indexCoding;
    }

    public static class ClusterInfo {
        int zoneIndex;
        int size;
        double logDet;
        double[][] centerRe = null;
        double[][] centerIm = null;
        double[][] invCenterRe = null;
        double[][] invCenterIm = null;

        public ClusterInfo() {
        }

        public void setClusterCenter(final int zoneIdx, final double[][] Mr, final double[][] Mi, final int size) {

            final int dimension = Mr.length;
            centerRe = new double[dimension][dimension];
            centerIm = new double[dimension][dimension];
            invCenterRe = new double[dimension][dimension];
            invCenterIm = new double[dimension][dimension];

            this.zoneIndex = zoneIdx;
            this.size = size;
            for (int i = 0; i < dimension; ++i) {
                for (int j = 0; j < dimension; ++j) {
                    this.centerRe[i][j] = Mr[i][j];
                    this.centerIm[i][j] = Mi[i][j];
                }
            }

            if (dimension == 3) {
                this.logDet = Math.log(determinantCmplxMatrix3(Mr, Mi));
                inverseCmplxMatrix3(Mr, Mi, invCenterRe, invCenterIm);
            } else if (dimension == 2) {
                this.logDet = Math.log(determinantCmplxMatrix2(Mr, Mi));
                inverseCmplxMatrix2(Mr, Mi, invCenterRe, invCenterIm);
            }
        }
    }
}
