package com.leosatdata.ccsds2110b6.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;

import com.leosatdata.ccsds2110b6.model.DfcId;
import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.ccsds2110b6.model.TransferFrame;
import com.leosatdata.ccsds2110b6.model.TransferFrameHeader;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.TestUtil;

public class TransferFrameParserTest {

	@Test
	public void testSuccess() throws Exception {
		byte[] payload = new byte[] { 0, 1, 2 };
		TransferFrameHeader header = new TransferFrameHeader();
		header.setDfcId(DfcId.USER);
		header.setFrameLength(payload.length);
		header.setFrameSequenceNumber(12);
		header.setPcid(1);
		header.setPduTypeId(PduTypeId.USER_DATA);
		header.setPortId(1);
		header.setQualityOfService(QualityOfService.EXPEDITED);
		header.setScid(2);
		header.setSourceOrDestination(SourceDestination.SOURCE);
		header.setVersionNumber(TransferFrameParser.VERSION_NUMBER);
		TransferFrame frame = new TransferFrame();
		frame.setHeader(header);
		frame.setDataField(payload);

		assertDirectives(frame);
	}
	
	@Test
	public void testInvalidArguments() throws Exception {
		assertFalse(TransferFrameParser.accept(null));
		assertFalse(TransferFrameParser.accept(new byte[0]));
		assertFalse(TransferFrameParser.accept(new byte[] { 0x00 }));
	}

	private static TransferFrame encodeDecode(TransferFrame input) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (BitOutputStream bos = new BitOutputStream(baos)) {
			TransferFrameParser.encode(input, bos);
		}

		byte[] data = baos.toByteArray();
		assertTrue(TransferFrameParser.accept(data));

		TransferFrame actual;
		try (BitInputStream bis = new BitInputStream(data)) {
			actual = TransferFrameParser.decode(bis);
		}
		return actual;
	}

	private static void assertDirectives(TransferFrame expected) throws IOException {
		TestUtil.assertObjectEqual(expected, encodeDecode(expected));
	}
}
