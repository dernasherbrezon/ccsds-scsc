package com.leosatdata.model;

public class Plcw implements Spdu {

	public final static int SHORT_PLCW = 0; // Type F1
	public final static int LONG_PLCW = 1; // Type F2

	private SpduHeader header;

	private boolean retransmit;
	private int pcid; // See 5.1.2.10, ‘RECEIVING_PCID_BUFFER’.
	private int expeditedFrameCounter;
	private int reportValue; // nr

	public Plcw() {
		// do nothing
	}

	@Override
	public SpduHeader getHeader() {
		return header;
	}

	@Override
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

	@Override
	public String toString() {
		return "plcw [header=" + header + ", retransmit=" + retransmit + ", pcid=" + pcid + ", expeditedFrameCounter=" + expeditedFrameCounter + ", reportValue=" + reportValue + "]";
	}

}
