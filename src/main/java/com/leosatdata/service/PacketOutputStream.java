package com.leosatdata.service;

import java.io.IOException;
import java.io.OutputStream;

import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.TransferFrame;
import com.leosatdata.ccsds2110b6.util.TransferFrameParser;
import com.leosatdata.util.Configuration;

public class PacketOutputStream {

	private final Node node;
	private final QualityOfService qos;
	private final int vcid;
	private final boolean fixedFrameLength;
	private final int maxDataLength;
	// buffer to keep outgoing messages
	private final byte[] buffer;
	// The number of valid bytes in the buffer
	private int count;

	private Object currentFrame;

	public PacketOutputStream(Node node, Configuration config, QualityOfService qos, int vcid) {
		this.node = node;
		this.qos = qos;
		this.vcid = vcid;
		int frameVersion = config.getInteger("u_frame_version", 3);
		int maxFrameLength = config.getInteger("maximum_frame_length", 2043);
		if (frameVersion == 2) {
			// v2 is always fixed
			this.fixedFrameLength = true;

			int dataUnitType = config.getInteger("aos.dataUnitType", 1);

			int headerLength = com.leosatdata.ccsds7320b4.model.TransferFrame.HEADER_LENGTH;
			headerLength += config.getBoolean("aos.frameHeaderErrorControl") ? com.leosatdata.ccsds7320b4.model.TransferFrame.FEC_HEADER_LENGTH : 0;
			headerLength += config.getInteger("aos.insertZoneLength", 0);
			headerLength += (dataUnitType == 1 || dataUnitType == 2) ? 2 : 0;
			headerLength += config.getBoolean("aos.ocf") ? com.leosatdata.ccsds7320b4.model.TransferFrame.OCF_LENGTH : 0;
			headerLength += config.getBoolean("aos.frameErrorControl") ? com.leosatdata.ccsds7320b4.model.TransferFrame.FEC_LENGTH : 0;

			this.maxDataLength = maxFrameLength - headerLength;
		} else if (frameVersion == 3) {
			// v3 is always variable
			this.fixedFrameLength = false;
			this.maxDataLength = maxFrameLength - com.leosatdata.ccsds2110b6.model.TransferFrame.HEADER_LENGTH - com.leosatdata.ccsds2110b6.model.TransferFrame.SEGMENT_HEADER_LENGTH;
		} else if (frameVersion == 4) {
			this.fixedFrameLength = config.getBoolean("framev4.fixedLength");

			int headerLength = 32 / 8;
			headerLength += 24 / 8; // not truncated header
			headerLength += 1; // VcfCountLength=1 byte
			headerLength += 16; // segmented SDU
			headerLength += com.leosatdata.ccsds7321b3.model.TransferFrame.TFDF_HEADER_LENGTH;
			headerLength += 0; // no ocf
			headerLength += config.getBoolean("framev4.frameErrorControl") ? com.leosatdata.ccsds7321b3.model.TransferFrame.FEC_LENGTH : 0;

			this.maxDataLength = maxFrameLength - config.getInteger("framev4.insertZoneLength", 0) - headerLength;
		} else {
			throw new IllegalArgumentException("unsupported frame version: " + frameVersion);
		}
		this.buffer = new byte[this.maxDataLength];
	}

	public void write(byte b[], int off, int len) throws IOException {
		int remaining = maxDataLength - count;
		if (len > remaining) {
			System.arraycopy(b, off, buffer, count, remaining);
			
		}
	}

	private void flushBuffer() throws IOException {

	}

}
