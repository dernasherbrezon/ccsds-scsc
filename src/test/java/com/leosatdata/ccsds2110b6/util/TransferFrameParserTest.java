package com.leosatdata.ccsds2110b6.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import com.leosatdata.ccsds2110b6.model.DfcId;
import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SegmentFlags;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.ccsds2110b6.model.TransferFrame;
import com.leosatdata.service.OnePacketUserDataCallback;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.TestUtil;

public class TransferFrameParserTest {

	private Configuration config;
	private TransferFrameParser parser;
	private OnePacketUserDataCallback callback;

	@Test
	public void testSuccess() throws Exception {
		assertDirectives(createFrame());
	}

	@Test
	public void testPFrame() throws Exception {
		assertDirectives(parser.createPFrame(new byte[] { 0 }));
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

	@Test
	public void testInvalidPort() throws Exception {
		assertTrue(parser.createUFrame(new byte[10], QualityOfService.EXPEDITED, 15).isEmpty());
	}

	@Test
	public void testCreateUFrame() {
		config.setProperty("maximum_frame_length", "50");
		parser = new TransferFrameParser(config);

		byte[] packet = TestUtil.createTestData(5);
		List<TransferFrame> frames = parser.createUFrame(packet, QualityOfService.EXPEDITED, 0);
		assertEquals(1, frames.size());
		assertEquals(packet, frames.get(0).getDataField());
		assertEquals(SegmentFlags.NO_SEGMENTATION, frames.get(0).getSegmentFlags());

		parser.onFrame(callback, frames.get(0));
		assertArrayEquals(packet, callback.getPacket());

		packet = TestUtil.createTestData(100);
		frames = parser.createUFrame(packet, QualityOfService.EXPEDITED, 0);
		assertEquals(3, frames.size());
		assertEquals(SegmentFlags.FIRST, frames.get(0).getSegmentFlags());
		assertEquals(SegmentFlags.CONTINUING, frames.get(1).getSegmentFlags());
		assertEquals(SegmentFlags.LAST, frames.get(2).getSegmentFlags());

		assertPacket(null, frames.get(0));
		assertPacket(null, frames.get(1));
		assertPacket(packet, frames.get(2));

		// multiple LAST won't produce anything
		assertPacket(null, frames.get(2));
		assertPacket(null, frames.get(2));

		int pseudoPacketId = frames.get(0).getPseudoPacketId();
		assertPacket(null, frames.get(0));
		frames.get(1).setPseudoPacketId(pseudoPacketId + 1);
		assertPacket(null, frames.get(1));
		assertPacket(null, frames.get(2));

		// reset back and test order is important
		frames.get(1).setPseudoPacketId(pseudoPacketId);
		assertPacket(null, frames.get(2));
		assertPacket(null, frames.get(0));
		assertPacket(null, frames.get(1));

		// good assembly when order is right again
		assertPacket(null, frames.get(0));
		assertPacket(null, frames.get(1));
		assertPacket(packet, frames.get(2));

		// several continue
		assertPacket(null, frames.get(0));
		assertPacket(null, frames.get(1));
		assertPacket(null, frames.get(1));
		assertNotNullPacket(frames.get(2));

		// frst last only
		assertPacket(null, frames.get(0));
		assertNotNullPacket(frames.get(2));
	}

	private void assertNotNullPacket(TransferFrame frame) {
		callback = new OnePacketUserDataCallback();
		parser.onFrame(callback, frame);
		assertNotNull(callback.getPacket());
	}

	private void assertPacket(byte[] expected, TransferFrame frame) {
		callback = new OnePacketUserDataCallback();
		parser.onFrame(callback, frame);
		if (expected == null) {
			assertNull(callback.getPacket());
		} else {
			assertArrayEquals(expected, callback.getPacket());
		}

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
		callback = new OnePacketUserDataCallback();
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
