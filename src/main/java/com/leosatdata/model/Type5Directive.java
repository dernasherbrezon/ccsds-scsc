package com.leosatdata.model;

public class Type5Directive implements Spdu {

	private SpduHeader header;
	private Link2Establishment rxLink;
	private Link2Establishment txLink;
	private ReportRequest reportRequest;
	private Vr vr;
	private Integer sourceSpacecraftId;
	private PnRaging pnRanging;

	public void merge(Type5Directive obj) {
		if (obj.getRxLink() != null) {
			rxLink = obj.getRxLink();
		}
		if (obj.getTxLink() != null) {
			txLink = obj.getTxLink();
		}
	}

	public Link2Establishment getRxLink() {
		return rxLink;
	}

	public void setRxLink(Link2Establishment rxLink) {
		this.rxLink = rxLink;
	}

	public Link2Establishment getTxLink() {
		return txLink;
	}

	public void setTxLink(Link2Establishment txLink) {
		this.txLink = txLink;
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

	public PnRaging getPnRanging() {
		return pnRanging;
	}

	public void setPnRanging(PnRaging pnRanging) {
		this.pnRanging = pnRanging;
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
