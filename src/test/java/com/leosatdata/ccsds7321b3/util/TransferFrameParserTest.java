/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.ccsds7321b3.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.ccsds7321b3.model.ConstructionRules;
import com.leosatdata.ccsds7321b3.model.TransferFrame;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.TestUtil;

public class TransferFrameParserTest {

	private Configuration config;
	private TransferFrameParser parser;

	@Test
	public void testSuccess() throws Exception {
		assertDirectives(createTestFrame());
	}

	@Test
	public void testPFrame() throws Exception {
		assertDirectives(parser.createPFrame(new byte[] { 0 }));
	}

	@Test
	public void testInsertZone() throws Exception {
		config.setProperty("framev4.insertZoneLength", "10");
		parser = new TransferFrameParser(config);
		TransferFrame frame = createTestFrame();
		frame.setInsertZone(new byte[10]);
		assertDirectives(frame);
	}

	@Test
	public void testFec() throws Exception {
		config.setProperty("framev4.frameErrorControl", "true");
		parser = new TransferFrameParser(config);
		TransferFrame frame = createTestFrame();
		frame.setFrameErrorControl(new byte[2]);
		assertDirectives(frame);
	}

	@Test
	public void testFixedLength() throws Exception {
		config.setProperty("framev4.fixedLength", "true");
		parser = new TransferFrameParser(config);
		TransferFrame frame = createTestFrame();
		TransferFrame actual = encodeDecode(frame);
		assertEquals(parser.getMaxDataLength(), actual.getDataField().length);
	}

	@Test
	public void testCreateUFrame() {
		config.setProperty("maximum_frame_length", "60");
		parser = new TransferFrameParser(config);

		byte[] packet = TestUtil.createTestData(5);
		List<TransferFrame> frames = parser.createUFrame(packet, QualityOfService.EXPEDITED, 0);
		assertEquals(1, frames.size());
		assertArrayEquals(packet, frames.get(0).getDataField());
		assertEquals(ConstructionRules.NO_SEGMENTATION, frames.get(0).getConstructionRule());

		packet = TestUtil.createTestData(100);
		frames = parser.createUFrame(packet, QualityOfService.EXPEDITED, 0);
		assertEquals(3, frames.size());
		assertEquals(ConstructionRules.STARTING_SEGMENT, frames.get(0).getConstructionRule());
		assertEquals(ConstructionRules.CONTINUING_SEGMENT, frames.get(1).getConstructionRule());
		assertEquals(ConstructionRules.LAST_SEGMENT, frames.get(2).getConstructionRule());
	}

	@Test
	public void testInvalidArguments() throws Exception {
		assertFalse(TransferFrameParser.accept(null));
		assertFalse(TransferFrameParser.accept(new byte[0]));
		assertFalse(TransferFrameParser.accept(new byte[] { 0x00 }));
	}

	@Test
	public void testInvalidPort() throws Exception {
		assertTrue(parser.createUFrame(new byte[10], QualityOfService.EXPEDITED, 64).isEmpty());
	}

	private TransferFrame encodeDecode(TransferFrame input) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (BitOutputStream bos = new BitOutputStream(baos)) {
			parser.encode(input, bos);
		}

		byte[] data = baos.toByteArray();
		assertTrue(TransferFrameParser.accept(data));
		assertTrue(config.getInteger("maximum_frame_length", 0) >= data.length);

		TransferFrame actual;
		try (BitInputStream bis = new BitInputStream(data)) {
			actual = parser.decode(bis);
		}
		return actual;
	}

	private void assertDirectives(TransferFrame expected) throws IOException {
		TestUtil.assertObjectEqual(expected, encodeDecode(expected));
	}

	private static TransferFrame createTestFrame() {
		byte[] payload = new byte[] { 1, 2, 3 };
		TransferFrame result = new TransferFrame();
		result.setScid(1);
		result.setSourceOrDestination(SourceDestination.SOURCE);
		result.setVcid(2);
		result.setMapid(2);
		result.setHeaderTruncated(false);
		result.setSequenceControl(QualityOfService.EXPEDITED);
		result.setProtocolControl(PduTypeId.SPDU);
		result.setOcf(true);
		result.setOperationalControl(new byte[4]);
		result.setVcfCountLength(1);
		result.setVcfCount(4);
		result.setConstructionRule(ConstructionRules.PSMF);
		result.setFirstHeaderPointer(0);
		result.setDataField(payload);
		return result;
	}

	@Before
	public void start() throws Exception {
		Properties props = new Properties();
		props.putAll(TestUtil.load("src/main/resources/mib.properties"));
		props.putAll(TestUtil.load("src/main/resources/transceiver.properties"));
		props.putAll(TestUtil.load("src/main/resources/custom.properties"));
		props.putAll(TestUtil.load("src/test/resources/ground.properties"));
		config = new Configuration(props);
		parser = new TransferFrameParser(config);
	}
}
