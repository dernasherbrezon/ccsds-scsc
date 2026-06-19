package com.leosatdata.service;

public class OnePacketUserDataCallback implements UserDataCallback {

	private byte[] packet;
	private int portId;

	@Override
	public void onPacketData(byte[] packet, int portId) {
		this.packet = packet;
		this.portId = portId;
	}

	public byte[] getPacket() {
		return packet;
	}

	public int getPortId() {
		return portId;
	}

	@Override
	public void onPacketStream(byte[] data, int portId, int gapFramesCount, int firstHeaderPointer) {
		// do nothing
	}

}
