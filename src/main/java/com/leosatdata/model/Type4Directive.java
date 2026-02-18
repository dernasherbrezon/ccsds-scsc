package com.leosatdata.model;

public class Type4Directive implements Spdu {

	private SpduHeader header;
	private LinkEstablishment linkEstablishment;
	private ReportRequest reportRequest;
	private Vr vr;
	private int sourceSpacecraftId;

	public LinkEstablishment getLinkEstablishment() {
		return linkEstablishment;
	}

	public void setLinkEstablishment(LinkEstablishment linkEstablishment) {
		this.linkEstablishment = linkEstablishment;
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

	public int getSourceSpacecraftId() {
		return sourceSpacecraftId;
	}

	public void setSourceSpacecraftId(int sourceSpacecraftId) {
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
