package com.leosatdata.service;

public interface UserDataCallback {

	/**
	 * Called when packet fully assembled
	 * @param packet
	 * @param portId
	 */
	void onPacketData(byte[] packet, int portId);

	/**
	 * Handling stream of data. Can be stream of packets (AOS v2 type) or user stream data
	 * @param data
	 * @param portId - portId or vcId
	 * @param gapFramesCount - if non 0, then gap was detected. User code can try and recover the gap or be aware of it
	 * @param firstHeaderPointer - index in the data buffer of the first packet. Can be used to re-sync client with packet stream
	 */
	void onPacketStream(byte[] data, int portId, int gapFramesCount, int firstHeaderPointer);

}
