/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.ccsds7320b4.model;

// AOS SPACE DATA LINK PROTOCOL CCSDS 732.0-B-4
public class TransferFrame {

	public static final int VERSION_NUMBER = 0b01;
	public static final int HEADER_LENGTH = 6;
	public static final int FEC_HEADER_LENGTH = 2;
	public static final int OCF_LENGTH = 4;
	public static final int FEC_LENGTH = 2;

	private int versionNumber;
	private int scid;
	private int vcid;
	private int vcFrameCount;

	private boolean replay;
	private boolean vcFrameCountCycleUsed;
	private int vcFrameCountCycle;
	private Integer frameHeaderErrorControl;

	private byte[] insertZone;

	// M_PDU
	private int firstHeaderPointer;

	// B_PDU
	private int bitstreamDataPointer;

	private byte[] dataField;
	private byte[] operationalControl;
	private byte[] frameErrorControl;

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

	public Integer getFrameHeaderErrorControl() {
		return frameHeaderErrorControl;
	}

	public void setFrameHeaderErrorControl(Integer frameHeaderErrorControl) {
		this.frameHeaderErrorControl = frameHeaderErrorControl;
	}

	public byte[] getInsertZone() {
		return insertZone;
	}

	public void setInsertZone(byte[] insertZone) {
		this.insertZone = insertZone;
	}

	public int getFirstHeaderPointer() {
		return firstHeaderPointer;
	}

	public void setFirstHeaderPointer(int firstHeaderPointer) {
		this.firstHeaderPointer = firstHeaderPointer;
	}

	public int getBitstreamDataPointer() {
		return bitstreamDataPointer;
	}

	public void setBitstreamDataPointer(int bitstreamDataPointer) {
		this.bitstreamDataPointer = bitstreamDataPointer;
	}

	public byte[] getDataField() {
		return dataField;
	}

	public void setDataField(byte[] dataField) {
		this.dataField = dataField;
	}

	public byte[] getOperationalControl() {
		return operationalControl;
	}

	public void setOperationalControl(byte[] operationalControl) {
		this.operationalControl = operationalControl;
	}

	public byte[] getFrameErrorControl() {
		return frameErrorControl;
	}

	public void setFrameErrorControl(byte[] frameErrorControl) {
		this.frameErrorControl = frameErrorControl;
	}
}
