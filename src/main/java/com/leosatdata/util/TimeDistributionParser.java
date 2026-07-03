package com.leosatdata.util;

import java.io.IOException;

import com.leosatdata.model.TimeDistribution;
import com.leosatdata.model.TimeDistributionType;

public class TimeDistributionParser {

	public static TimeDistribution decode(BitInputStream bis) throws IOException {
		TimeDistribution timeDistribution = new TimeDistribution();
		timeDistribution.setType(TimeDistributionType.valueOfCode(bis.readUnsignedByte()));
		timeDistribution.setTransceiverClock(bis.readLong());
		timeDistribution.setSendSideDelay(bis.readUnsignedInt(3 * 8));
		timeDistribution.setOneWayLightTime(bis.readUnsignedInt(3 * 8));
		return timeDistribution;
	}

	public static void encode(TimeDistribution message, BitOutputStream bos) throws IOException {
		bos.writeUnsignedByte(message.getType().getCode());
		bos.writeLong(message.getTransceiverClock());
		bos.writeUnsignedInt(3 * 8, message.getSendSideDelay());
		bos.writeUnsignedInt(3 * 8, message.getOneWayLightTime());
	}

}
