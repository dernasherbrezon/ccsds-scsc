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
import com.leosatdata.ccsds7320b4.model.TransferFramePrimaryHeader;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.TestUtil;

public class TransferFrameParserTest {

	private Configuration config;
	private TransferFrameParser parser;

	@Test
	public void testSuccess() throws Exception {
		TransferFramePrimaryHeader header = createHeader();
		TransferFrame frame = new TransferFrame();
		frame.setHeader(header);
		frame.setDataField(new byte[2037]);
		assertDirectives(frame);
	}

	@Test
	public void testPadInzertZone() throws Exception {
		// make sure insertZone preserved on the wire
		config.setProperty("aos.insertZoneLength", "10");
		parser = new TransferFrameParser(config);

		TransferFramePrimaryHeader header = createHeader();
		TransferFrame frame = new TransferFrame();
		frame.setHeader(header);
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
		TransferFramePrimaryHeader header = createHeader();
		header.setVcFrameCountCycleUsed(false);
		header.setVcFrameCountCycle(4);
		TransferFrame frame = new TransferFrame();
		frame.setHeader(header);
		frame.setDataField(new byte[2037]);

		TransferFrame actual = encodeDecode(frame);
		// ignore count cycle value if used=false
		assertEquals(0, actual.getHeader().getVcFrameCountCycle());

		header.setVcFrameCountCycleUsed(true);
		header.setVcFrameCount(4);
		actual = encodeDecode(frame);
		assertEquals(4, actual.getHeader().getVcFrameCountCycle());
	}

	@Test
	public void testFrameControl() throws Exception {
		config.setProperty("aos.frameErrorControl", "true");
		parser = new TransferFrameParser(config);

		int fec = 0x1278;

		TransferFramePrimaryHeader header = createHeader();
		header.setFrameErrorControl(fec);
		TransferFrame frame = new TransferFrame();
		frame.setHeader(header);
		frame.setDataField(new byte[2037]);
		TransferFrame actual = encodeDecode(frame);
		assertEquals(fec, actual.getHeader().getFrameErrorControl().intValue());

		header = createHeader();
		frame = new TransferFrame();
		frame.setHeader(header);
		frame.setDataField(new byte[2037]);
		actual = encodeDecode(frame);
		// even if not set, send zeros
		assertEquals(0, actual.getHeader().getFrameErrorControl().intValue());
	}

	@Test
	public void testInvalidArguments() throws Exception {
		assertFalse(TransferFrameParser.accept(null));
		assertFalse(TransferFrameParser.accept(new byte[0]));
		assertFalse(TransferFrameParser.accept(new byte[] { 0x00 }));
	}

	private static TransferFramePrimaryHeader createHeader() {
		TransferFramePrimaryHeader header = new TransferFramePrimaryHeader();
		header.setVersionNumber(TransferFrameParser.VERSION_NUMBER);
		header.setReplay(false);
		header.setScid(2);
		header.setVcid(0);
		header.setVcFrameCount(13);
		header.setVcFrameCountCycleUsed(false);
		header.setVcFrameCountCycle(0);
		return header;
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
