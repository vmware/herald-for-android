//  Copyright 2020-2021 Herald Project Contributors
//  SPDX-License-Identifier: Apache-2.0
//

package io.heraldprox.herald.sensor.ble.filter;

import io.heraldprox.herald.sensor.ble.filter.BLEAdvertManufacturerData;
import io.heraldprox.herald.sensor.ble.filter.BLEAdvertParser;
import io.heraldprox.herald.sensor.ble.filter.BLEScanResponseData;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AdvertParserTests {

    // MARK: Low level individual parsing functions

    @Test
    public void testDataSubsetBigEndian() {
        byte[] data = new byte[]{0,1,5,6,7,8,12,13,14};
        assertEquals(5, data[2]);
        assertEquals(6, data[3]);
        assertEquals(7, data[4]);
        assertEquals(8, data[5]);
        byte[] result = BLEAdvertParser.subDataBigEndian(data,2,4);
        assertNotNull(result);
        assertEquals(4, result.length);
        assertEquals(5, result[0]);
        assertEquals(6, result[1]);
        assertEquals(7, result[2]);
        assertEquals(8, result[3]);
    }

    @Test
    public void testDataSubsetLittleEndian() {
        byte[] data = new byte[]{0,1,5,6,7,8,12,13,14};
        byte[] result = BLEAdvertParser.subDataLittleEndian(data,2,4);
        assertNotNull(result);
        assertEquals(4, result.length);
        assertEquals(8, result[0]);
        assertEquals(7, result[1]);
        assertEquals(6, result[2]);
        assertEquals(5, result[3]);
    }

    @Test
    public void testDataSubsetBigEndianOverflow() {
        byte[] data = new byte[]{0,1,5,6,7};
        byte[] result = BLEAdvertParser.subDataBigEndian(data,2,4);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testDataSubsetLittleEndianOverflow() {
        byte[] data = new byte[]{0, 1, 5, 6, 7};
        byte[] result = BLEAdvertParser.subDataLittleEndian(data, 2, 4);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testDataSubsetBigEndianLowIndex() {
        byte[] data = new byte[]{0,1,5,6,7};
        byte[] result = BLEAdvertParser.subDataBigEndian(data,-1,4);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testDataSubsetLittleEndianLowIndex() {
        byte[] data = new byte[]{0,1,5,6,7};
        byte[] result = BLEAdvertParser.subDataLittleEndian(data,-1,4);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testDataSubsetBigEndianHighIndex() {
        byte[] data = new byte[]{0,1,5,6,7};
        byte[] result = BLEAdvertParser.subDataBigEndian(data,5,4);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testDataSubsetLittleEndianHighIndex() {
        byte[] data = new byte[]{0,1,5,6,7};
        byte[] result = BLEAdvertParser.subDataLittleEndian(data,5,4);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testDataSubsetBigEndianLargeLength() {
        byte[] data = new byte[]{0,1,5,6,7};
        byte[] result = BLEAdvertParser.subDataBigEndian(data,2,4);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testDataSubsetLittleEndianLargeLength() {
        byte[] data = new byte[]{0,1,5,6,7};
        byte[] result = BLEAdvertParser.subDataLittleEndian(data,2,4);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testDataSubsetBigEndianEmptyData() {
        byte[] data = new byte[]{};
        byte[] result = BLEAdvertParser.subDataBigEndian(data,0,1);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testDataSubsetLittleEndianEmptyData() {
        byte[] data = new byte[]{};
        byte[] result = BLEAdvertParser.subDataLittleEndian(data,0,1);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testDataSubsetBigEndianNullData() {
        byte[] result = BLEAdvertParser.subDataBigEndian(null,0,1);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testDataSubsetLittleNullEmptyData() {
        byte[] result = BLEAdvertParser.subDataLittleEndian(null,0,1);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    // MARK: HIGH LEVEL FULL PACKET METHODS

    @Test
    public void testAppleTVFG() {
        byte[] data = new byte[]{2, 1, 26, 2, 10, 8,
                (byte)0x0c, (byte)0xff, (byte)0x4c, (byte)0x00,
                (byte)0x10, (byte)0x07, (byte)0x33,
                (byte)0x1f, (byte)0x2c, (byte)0x30, (byte)0x2f, (byte)0x92,
                (byte)0x58
 };
        assertEquals("02011a020a080cff4c001007331f2c302f9258", BLEAdvertParser.hex(data));
        BLEScanResponseData result = BLEAdvertParser.parseScanResponse(data,0);
        assertNotNull(result);

        assertEquals(3,result.segments.size());
        List<BLEAdvertManufacturerData> manu = BLEAdvertParser.extractManufacturerData(result.segments);
        assertNotNull(manu);
        assertEquals(1, manu.size());

        byte[] manuData = manu.get(0).data;
        assertNotNull(manuData);
        assertEquals(9, manuData.length);
        assertEquals(16, manuData[0]); // int 16 = byte 10
        assertEquals(7, manuData[1]); // int 7 = byte 07

        // full apple tv check
//        assertEquals(true, BLEAdvertParser.isAppleTV(result.segments));
    }

}