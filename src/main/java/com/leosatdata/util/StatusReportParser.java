package com.leosatdata.util;

import java.io.IOException;

import com.leosatdata.model.SpduHeader;
import com.leosatdata.model.StatusReport;

public class StatusReportParser {

	public static StatusReport decode(SpduHeader header, BitInputStream bis) throws IOException {
		StatusReport result = new StatusReport();
		result.setHeader(header);
		byte[] statusReport = new byte[16];
		bis.readFully(statusReport);
		result.setReport(statusReport);
		return result;
	}

	public static void encode(StatusReport message, BitOutputStream payload) {
		// TODO Auto-generated method stub
		
	}

}
