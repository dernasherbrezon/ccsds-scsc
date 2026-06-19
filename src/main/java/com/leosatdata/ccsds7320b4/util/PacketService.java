package com.leosatdata.ccsds7320b4.util;

import java.util.ArrayList;
import java.util.List;

import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.ccsds7320b4.model.TransferFrame;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.Util;

// This is not fully implemented packet service as it's not aware of Packets
// Data link layer still need to somehow signal upper layer if stream of packets has gaps and how to recover first header pointer
public class PacketService {

	private final int localScid;
	private final int remoteScid;
	private final SourceDestination sourceDestination;

	private int vcFrameCounter = 0;
	private byte[] currentDataField;
	private int currentSize;
	private boolean storeFirstHeaderPointer = true;
	private int firstHeaderPointer = 0;

	public PacketService(Configuration config) {
		this.localScid = config.getInteger("local_spacecraft_id", 0);
		this.remoteScid = config.getInteger("remote_spacecraft_id", 0);
		this.sourceDestination = SourceDestination.valueOfCode(config.getInteger("source_destination_id", 0));
	}

	public void setMaxDataLength(int maxDataLength) {
		if (this.currentDataField != null && this.currentDataField.length == maxDataLength) {
			return;
		}
		// change of frame will reset current buffer
		this.currentDataField = new byte[maxDataLength];
		this.currentSize = 0;
	}

	public List<TransferFrame> createUFrame(byte[] packet, int portId) {
		Util.validateInt(TransferFrameParser.VCID_LENGTH_BITS, "portId", portId);
		int toWrite = packet.length;
		int alreadyWrote = 0;
		List<TransferFrame> result = new ArrayList<>();
		if (storeFirstHeaderPointer) {
			firstHeaderPointer = currentSize;
			storeFirstHeaderPointer = false;
		}
		while (toWrite > 0) {
			int remaining = currentDataField.length - currentSize;
			if (toWrite < remaining) {
				System.arraycopy(packet, alreadyWrote, currentDataField, currentSize, toWrite);
				currentSize += toWrite;
				alreadyWrote += toWrite;
				toWrite = 0;
			} else {
				System.arraycopy(packet, alreadyWrote, currentDataField, currentSize, remaining);
				byte[] frameDataField = new byte[currentDataField.length];
				System.arraycopy(currentDataField, 0, frameDataField, 0, frameDataField.length);
				TransferFrame frame = new TransferFrame();
				frame.setReplay(false);
				if (sourceDestination.equals(SourceDestination.SOURCE)) {
					frame.setScid(localScid);
				} else {
					frame.setScid(remoteScid);
				}
				frame.setVcFrameCountCycleUsed(false);
				frame.setVcid(portId);
				frame.setVcFrameCount(vcFrameCounter);
				// FIXME check boundaries / increment cycle count if use
				vcFrameCounter++;
				frame.setDataField(frameDataField);
				if (currentSize == 0 && toWrite > currentDataField.length) {
					frame.setFirstHeaderPointer(0x7FF);
				} else {
					frame.setFirstHeaderPointer(firstHeaderPointer);
				}
				result.add(frame);
				currentSize = 0;
				storeFirstHeaderPointer = true;
				alreadyWrote += remaining;
				toWrite -= remaining;
			}
		}
		return result;
	}

}
