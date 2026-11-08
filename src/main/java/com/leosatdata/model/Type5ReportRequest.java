/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public class Type5ReportRequest {

	private boolean pcid1PlcwRequest;
	private boolean pcid0PlcwRequest;
	// LEC directive values are requested by the remote transceiver;
	// otherwise reported
	private boolean lecRequested;
	private int statusReportRequest; // 0x0 indicate report is not required. Everything else is reserved
	private int vcid;

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

	public boolean isLecRequested() {
		return lecRequested;
	}

	public void setLecRequested(boolean lecRequested) {
		this.lecRequested = lecRequested;
	}

	public int getStatusReportRequest() {
		return statusReportRequest;
	}

	public void setStatusReportRequest(int statusReportRequest) {
		this.statusReportRequest = statusReportRequest;
	}

	public int getVcid() {
		return vcid;
	}

	public void setVcid(int vcid) {
		this.vcid = vcid;
	}

	@Override
	public String toString() {
		return "[pcid1PlcwRequest=" + pcid1PlcwRequest + ", pcid0PlcwRequest=" + pcid0PlcwRequest + ", lecRequested=" + lecRequested + ", statusReportRequest=" + statusReportRequest + ", vcid=" + vcid + "]";
	}

}
