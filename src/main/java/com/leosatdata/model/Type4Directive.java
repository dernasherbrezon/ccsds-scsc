package com.leosatdata.model;

public class Type4Directive implements Spdu {

	private SpduHeader header;
	private LinkEstablishment rxLink;
	private LinkEstablishment txLink;
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

	public LinkEstablishment getRxLink() {
		return rxLink;
	}

	public void setRxLink(LinkEstablishment rxLink) {
		this.rxLink = rxLink;
	}

	public void setTxLink(LinkEstablishment txLink) {
		this.txLink = txLink;
	}

	public LinkEstablishment getTxLink() {
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

}
