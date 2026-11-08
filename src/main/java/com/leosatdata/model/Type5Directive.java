/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public class Type5Directive implements Spdu {

	public static final int SPDU_VERSION = 3;

	private SpduHeader header;
	private Type5LinkEstablishment rxLink;
	private Type5LinkEstablishment txLink;
	private Type5ReportRequest reportRequest;
	private Vr vr;
	private ServiceRequest serviceRequest;
	private SetFixedLengthFrame setFixedLength;
	private ReportSourceScid reportSourceScid;
	private PnRanging pnRanging;

	public void merge(Type5Directive obj) {
		if (obj.getRxLink() != null) {
			rxLink = obj.getRxLink();
		}
		if (obj.getTxLink() != null) {
			txLink = obj.getTxLink();
		}
	}

	public Type5LinkEstablishment getRxLink() {
		return rxLink;
	}

	public void setRxLink(Type5LinkEstablishment rxLink) {
		this.rxLink = rxLink;
	}

	public Type5LinkEstablishment getTxLink() {
		return txLink;
	}

	public void setTxLink(Type5LinkEstablishment txLink) {
		this.txLink = txLink;
	}

	public Type5ReportRequest getReportRequest() {
		return reportRequest;
	}

	public void setReportRequest(Type5ReportRequest reportRequest) {
		this.reportRequest = reportRequest;
	}

	public Vr getVr() {
		return vr;
	}

	public void setVr(Vr vr) {
		this.vr = vr;
	}

	public ReportSourceScid getReportSourceScid() {
		return reportSourceScid;
	}
	
	public void setReportSourceScid(ReportSourceScid reportSourceScid) {
		this.reportSourceScid = reportSourceScid;
	}

	public PnRanging getPnRanging() {
		return pnRanging;
	}

	public void setPnRanging(PnRanging pnRanging) {
		this.pnRanging = pnRanging;
	}
	
	public ServiceRequest getServiceRequest() {
		return serviceRequest;
	}
	
	public void setServiceRequest(ServiceRequest serviceRequest) {
		this.serviceRequest = serviceRequest;
	}
	
	public SetFixedLengthFrame getSetFixedLength() {
		return setFixedLength;
	}
	
	public void setSetFixedLength(SetFixedLengthFrame setFixedLength) {
		this.setFixedLength = setFixedLength;
	}

	@Override
	public SpduHeader getHeader() {
		return header;
	}

	@Override
	public void setHeader(SpduHeader header) {
		this.header = header;
	}

}
