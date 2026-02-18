package com.leosatdata.model;

public class Type5Directive implements Spdu {

	private SpduHeader header;
	private Link2Establishment linkEstablishment;
	private ReportRequest reportRequest;
	private Vr vr;
	private Integer sourceSpacecraftId;
	private PnRaging pnRanging;

	public Link2Establishment getLinkEstablishment() {
		return linkEstablishment;
	}

	public void setLinkEstablishment(Link2Establishment linkEstablishment) {
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
