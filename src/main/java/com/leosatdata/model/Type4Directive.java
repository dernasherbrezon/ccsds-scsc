/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public class Type4Directive implements Spdu {

	public static final int SPDU_VERSION = 2;

	private SpduHeader header;
	private Type4LinkEstablishment rxLink;
	private Type4LinkEstablishment txLink;
	private ReportRequest reportRequest;
	private Vr vr;
	private Integer sourceSpacecraftId;

	public void merge(Type4Directive obj) {
		if (obj.getRxLink() != null) {
			rxLink = obj.getRxLink();
		}
		if (obj.getTxLink() != null) {
			txLink = obj.getTxLink();
		}
	}

	public Type4LinkEstablishment getRxLink() {
		return rxLink;
	}

	public void setRxLink(Type4LinkEstablishment rxLink) {
		this.rxLink = rxLink;
	}

	public void setTxLink(Type4LinkEstablishment txLink) {
		this.txLink = txLink;
	}

	public Type4LinkEstablishment getTxLink() {
		return txLink;
	}

	public ReportRequest getReportRequest() {
		return reportRequest;
	}

	public void setReportRequest(ReportRequest reportRequest) {
		this.reportRequest = reportRequest;
	}

	public Vr getVr() {
		return vr;
	}

	public void setVr(Vr vr) {
		this.vr = vr;
	}

	public Integer getSourceSpacecraftId() {
		return sourceSpacecraftId;
	}

	public void setSourceSpacecraftId(Integer sourceSpacecraftId) {
		this.sourceSpacecraftId = sourceSpacecraftId;
	}

	@Override
	public SpduHeader getHeader() {
		return header;
	}

	@Override
	public void setHeader(SpduHeader header) {
		this.header = header;
	}

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		result.append("type4 [");
		if (header != null) {
			result.append(" header=").append(header);
		}
		if (rxLink != null) {
			result.append(" rx=").append(rxLink);
		}
		if (txLink != null) {
			result.append(" tx=").append(txLink);
		}
		if (reportRequest != null) {
			result.append(" report=").append(reportRequest);
		}
		if (vr != null) {
			result.append(" vr=").append(vr);
		}
		if (sourceSpacecraftId != null) {
			result.append(" ssid=").append(sourceSpacecraftId);
		}
		result.append(" ]");
		return result.toString();
	}

}
