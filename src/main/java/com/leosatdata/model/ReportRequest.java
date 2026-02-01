package com.leosatdata.model;

public class ReportRequest {

	private SpduHeader header;
	private boolean pcid1PlcwRequest;
	private boolean pcid0PlcwRequest;
	private int timeTagRequest; // FIXME implement reference 6 section 5
	private int statusReportRequest; // 0x0 indicate report is not required. Everything else is reserved

	public SpduHeader getHeader() {
		return header;
	}

	public void setHeader(SpduHeader header) {
		this.header = header;
	}

	public boolean isPcid1PlcwRequest() {
		return pcid1PlcwRequest;
	}

	public void setPcid1PlcwRequest(boolean pcid1PlcwRequest) {
		this.pcid1PlcwRequest = pcid1PlcwRequest;
	}

	public boolean isPcid0PlcwRequest() {
		return pcid0PlcwRequest;
	}

	public void setPcid0PlcwRequest(boolean pcid0PlcwRequest) {
		this.pcid0PlcwRequest = pcid0PlcwRequest;
	}

	public int getTimeTagRequest() {
		return timeTagRequest;
	}

	public void setTimeTagRequest(int timeTagRequest) {
		this.timeTagRequest = timeTagRequest;
	}

	public int getStatusReportRequest() {
		return statusReportRequest;
	}

	public void setStatusReportRequest(int statusReportRequest) {
		this.statusReportRequest = statusReportRequest;
	}

}
