package com.leosatdata.ccsds7320b4.util;

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

import com.leosatdata.ccsds7320b4.model.TransferFrame;
import com.leosatdata.service.OnePacketUserDataCallback;
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
		assertDirectives(frame);

		config.setProperty("aos.dataUnitType", "2");
		parser = new TransferFrameParser(config);
		assertDirectives(frame);
	}

	@Test
	public void testPadInzertZone() throws Exception {
		// make sure insertZone preserved on the wire
		config.setProperty("aos.insertZoneLength", "10");
		parser = new TransferFrameParser(config);

		TransferFrame frame = createFrame();

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

		TransferFrame actual = encodeDecode(frame);
		// ignore count cycle value if used=false
		assertEquals(0, actual.getVcFrameCountCycle());

		frame.setVcFrameCountCycleUsed(true);
		frame.setVcFrameCount(4);
		actual = encodeDecode(frame);
		assertEquals(4, actual.getVcFrameCountCycle());
	}

	@Test
	public void testFrameHeaderErrorControl() throws Exception {
		config.setProperty("aos.frameHeaderErrorControl", "true");
		parser = new TransferFrameParser(config);

		int fec = 0x1278;

		TransferFrame frame = createFrame();
		frame.setFrameHeaderErrorControl(fec);
		TransferFrame actual = encodeDecode(frame);
		assertEquals(fec, actual.getFrameHeaderErrorControl().intValue());

		frame = createFrame();
		actual = encodeDecode(frame);
		// even if not set, send zeros
		assertEquals(0, actual.getFrameHeaderErrorControl().intValue());
	}

	@Test
	public void testFrameErrorControl() throws Exception {
		config.setProperty("aos.frameErrorControl", "true");
		parser = new TransferFrameParser(config);

		TransferFrame frame = createFrame();
		TransferFrame actual = encodeDecode(frame);
		assertNotNull(actual.getFrameErrorControl());
		assertEquals(TransferFrame.FEC_LENGTH, actual.getFrameErrorControl().length);

		byte[] fec = new byte[TransferFrame.FEC_LENGTH];
		fec[0] = 5;
		frame.setFrameErrorControl(fec);
		actual = encodeDecode(frame);
		assertEquals(5, actual.getFrameErrorControl()[0]);
	}

	@Test
	public void testOcf() throws Exception {
		config.setProperty("aos.ocf", "true");
		parser = new TransferFrameParser(config);

		TransferFrame frame = createFrame();
		TransferFrame actual = encodeDecode(frame);
		assertNotNull(actual.getOperationalControl());
		assertEquals(TransferFrame.OCF_LENGTH, actual.getOperationalControl().length);

		byte[] ocf = new byte[TransferFrame.OCF_LENGTH];
		ocf[0] = 5;
		frame.setOperationalControl(ocf);
		actual = encodeDecode(frame);
		assertEquals(5, actual.getOperationalControl()[0]);
	}

	@Test
	public void testInvalidArguments() throws Exception {
		assertFalse(TransferFrameParser.accept(null));
		assertFalse(TransferFrameParser.accept(new byte[0]));
		assertFalse(TransferFrameParser.accept(new byte[] { 0x00 }));
	}

	@Test
	public void testInvalidPort() throws Exception {
		assertTrue(parser.createUFrame(new byte[10], 64).isEmpty());
	}

	@Test
	public void testCreateUFrame() throws Exception {
		List<TransferFrame> frames = parser.createUFrame(new byte[parser.getMaxDataLength() / 3], 0);
		// concat 3 frames
		assertEquals(0, frames.size());
		frames = parser.createUFrame(new byte[parser.getMaxDataLength() / 3], 0);
		assertEquals(0, frames.size());
		frames = parser.createUFrame(new byte[parser.getMaxDataLength() - 2 * (parser.getMaxDataLength() / 3)], 0);
		assertEquals(1, frames.size());
		assertEquals(0, frames.get(0).getVcFrameCount());
		assertEquals(0, frames.get(0).getFirstHeaderPointer());

		// packet fill whole frame
		parser = new TransferFrameParser(config);
		frames = parser.createUFrame(new byte[parser.getMaxDataLength()], 0);
		assertEquals(1, frames.size());
		assertEquals(0, frames.get(0).getVcFrameCount());
		assertEquals(0, frames.get(0).getFirstHeaderPointer());

		// packet overflow to the next frame
		parser = new TransferFrameParser(config);
		parser.createUFrame(new byte[parser.getMaxDataLength() / 3], 0);
		parser.createUFrame(new byte[parser.getMaxDataLength() / 3], 0);
		frames = parser.createUFrame(new byte[parser.getMaxDataLength()], 0);
		assertEquals(1, frames.size());
		assertEquals(0, frames.get(0).getVcFrameCount());
		frames = parser.createUFrame(new byte[parser.getMaxDataLength()], 0);
		assertEquals(1, frames.size());
		assertEquals(1, frames.get(0).getVcFrameCount());
		assertEquals(1356, frames.get(0).getFirstHeaderPointer());

		// packet more than a frame
		parser = new TransferFrameParser(config);
		parser.createUFrame(new byte[parser.getMaxDataLength() / 3], 0);
		frames = parser.createUFrame(new byte[3 * parser.getMaxDataLength()], 0);
		assertEquals(3, frames.size());
		assertEquals(0, frames.get(0).getFirstHeaderPointer());
		assertEquals(0, frames.get(0).getVcFrameCount());
		assertEquals(0x7FF, frames.get(1).getFirstHeaderPointer());
		assertEquals(1, frames.get(1).getVcFrameCount());
		assertEquals(0x7FF, frames.get(2).getFirstHeaderPointer());
		assertEquals(2, frames.get(2).getVcFrameCount());
		frames = parser.createUFrame(new byte[parser.getMaxDataLength()], 0);
		assertEquals(1, frames.size());
		assertEquals(678, frames.get(0).getFirstHeaderPointer());

		// different vcid produce packets separately
		parser = new TransferFrameParser(config);
		frames = parser.createUFrame(new byte[parser.getMaxDataLength() / 3], 0);
		assertEquals(0, frames.size());
		frames = parser.createUFrame(new byte[parser.getMaxDataLength() - (parser.getMaxDataLength() / 3)], 1);
		assertEquals(0, frames.size());
		frames = parser.createUFrame(new byte[parser.getMaxDataLength() - (parser.getMaxDataLength() / 3)], 0);
		assertEquals(1, frames.size());
		frames = parser.createUFrame(new byte[parser.getMaxDataLength() / 3], 1);
		assertEquals(1, frames.size());
	}

	@Test
	public void testFrameCounterOverflow() throws Exception {
		parser.setVcFrameCounter(16_777_215, 0);
		byte[] packet = new byte[parser.getMaxDataLength()];
		List<TransferFrame> frames = parser.createUFrame(packet, 0);
		assertEquals(1, frames.size());
		assertEquals(16_777_215, frames.get(0).getVcFrameCount());
		frames = parser.createUFrame(packet, 0);
		assertEquals(1, frames.size());
		// overflow to zero
		assertEquals(0, frames.get(0).getVcFrameCount());
	}

	@Test
	public void testOnFrame() throws Exception {
		TransferFrame frame = createFrame();
		assertData(frame.getDataField(), 0, frame);
		frame.setVcFrameCount(frame.getVcFrameCount() + 1);
		assertData(frame.getDataField(), 0, frame);
		frame.setVcFrameCount(frame.getVcFrameCount() + 2);
		assertData(frame.getDataField(), 1, frame);

		parser = new TransferFrameParser(config);
		int maxFrameCount = (1 << TransferFrameParser.VCFRAME_COUNT_LENGTH_BITS) - 1;
		frame.setVcFrameCount(maxFrameCount);
		assertData(frame.getDataField(), 0, frame);
		// wrap-around
		frame.setVcFrameCount(0);
		assertData(frame.getDataField(), 0, frame);

		parser = new TransferFrameParser(config);
		frame.setVcFrameCount(maxFrameCount - 1);
		assertData(frame.getDataField(), 0, frame);
		// wrap-around with gaps
		frame.setVcFrameCount(1);
		assertData(frame.getDataField(), 2, frame);
	}

	private void assertData(byte[] data, int gap, TransferFrame frame) {
		OnePacketUserDataCallback callback = new OnePacketUserDataCallback();
		parser.onFrame(callback, frame);
		if (data == null) {
			assertNull(callback.getData());
		} else {
			assertArrayEquals(data, callback.getData());
			assertEquals(gap, callback.getGapFramesCount());
		}
	}

	private TransferFrame createFrame() {
		byte[] data = new byte[parser.getMaxDataLength()];
		for (int i = 0; i < data.length; i++) {
			data[i] = (byte) i;
		}
		TransferFrame result = new TransferFrame();
		result.setReplay(false);
		result.setScid(2);
		result.setVcid(0);
		result.setVcFrameCount(13);
		result.setVcFrameCountCycleUsed(false);
		result.setVcFrameCountCycle(0);
		result.setDataField(data);
		return result;
	}

	private TransferFrame encodeDecode(TransferFrame input) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (BitOutputStream bos = new BitOutputStream(baos)) {
			parser.encode(input, bos);
		}

		byte[] data = baos.toByteArray();
		assertTrue(TransferFrameParser.accept(data));
		assertEquals(config.getInteger("maximum_frame_length", 0), data.length);

		TransferFrame actual;
		try (BitInputStream bis = new BitInputStream(data)) {
			actual = parser.decode(bis);
		}
		return actual;
	}

	private void assertDirectives(TransferFrame expected) throws IOException {
		TransferFrame actual = encodeDecode(expected);
		byte[] actualData = new byte[expected.getDataField().length];
		System.arraycopy(expected.getDataField(), 0, actualData, 0, actualData.length);
		actual.setDataField(actualData);
		TestUtil.assertObjectEqual(expected, actual);
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
