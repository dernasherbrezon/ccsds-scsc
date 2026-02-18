package com.leosatdata.model;

public class StatusReport implements Spdu {

	private SpduHeader header;
	private byte[] report;

	@Override
	public SpduHeader getHeader() {
		return header;
	}

	@Override
	public void setHeader(SpduHeader header) {
		this.header = header;
	}

	public byte[] getReport() {
		return report;
	}

	public void setReport(byte[] report) {
		this.report = report;
	}

}
