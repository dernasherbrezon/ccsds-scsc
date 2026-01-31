package com.leosatdata.model;

public class Plcw {

	private SpduHeader header;

	private boolean retransmit;
	private int pcid; // See 5.1.2.10, ‘RECEIVING_PCID_BUFFER’.
	private int expeditedFrameCounter;
	private int reportValue;

	public Plcw() {
		// do nothing
	}

	public SpduHeader getHeader() {
		return header;
	}

	public void setHeader(SpduHeader header) {
		this.header = header;
	}

	public boolean isRetransmit() {
		return retransmit;
	}

	public void setRetransmit(boolean retransmit) {
		this.retransmit = retransmit;
	}

	public int getPcid() {
		return pcid;
	}

	public void setPcid(int pcid) {
		this.pcid = pcid;
	}

	public int getExpeditedFrameCounter() {
		return expeditedFrameCounter;
	}

	public void setExpeditedFrameCounter(int expeditedFrameCounter) {
		this.expeditedFrameCounter = expeditedFrameCounter;
	}

	public int getReportValue() {
		return reportValue;
	}

	public void setReportValue(int reportValue) {
		this.reportValue = reportValue;
	}

}
