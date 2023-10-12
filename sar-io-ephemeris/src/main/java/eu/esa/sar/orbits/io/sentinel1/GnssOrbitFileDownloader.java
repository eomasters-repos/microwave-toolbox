/*
 * Copyright (C) 2021 by SkyWatch Space Applications Inc. http://www.skywatch.com
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
package eu.esa.sar.orbits.io.sentinel1;

import eu.esa.sar.cloud.opendata.OpenData;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.dataop.downloadable.SSLUtil;
import org.esa.snap.core.util.StringUtils;
import org.esa.snap.core.util.ThreadExecutor;
import org.esa.snap.core.util.ThreadRunnable;
import org.esa.snap.core.util.io.FileUtils;
import org.esa.snap.engine_utilities.util.ZipUtils;

import java.io.File;

public class GnssOrbitFileDownloader {

    static final String COPERNICUS_HOST = "https://scihub.copernicus.eu";
    static final String COPERNICUS_ODATA_ROOT = "https://scihub.copernicus.eu/gnss/odata/v1/";

    private static final String POEORB = "AUX_POEORB";
    private static final String RESORB = "AUX_RESORB";

    static final String USER_NAME = "gnssguest";
    static final String PASSWORD = "gnssguest";

    public File download(final File localFolder, final String mission, final String missionPrefix,
                         final String orbitType, int year, int month, final int day,
                         final ProductData.UTC stateVectorTime) throws Exception {

        final OpenSearch openSearch = new OpenSearch(GnssOrbitFileDownloader.COPERNICUS_HOST,
                GnssOrbitFileDownloader.USER_NAME, GnssOrbitFileDownloader.PASSWORD);

        String query = constructQuery(mission, missionPrefix, orbitType, year, month, day);
        OpenSearch.PageResult pageResult = openSearch.getPages(query);
        OpenSearch.SearchResult[] searchResults = openSearch.getSearchResults(pageResult);

        for(OpenSearch.SearchResult searchResult : searchResults) {
            if(Sentinel1OrbitFileReader.isWithinRange(searchResult.title, stateVectorTime)) {
                return download(localFolder, searchResult);
            }
        }

        OrbitFileScraper.NewDate newDate = OrbitFileScraper.getNeighouringMonth(year, month, day);
        query = constructQuery(mission, missionPrefix, orbitType, newDate.year, newDate.month, day);
        pageResult = openSearch.getPages(query);
        searchResults = openSearch.getSearchResults(pageResult);

        for(OpenSearch.SearchResult searchResult : searchResults) {
            if(Sentinel1OrbitFileReader.isWithinRange(searchResult.title, stateVectorTime)) {
                return download(localFolder, searchResult);
            }
        }

        return null;
    }

    public void scrape(final File localFolder, final String mission, final String missionPrefix,
                         final String orbitType, int year, int month) throws Exception {

        final OpenSearch openSearch = new OpenSearch(GnssOrbitFileDownloader.COPERNICUS_HOST,
                GnssOrbitFileDownloader.USER_NAME, GnssOrbitFileDownloader.PASSWORD);

        String query = constructQuery(mission, missionPrefix, orbitType, year, month, 0);
        OpenSearch.PageResult pageResult = openSearch.getPages(query);
        OpenSearch.SearchResult[] searchResults = openSearch.getSearchResults(pageResult);

        final ThreadExecutor executor = new ThreadExecutor();
        for(OpenSearch.SearchResult searchResult : searchResults) {
            ThreadRunnable runnable = new ThreadRunnable() {
                @Override
                public void process() throws Exception {
                    download(localFolder, searchResult);
                }
            };
            executor.execute(runnable);
        }
        executor.complete();
    }

    private File download(final File localFolder, final OpenSearch.SearchResult searchResult) throws Exception {
        final SSLUtil ssl = new SSLUtil();
        ssl.disableSSLCertificateCheck();

        try {
            final String downloadURL = GnssOrbitFileDownloader.COPERNICUS_ODATA_ROOT + "Products('" + searchResult.id + "')" + "/$value";

            final OpenData openData = new OpenData(GnssOrbitFileDownloader.COPERNICUS_ODATA_ROOT,
                    GnssOrbitFileDownloader.USER_NAME, GnssOrbitFileDownloader.PASSWORD);
            File localFile = openData.download(searchResult.id, downloadURL, localFolder, ".EOF");

            if (localFile.exists()) {
                final File localZipFile = FileUtils.exchangeExtension(localFile, ".EOF.zip");
                ZipUtils.zipFile(localFile, localZipFile);
                localFile.delete();

                return localZipFile;
            }
            return null;
        } finally {
            ssl.enableSSLCertificateCheck();
        }
    }

    static String constructQuery(final String mission, final String missionPrefix, final String orbitType,
                                 final int year, final int month, final int day) throws Exception {
        final String monthStr = StringUtils.padNum(month, 2, '0');
        final String dayStr = StringUtils.padNum(day, 2, '0');

        final StringBuilder query = new StringBuilder(COPERNICUS_HOST);
        query.append("/gnss/search?q=");
        query.append("platformname:");
        query.append(mission);
        query.append(" AND ");
        query.append("platformnumber:");
        query.append(convertMissionPrefix(missionPrefix));
        query.append(" AND ");
        query.append("producttype:");
        query.append(convertOrbitType(orbitType));
        query.append(" AND ");
        query.append("beginposition:");
        query.append("["+year+'-'+monthStr+'-'+"01"+"T00:00:000Z TO "+year+'-'+monthStr+'-'+"31"+"T24:00:000Z]");

        return query.toString();
    }

    private static String convertOrbitType(final String orbitType) throws Exception {
        if (orbitType.startsWith(RESORB) || orbitType.startsWith(SentinelPODOrbitFile.RESTITUTED)) {
            return RESORB;
        }
        if (orbitType.startsWith(POEORB) || orbitType.startsWith(SentinelPODOrbitFile.PRECISE)) {
            return POEORB;
        }
        throw new Exception("Unsupported orbit type " + orbitType);
    }

    private static String convertMissionPrefix(final String missionPrefix) throws Exception {
        switch (missionPrefix) {
            case "S1A":
                return "A";
            case "S1B":
                return "B";
            case "S1C":
                return "C";
            case "S1D":
                return "D";
            default:
                throw new Exception("Unsupported mission prefix " + missionPrefix);
        }
    }
}
