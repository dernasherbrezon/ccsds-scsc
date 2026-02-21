package com.leosatdata.model;

public class Type1Directive implements Spdu {

	private SpduHeader header;
	private TxRxParameters txParams;
	private PlExtensions txPlExtensions;
	private ControlParameters controlParams;
	private TxRxParameters rxParams;
	private PlExtensions rxPlExtensions;
	private Vr vr;
	private ReportRequest reportReq;
	private Integer sourceSpacecraftId;

	@Override
	public SpduHeader getHeader() {
		return header;
	}

	@Override
	public void setHeader(SpduHeader header) {
		this.header = header;
	}

	public TxRxParameters getTxParams() {
		return txParams;
	}

	public void setTxParams(TxRxParameters txParams) {
		this.txParams = txParams;
	}

	public ControlParameters getControlParams() {
		return controlParams;
	}

	public void setControlParams(ControlParameters controlParams) {
		this.controlParams = controlParams;
	}

	public TxRxParameters getRxParams() {
		return rxParams;
	}

	public void setRxParams(TxRxParameters rxParams) {
		this.rxParams = rxParams;
	}

	public Vr getVr() {
		return vr;
	}

	public void setVr(Vr vr) {
		this.vr = vr;
	}

	public ReportRequest getReportReq() {
		return reportReq;
	}

	public void setReportReq(ReportRequest reportReq) {
		this.reportReq = reportReq;
	}

	public PlExtensions getRxPlExtensions() {
		return rxPlExtensions;
	}
	
	public void setRxPlExtensions(PlExtensions rxPlExtensions) {
		this.rxPlExtensions = rxPlExtensions;
	}
	
	public PlExtensions getTxPlExtensions() {
		return txPlExtensions;
	}
	
	public void setTxPlExtensions(PlExtensions txPlExtensions) {
		this.txPlExtensions = txPlExtensions;
	}

	public Integer getSourceSpacecraftId() {
		return sourceSpacecraftId;
	}
	
	public void setSourceSpacecraftId(Integer sourceSpacecraftId) {
		this.sourceSpacecraftId = sourceSpacecraftId;
	}

}
