package com.leosatdata.util;

import java.io.IOException;

import com.leosatdata.model.SpduHeader;
import com.leosatdata.model.TimeDistribution;
import com.leosatdata.model.TimeDistributionType;

public class TimeDistributionParser {

	public static TimeDistribution decode(SpduHeader header, BitInputStream bis) throws IOException {
		TimeDistribution timeDistribution = new TimeDistribution();
		timeDistribution.setHeader(header);
		timeDistribution.setType(TimeDistributionType.valueOfCode(bis.readUnsignedByte()));
		timeDistribution.setTransceiverClock(bis.readLong());
		timeDistribution.setSendSideDelay(bis.readUnsignedInt(3 * 8));
		timeDistribution.setOneWayLightTime(bis.readUnsignedInt(3 * 8));
		return timeDistribution;
	}

	public static void encode(TimeDistribution message, BitOutputStream payload) {
		// TODO Auto-generated method stub
		
	}

}
