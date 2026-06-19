package com.leosatdata.ccsds7320b4.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.leosatdata.ccsds7320b4.model.TransferFrame;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.Util;

public class TransferFrameParser {

	static final int VCID_LENGTH_BITS = 6;

	private final Configuration config;
	private final int insertZoneLength;
	private final boolean frameHeaderErrorControl;
	private final int dataUnitType;
	private final boolean ocf;
	private final boolean frameErrorControl;
	private final int headerLength;
	private final Map<Integer, PacketService> serviceByVcid = new HashMap<>();

	private int maxDataLength;

	public TransferFrameParser(Configuration config) {
		this.config = config;
		this.insertZoneLength = config.getInteger("aos.insertZoneLength", 0);
		this.frameHeaderErrorControl = config.getBoolean("aos.frameHeaderErrorControl");
		this.dataUnitType = config.getInteger("aos.dataUnitType", 1);
		this.ocf = config.getBoolean("aos.ocf");
		this.frameErrorControl = config.getBoolean("aos.frameErrorControl");
		int headerLength = TransferFrame.HEADER_LENGTH;
		headerLength += frameHeaderErrorControl ? TransferFrame.FEC_HEADER_LENGTH : 0;
		headerLength += this.insertZoneLength;
		headerLength += (dataUnitType == 1 || dataUnitType == 2) ? 2 : 0;
		headerLength += ocf ? TransferFrame.OCF_LENGTH : 0;
		headerLength += frameErrorControl ? TransferFrame.FEC_LENGTH : 0;
		this.headerLength = headerLength;
		int maxFrameLength = config.getInteger("maximum_frame_length", 0);
		this.maxDataLength = maxFrameLength - headerLength;
		if (maxDataLength <= 0) {
			throw new IllegalArgumentException("maximum_frame_length is too small: " + maxFrameLength);
		}
		setFrameLength(this.maxDataLength);
	}

	public void setFrameLength(int frameLength) {
		this.maxDataLength = frameLength - this.headerLength;
		for (PacketService cur : serviceByVcid.values()) {
			cur.setMaxDataLength(maxDataLength);
		}
	}

	public static boolean accept(byte[] data) {
		if (data == null || data.length == 0) {
			return false;
		}
		return ((data[0] & 0b1100_0000) >> 6) == TransferFrame.VERSION_NUMBER;
	}

	public TransferFrame decode(BitInputStream bis) throws IOException {
		TransferFrame result = new TransferFrame();
		result.setVersionNumber(bis.readUnsignedInt(2));
		result.setScid(bis.readUnsignedInt(8));
		result.setVcid(bis.readUnsignedInt(VCID_LENGTH_BITS));
		result.setVcFrameCount(bis.readUnsignedInt(24));
		result.setReplay(bis.readBoolean());
		result.setVcFrameCountCycleUsed(bis.readBoolean());
		bis.skipBits(2);
		if (result.isVcFrameCountCycleUsed()) {
			result.setVcFrameCountCycle(bis.readUnsignedInt(4));
		} else {
			bis.skipBits(4);
		}
		if (frameHeaderErrorControl) {
			result.setFrameHeaderErrorControl(bis.readUnsignedInt(16));
		}

		if (insertZoneLength != 0) {
			byte[] insertZone = new byte[insertZoneLength];
			bis.readFully(insertZone);
			result.setInsertZone(insertZone);
		}
		if (dataUnitType == 1) {
			bis.skipBits(5);
			result.setFirstHeaderPointer(bis.readUnsignedInt(11));
		} else if (dataUnitType == 2) {
			bis.skipBits(2);
			result.setBitstreamDataPointer(bis.readUnsignedInt(14));
		}
		byte[] dataField = new byte[maxDataLength];
		bis.readFully(dataField);
		result.setDataField(dataField);
		if (ocf) {
			byte[] ocf = new byte[TransferFrame.OCF_LENGTH];
			bis.readFully(ocf);
			result.setOperationalControl(ocf);
		}
		if (frameErrorControl) {
			byte[] fec = new byte[TransferFrame.FEC_LENGTH];
			bis.readFully(fec);
			result.setFrameErrorControl(fec);
		}
		return result;
	}

	public void encode(TransferFrame frame, BitOutputStream bos) throws IOException {
		if (frame.getDataField().length > maxDataLength) {
			throw new IOException("data field length too big: " + frame.getDataField().length);
		}
		frame.setVersionNumber(TransferFrame.VERSION_NUMBER);
		bos.writeUnsignedInt(2, frame.getVersionNumber());
		bos.writeUnsignedInt(8, frame.getScid());
		bos.writeUnsignedInt(VCID_LENGTH_BITS, frame.getVcid());
		bos.writeUnsignedInt(24, frame.getVcFrameCount());
		bos.writeBoolean(frame.isReplay());
		bos.writeBoolean(frame.isVcFrameCountCycleUsed());
		bos.writeUnsignedInt(2, 0); // unused
		if (frame.isVcFrameCountCycleUsed()) {
			bos.writeUnsignedInt(4, frame.getVcFrameCountCycle());
		} else {
			bos.writeUnsignedInt(4, 0);
		}
		if (frameHeaderErrorControl) {
			int frameErrorControl = 0;
			if (frame.getFrameHeaderErrorControl() != null) {
				frameErrorControl = frame.getFrameHeaderErrorControl();
			}
			// if not provided and required, then populate with zeros
			bos.writeUnsignedInt(16, frameErrorControl);
		}
		if (insertZoneLength != 0) {
			byte[] insertZone = frame.getInsertZone();
			if (insertZone == null) {
				insertZone = new byte[insertZoneLength];
			}
			bos.writeFully(insertZone);
		}
		if (dataUnitType == 1) {
			bos.writeUnsignedInt(5, 0); // unused
			bos.writeUnsignedInt(11, frame.getFirstHeaderPointer());
		} else if (dataUnitType == 2) {
			bos.writeUnsignedInt(2, 0); // unused
			bos.writeUnsignedInt(14, frame.getBitstreamDataPointer());
		}
		// every other dataUnitTypes are just bytes
		bos.writeFully(frame.getDataField());

		if (ocf) {
			if (frame.getOperationalControl() != null) {
				bos.writeFully(frame.getOperationalControl());
			} else {
				bos.writeFully(new byte[TransferFrame.OCF_LENGTH]);
			}
		}
		if (frameErrorControl) {
			if (frame.getFrameErrorControl() != null) {
				bos.writeFully(frame.getFrameErrorControl());
			} else {
				bos.writeFully(new byte[TransferFrame.FEC_LENGTH]);
			}
		}
	}

	public int getMaxDataLength() {
		return maxDataLength;
	}

	public List<TransferFrame> createUFrame(byte[] packet, int portId) {
		Util.validateInt(VCID_LENGTH_BITS, "portId", portId);
		PacketService service = serviceByVcid.get(portId);
		if (service == null) {
			service = new PacketService(config);
			service.setMaxDataLength(this.maxDataLength);
			serviceByVcid.put(portId, service);
		}
		return service.createUFrame(packet, portId);
	}

	// Packet service and packet processing function is outside of scope of this implementation
	// pass data as-is
	// FIXME re-deserialize from stream
	public byte[] assemblePacket(Object obj) {
		TransferFrame frame = (TransferFrame) obj;
		return frame.getDataField();
	}

}
