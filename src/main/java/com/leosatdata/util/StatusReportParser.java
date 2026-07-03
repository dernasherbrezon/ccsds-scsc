package com.leosatdata.util;

import java.io.IOException;

import com.leosatdata.model.StatusReport;

public class StatusReportParser {

	public static StatusReport decode(BitInputStream bis) throws IOException {
		StatusReport result = new StatusReport();
		byte[] statusReport = new byte[16];
		bis.readFully(statusReport);
		result.setReport(statusReport);
		return result;
	}

	public static void encode(StatusReport message, BitOutputStream bos) throws IOException {
		bos.writeFully(message.getReport());
	}

}
