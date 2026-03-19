package com.leosatdata.ccsds7320b4.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import com.leosatdata.ccsds7320b4.model.TransferFrame;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.TestUtil;

public class TransferFrameParserTest {

	private Configuration config;
	private TransferFrameParser parser;

	@Test
	public void testSuccess() throws Exception {
		TransferFrame frame = createFrame();
		frame.setDataField(new byte[2037]);
		assertDirectives(frame);
	}

	@Test
	public void testPadInzertZone() throws Exception {
		// make sure insertZone preserved on the wire
		config.setProperty("aos.insertZoneLength", "10");
		parser = new TransferFrameParser(config);

		TransferFrame frame = createFrame();
		frame.setDataField(new byte[2027]);

		TransferFrame actual = encodeDecode(frame);
		assertNotNull(actual.getInsertZone());
		assertEquals(10, actual.getInsertZone().length);

		byte[] insertZone = new byte[10];
		insertZone[0] = 5;
		frame.setInsertZone(insertZone);
		actual = encodeDecode(frame);
		assertEquals(5, actual.getInsertZone()[0]);
	}

	@Test
	public void testCycleCount() throws Exception {
		TransferFrame frame = createFrame();
		frame.setVcFrameCountCycleUsed(false);
		frame.setVcFrameCountCycle(4);
		frame.setDataField(new byte[2037]);

		TransferFrame actual = encodeDecode(frame);
		// ignore count cycle value if used=false
		assertEquals(0, actual.getVcFrameCountCycle());

		frame.setVcFrameCountCycleUsed(true);
		frame.setVcFrameCount(4);
		actual = encodeDecode(frame);
		assertEquals(4, actual.getVcFrameCountCycle());
	}

	@Test
	public void testFrameControl() throws Exception {
		config.setProperty("aos.frameErrorControl", "true");
		parser = new TransferFrameParser(config);

		int fec = 0x1278;

		TransferFrame frame = createFrame();
		frame.setFrameErrorControl(fec);
		frame.setDataField(new byte[2037]);
		TransferFrame actual = encodeDecode(frame);
		assertEquals(fec, actual.getFrameErrorControl().intValue());

		frame = createFrame();
		frame.setDataField(new byte[2037]);
		actual = encodeDecode(frame);
		// even if not set, send zeros
		assertEquals(0, actual.getFrameErrorControl().intValue());
	}

	@Test
	public void testInvalidArguments() throws Exception {
		assertFalse(TransferFrameParser.accept(null));
		assertFalse(TransferFrameParser.accept(new byte[0]));
		assertFalse(TransferFrameParser.accept(new byte[] { 0x00 }));
	}

	private static TransferFrame createFrame() {
		TransferFrame result = new TransferFrame();
		result.setVersionNumber(TransferFrameParser.VERSION_NUMBER);
		result.setReplay(false);
		result.setScid(2);
		result.setVcid(0);
		result.setVcFrameCount(13);
		result.setVcFrameCountCycleUsed(false);
		result.setVcFrameCountCycle(0);
		return result;
	}

	private TransferFrame encodeDecode(TransferFrame input) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (BitOutputStream bos = new BitOutputStream(baos)) {
			parser.encode(input, bos);
		}

		byte[] data = baos.toByteArray();
		assertTrue(TransferFrameParser.accept(data));

		TransferFrame actual;
		try (BitInputStream bis = new BitInputStream(data)) {
			actual = parser.decode(bis);
		}
		return actual;
	}

	private void assertDirectives(TransferFrame expected) throws IOException {
		TestUtil.assertObjectEqual(expected, encodeDecode(expected));
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
