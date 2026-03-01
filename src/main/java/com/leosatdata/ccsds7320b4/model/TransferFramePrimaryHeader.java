package com.leosatdata.ccsds7320b4.model;

public class TransferFramePrimaryHeader {

	private int versionNumber;
	private int scid;
	private int vcid;
	private int vcFrameCount;

	private boolean replay;
	private boolean vcFrameCountCycleUsed;
	private int vcFrameCountCycle;
	private Integer frameErrorControl;

	public int getVersionNumber() {
		return versionNumber;
	}

	public void setVersionNumber(int versionNumber) {
		this.versionNumber = versionNumber;
	}

	public int getScid() {
		return scid;
	}

	public void setScid(int scid) {
		this.scid = scid;
	}

	public int getVcid() {
		return vcid;
	}

	public void setVcid(int vcid) {
		this.vcid = vcid;
	}

	public int getVcFrameCount() {
		return vcFrameCount;
	}

	public void setVcFrameCount(int vcFrameCount) {
		this.vcFrameCount = vcFrameCount;
	}

	public boolean isReplay() {
		return replay;
	}

	public void setReplay(boolean replay) {
		this.replay = replay;
	}

	public boolean isVcFrameCountCycleUsed() {
		return vcFrameCountCycleUsed;
	}

	public void setVcFrameCountCycleUsed(boolean vcFrameCountCycleUsed) {
		this.vcFrameCountCycleUsed = vcFrameCountCycleUsed;
	}

	public int getVcFrameCountCycle() {
		return vcFrameCountCycle;
	}

	public void setVcFrameCountCycle(int vcFrameCountCycle) {
		this.vcFrameCountCycle = vcFrameCountCycle;
	}

	public Integer getFrameErrorControl() {
		return frameErrorControl;
	}

	public void setFrameErrorControl(Integer frameErrorControl) {
		this.frameErrorControl = frameErrorControl;
	}

}
