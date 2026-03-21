package com.leosatdata.ccsds2110b6.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import com.leosatdata.ccsds2110b6.model.DfcId;
import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SegmentFlags;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.ccsds2110b6.model.TransferFrame;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.TestUtil;

public class TransferFrameParserTest {

	private Configuration config;

	@Test
	public void testSuccess() throws Exception {
		TransferFrame frame = createFrame();
		assertDirectives(frame);
	}

	@Test
	public void testSegmented() throws Exception {
		TransferFrame frame = createFrame();
		frame.setDfcId(DfcId.SEGMENTED);
		frame.setSegmentFlags(SegmentFlags.FIRST);
		frame.setPseudoPacketId(4);
		assertDirectives(frame);
	}

	@Test
	public void testInvalidArguments() throws Exception {
		assertFalse(TransferFrameParser.accept(null));
		assertFalse(TransferFrameParser.accept(new byte[0]));
		assertFalse(TransferFrameParser.accept(new byte[] { 0x00 }));
	}

	@Before
	public void start() throws Exception {
		Properties props = new Properties();
		props.putAll(TestUtil.load("src/main/resources/mib.properties"));
		props.putAll(TestUtil.load("src/main/resources/transceiver.properties"));
		props.putAll(TestUtil.load("src/main/resources/custom.properties"));
		props.putAll(TestUtil.load("src/test/resources/ground.properties"));
		config = new Configuration(props);
	}

	private static TransferFrame createFrame() {
		byte[] payload = new byte[] { 0, 1, 2 };
		TransferFrame frame = new TransferFrame();
		frame.setDfcId(DfcId.USER);
		frame.setFrameSequenceNumber(12);
		frame.setPcid(1);
		frame.setPduTypeId(PduTypeId.USER_DATA);
		frame.setPortId(1);
		frame.setQualityOfService(QualityOfService.EXPEDITED);
		frame.setScid(2);
		frame.setSourceOrDestination(SourceDestination.SOURCE);
		frame.setDataField(payload);
		return frame;
	}

	private TransferFrame encodeDecode(TransferFrame input) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (BitOutputStream bos = new BitOutputStream(baos)) {
			TransferFrameParser.encode(input, bos);
		}

		byte[] data = baos.toByteArray();
		assertTrue(TransferFrameParser.accept(data));
		assertTrue(config.getInteger("maximum_frame_length", 0) >= data.length);

		TransferFrame actual;
		try (BitInputStream bis = new BitInputStream(data)) {
			actual = TransferFrameParser.decode(bis);
		}
		return actual;
	}

	private void assertDirectives(TransferFrame expected) throws IOException {
		TestUtil.assertObjectEqual(expected, encodeDecode(expected));
	}
}
