package com.leosatdata.ccsds7320b4.util;

import java.util.ArrayList;
import java.util.List;

import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.ccsds7320b4.model.TransferFrame;
import com.leosatdata.service.UserDataCallback;
import com.leosatdata.util.Configuration;

// This is not fully implemented packet service as it's not aware of Packets
// Data link layer still need to somehow signal upper layer if stream of packets has gaps and how to recover first header pointer
public class PacketService {

	private final static int MAX_FRAME_COUNT = (1 << TransferFrameParser.VCFRAME_COUNT_LENGTH_BITS) - 1;

	private final int localScid;
	private final int remoteScid;
	private final SourceDestination sourceDestination;
	private final boolean useframeCountCycle;

	private int txVcFrameCounterCycle = 0;
	private int txVcFrameCounter = 0;
	private byte[] currentDataField;
	private int currentSize;
	private boolean storeFirstHeaderPointer = true;
	private int firstHeaderPointer = 0;

	private int previousVcFrameCounter = -1;

	public PacketService(Configuration config) {
		this.localScid = config.getInteger("local_spacecraft_id", 0);
		this.remoteScid = config.getInteger("remote_spacecraft_id", 0);
		this.sourceDestination = SourceDestination.valueOfCode(config.getInteger("source_destination_id", 0));
		this.useframeCountCycle = config.getBoolean("aos.frameCountCycle");
	}

	public void setMaxDataLength(int maxDataLength) {
		if (this.currentDataField != null && this.currentDataField.length == maxDataLength) {
			return;
		}
		// change of frame will reset current buffer
		this.currentDataField = new byte[maxDataLength];
		this.currentSize = 0;
	}

	// used in tests only
	void setVcFrameCounter(int vcFrameCounter) {
		this.txVcFrameCounter = vcFrameCounter;
	}

	public List<TransferFrame> createUFrame(byte[] packet, int portId) {
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
				frame.setVcFrameCountCycleUsed(useframeCountCycle);
				frame.setVcid(portId);
				if (txVcFrameCounter > MAX_FRAME_COUNT) {
					txVcFrameCounter = 0;
					txVcFrameCounterCycle++;
				}
				frame.setVcFrameCount(txVcFrameCounter);
				if (useframeCountCycle) {
					frame.setVcFrameCountCycle(txVcFrameCounterCycle);
				} else {
					// be explicit
					frame.setVcFrameCountCycle(0);
				}
				txVcFrameCounter++;
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

	public void onFrame(UserDataCallback callback, TransferFrame frame) {
		int diff;
		if (previousVcFrameCounter != -1) {
			if (frame.getVcFrameCount() >= previousVcFrameCounter) {
				diff = frame.getVcFrameCount() - previousVcFrameCounter;
			} else {
				// before the wrap-around + after wrap-around
				diff = (MAX_FRAME_COUNT + 1 - previousVcFrameCounter) + (frame.getVcFrameCount() - 0);
				// when calculating diff, doesn't really care about frame count cycle
			}
		} else {
			diff = 1;
		}
		// duplicate frame. discard
		if (diff == 0) {
			return;
		}
		callback.onPacketStream(frame.getDataField(), frame.getVcid(), diff - 1, frame.getFirstHeaderPointer());
		previousVcFrameCounter = frame.getVcFrameCount();
	}

}
