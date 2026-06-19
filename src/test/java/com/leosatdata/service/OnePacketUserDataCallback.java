package com.leosatdata.service;

public class OnePacketUserDataCallback implements UserDataCallback {

	private byte[] packet;
	private int portId;
	private byte[] data;
	private int gapFramesCount;
	private int firstHeaderPointer;

	@Override
	public void onPacketData(byte[] packet, int portId) {
		this.packet = packet;
		this.portId = portId;
	}

	@Override
	public void onPacketStream(byte[] data, int portId, int gapFramesCount, int firstHeaderPointer) {
		this.data = data;
		this.portId = portId;
		this.gapFramesCount = gapFramesCount;
		this.firstHeaderPointer = firstHeaderPointer;
	}

	public byte[] getPacket() {
		return packet;
	}

	public byte[] getData() {
		return data;
	}
	
	public int getPortId() {
		return portId;
	}

	public int getGapFramesCount() {
		return gapFramesCount;
	}

	public int getFirstHeaderPointer() {
		return firstHeaderPointer;
	}

}
