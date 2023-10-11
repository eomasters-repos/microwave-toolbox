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
package eu.esa.sar.io.ceos.records;

import eu.esa.sar.io.binary.BinaryFileReader;
import eu.esa.sar.io.binary.BinaryRecord;
import eu.esa.sar.io.binary.IllegalBinaryFormatException;
import org.esa.snap.core.datamodel.MetadataAttribute;
import org.esa.snap.core.datamodel.MetadataElement;
import org.esa.snap.core.datamodel.ProductData;
import org.junit.Before;
import org.junit.Test;

import javax.imageio.stream.ImageOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BaseRecordTest {

    public static final int RECORD_LENGTH = 4680;
    private static final String format = "ers";
    private static final String recordDefinitionFile = "volume_descriptor.xml";
    private ImageOutputStream _ios;
    private String _prefix;
    private BinaryFileReader _reader;

    /*
    public void testAssignMetadataTo() throws IOException,
                                              IllegalBinaryFormatException {
        final BinaryRecord record = new BinaryRecord(_reader, _prefix.length(), format, recordDefinitionFile);
        final MetadataElement elem = new MetadataElement("elem");

        record.assignMetadataTo(elem, null);

        assertMetadata(elem);
        assertEquals(0, elem.getNumElements());
        assertEquals(6, elem.getNumAttributes());
    }
         */
    public static void assertMetadata(final MetadataElement elem) {
       /* assertIntAttribute(elem, "Record number", 1);
        assertIntAttribute(elem, "First record subtype", 077);
        assertIntAttribute(elem, "Record type code", 0300);
        assertIntAttribute(elem, "Second record subtype", 022);
        assertIntAttribute(elem, "Third record subtype", 021);       */
        assertIntAttribute(elem, "Record length", RECORD_LENGTH);
    }

    public static void assertIntAttribute(MetadataElement elem, String attributeName, int expectedValue) {
        final MetadataAttribute attribute = elem.getAttribute(attributeName);
        assertNotNull(attribute);
        assertEquals(ProductData.TYPE_INT32, attribute.getDataType());
        assertEquals(1, attribute.getNumDataElems());
        assertEquals(expectedValue, attribute.getData().getElemInt());
    }

    public static void assertStringAttribute(MetadataElement elem, String attibuteName, String expectedValue) {
        final MetadataAttribute attribute = elem.getAttribute(attibuteName);
        assertNotNull(attribute);
        assertEquals(ProductData.TYPESTRING_ASCII, attribute.getData().getTypeString());
        assertEquals(expectedValue, attribute.getData().getElemString());
    }

    public static void assertRecord(final BinaryRecord record) {
        assertNotNull(record);
   /*     assertEquals(1, record.getAttributeInt("Record Number"));
        assertEquals(077, record.getAttributeInt("First Record Subtype"));
        assertEquals(0300, record.getAttributeInt("Record Type Code"));
        assertEquals(022, record.getAttributeInt("Second Record Subtype"));
        assertEquals(021, record.getAttributeInt("Third Record Subtype"));   */
        assertEquals(RECORD_LENGTH, record.getRecordLength());
    }

    public static void writeRecordData(final ImageOutputStream ios) throws IOException {
        ios.writeInt(1); // recordNumber = 1
        ios.write(077); // firstRecordSubtype = 77 octal
        ios.write(0300); // recordTypeCode = 300 octal
        ios.write(022); // secondRecordSubtype = 22 octal
        ios.write(021); // thirdRecordSubtype = 22 octal (21 octal only for test)
        ios.writeInt(RECORD_LENGTH); // recordLength = variable
    }

    @Before
    public void setUp() throws Exception {
   /*     final ByteArrayOutputStream os = new ByteArrayOutputStream(24);
        _ios = new MemoryCacheImageOutputStream(os);
        _prefix = "BaseRecordTest_prefix";
        _ios.writeBytes(_prefix);
        writeRecordData(_ios);
        _ios.writeBytes("BaseRecordTest_suffix"); // as suffix
        _reader = new BinaryFileReader(_ios); */
    }

    @Test
    public void testInitBaseRecord() throws IOException, IllegalBinaryFormatException {
   /*     final BinaryRecord record = new BinaryRecord(_reader, _prefix.length(), format, recordDefinitionFile);

        assertRecord(record);
        assertSame(_reader, record.getReader());
        assertEquals(_prefix.length(), record.getStartPos());
        assertEquals(_prefix.length() + 12, _ios.getStreamPosition());  */
    }
   /*
    public void testCreateMetadataElement() {
        MetadataElement elem;
        String suffix;

        suffix = "suffix";
        elem = BinaryRecord.createMetadataElement("name", suffix);
        assertNotNull(elem);
        assertEquals("name suffix", elem.getName());

        suffix = "   ";
        elem = BinaryRecord.createMetadataElement("name", suffix);
        assertNotNull(elem);
        assertEquals("name", elem.getName());

        suffix = "";
        elem = BinaryRecord.createMetadataElement("name", suffix);
        assertNotNull(elem);
        assertEquals("name", elem.getName());

        suffix = null;
        elem = BinaryRecord.createMetadataElement("name", suffix);
        assertNotNull(elem);
        assertEquals("name", elem.getName());
    }   */
}
