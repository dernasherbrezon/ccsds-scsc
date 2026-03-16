package com.leosatdata.ccsds7320b4.util;

import static org.junit.Assert.assertEquals;
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
		frame.setInsertZone(new byte[10]);
		frame.setDataField(new byte[2027]);
		assertDirectives(frame);
	}

	@Test
	public void testPadInzertZone() throws Exception {
		TransferFramePrimaryHeader header = createHeader();
		TransferFrame frame = new TransferFrame();
		frame.setHeader(header);
		frame.setDataField(new byte[2027]);

		TransferFrame actual = encodeDecode(frame);
		assertNotNull(actual.getInsertZone());
		assertEquals(10, actual.getInsertZone().length);
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
		// make sure insertZone preserved on the wire
		props.put("aos.insertZoneLength", "10");
		config = new Configuration(props);
		parser = new TransferFrameParser(config);
	}
}
