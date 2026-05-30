package com.leosatdata.service;

import java.util.ArrayList;
import java.util.List;

import com.leosatdata.util.Configuration;
import com.leosatdata.util.Crc32c;
import com.leosatdata.util.Util;

public class TcpCsSublayer {

	private final byte[] asm;
	private final byte[] buffer;
	private final RxCallback callback;

	private int currentOffset = 0;

	public TcpCsSublayer(Configuration config, RxCallback callback) {
		this.callback = callback;
		this.asm = Util.hexToBytes(config.getProperty("cs.sublayer.asm"));
		this.buffer = new byte[config.getInteger("maximum_frame_length", 1024)];
		if (!config.getBoolean("cs.sublayer.asm.prepend") && !config.getBoolean("cs.sublayer.crc.append")) {
			throw new IllegalArgumentException("asm and crc must be enabled for tcp sublayer");
		}
	}

	// can't assume the other end is sending idle data between frames or when
	// nothing to send
	// thus buffer here do a bit of C&S layer
	public void handle(byte[] data, int offset, int length) {
		int toHandle = length;
		int inputOffset = offset;
		while (toHandle > 0) {
			int toCopy;
			if (currentOffset + toHandle > buffer.length) {
				toCopy = buffer.length - currentOffset;
			} else {
				toCopy = toHandle;
			}
			System.arraycopy(data, inputOffset, buffer, currentOffset, toCopy);
			currentOffset += toCopy;

			checkForPdu();

			// nothing found and buffer is full. reset it
			if (currentOffset == buffer.length) {
				currentOffset = 0;
			}
			toHandle -= toCopy;
			inputOffset += toCopy;
		}
	}

	private void checkForPdu() {
		List<Integer> asmIndexes = new ArrayList<>();
		for (int i = 0; i < currentOffset - asm.length; i++) {
			if (checkAsm(buffer, i)) {
				asmIndexes.add(i);
			}
		}

		if (asmIndexes.isEmpty()) {
			return;
		}

		// attempt tail. At this stage it is not clear if remaining byte array is "asm + partial payload" or "asm + payload + crc"
		// still a chance that "asm + partial payload" accidentally have matching crc and will be discarded as processed,
		// while should be retained for the next batch from TCP socket - used only in simulation, not real production
		if (asmIndexes.size() == 1) {
			if (crcMatch(asmIndexes.get(0), currentOffset)) {
				handle(asmIndexes.get(0), currentOffset);
				currentOffset = 0;
			}
		} else {
			// multiple found. asm is not escaped in the payload, so need to check all possible combinations
			int newHead = -1;
			for (int start = 0; start < asmIndexes.size() - 1;) {
				int nextStart = -1;
				for (int end = start + 1; end < asmIndexes.size(); end++) {
					if (crcMatch(asmIndexes.get(start), asmIndexes.get(end))) {
						handle(asmIndexes.get(start), asmIndexes.get(end));
						nextStart = asmIndexes.get(end);
						newHead = nextStart;
						break;
					}
				}
				if (nextStart == -1) {
					start++;
				} else {
					start = nextStart;
				}
			}

			// handle tail
			for (int start = 0; start < asmIndexes.size(); start++) {
				if (crcMatch(asmIndexes.get(start), currentOffset)) {
					handle(asmIndexes.get(start), currentOffset);
					currentOffset = 0;
					newHead = -1;
					break;
				}
			}

			// shift non-processed to the beginning of the buffer
			if (newHead > 0) {
				int newLength = currentOffset - newHead;
				System.arraycopy(buffer, newHead, buffer, 0, newLength);
				currentOffset = newLength;
			}
		}
	}

	private void handle(int startIndex, int endIndex) {
		int pduLength = endIndex - startIndex;
		byte[] frame = new byte[pduLength];
		System.arraycopy(buffer, startIndex, frame, 0, pduLength);
		callback.onRx(frame);
	}

	private boolean crcMatch(int startIndex, int endIndex) {
		int pduLength = endIndex - startIndex;
		if (pduLength <= 0) {
			return false;
		}
		int payloadLength = pduLength - asm.length - 4;
		if (payloadLength <= 0) {
			return false;
		}
		long actualCrc = ((buffer[endIndex - 4] & 0xFFL) << 24) | ((buffer[endIndex - 3] & 0xFFL) << 16) | ((buffer[endIndex - 2] & 0xFFL) << 8) | (buffer[endIndex - 1] & 0xFFL);
		long expectedCrc = Crc32c.calculate(buffer, startIndex + asm.length, pduLength - asm.length - 4); // 4 is for CRC length
		return actualCrc == expectedCrc;
	}

	private boolean checkAsm(byte[] data, int offset) {
		for (int i = 0; i < asm.length; i++) {
			if (asm[i] != data[i + offset]) {
				return false;
			}
		}
		return true;
	}
}
