/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.ccsds2110b6.model;

// PROXIMITY-1 CCSDS 211.0-B-6 Transfer Frame version 3
public class TransferFrame {

	public static final int VERSION_NUMBER = 0b10;
	public static final int HEADER_LENGTH = 5;
	public static final int SEGMENT_HEADER_LENGTH = 1;
	
	private int versionNumber;
	private QualityOfService qualityOfService;
	private PduTypeId pduTypeId;
	private DfcId dfcId;
	private int scid;
	private int pcid; // primary / backup
	private int portId;
	private SourceDestination sourceOrDestination;
	private int frameLength;
	private int frameSequenceNumber;

	// segment header. only when dfcId=01
	private SegmentFlags segmentFlags;
	private int pseudoPacketId;

	private byte[] dataField;

	public SegmentFlags getSegmentFlags() {
		return segmentFlags;
	}

	public void setSegmentFlags(SegmentFlags segmentFlags) {
		this.segmentFlags = segmentFlags;
	}

	public int getPseudoPacketId() {
		return pseudoPacketId;
	}

	public void setPseudoPacketId(int pseudoPacketId) {
		this.pseudoPacketId = pseudoPacketId;
	}

	public int getVersionNumber() {
		return versionNumber;
	}

	public void setVersionNumber(int versionNumber) {
		this.versionNumber = versionNumber;
	}

	public QualityOfService getQualityOfService() {
		return qualityOfService;
	}

	public void setQualityOfService(QualityOfService qualityOfService) {
		this.qualityOfService = qualityOfService;
	}

	public PduTypeId getPduTypeId() {
		return pduTypeId;
	}

	public void setPduTypeId(PduTypeId pduTypeId) {
		this.pduTypeId = pduTypeId;
	}

	public DfcId getDfcId() {
		return dfcId;
	}

	public void setDfcId(DfcId dfcId) {
		this.dfcId = dfcId;
	}

	public int getScid() {
		return scid;
	}

	public void setScid(int scid) {
		this.scid = scid;
	}

	public int getPcid() {
		return pcid;
	}

	public void setPcid(int pcid) {
		this.pcid = pcid;
	}

	public int getPortId() {
		return portId;
	}

	public void setPortId(int portId) {
		this.portId = portId;
	}

	public SourceDestination getSourceOrDestination() {
		return sourceOrDestination;
	}

	public void setSourceOrDestination(SourceDestination sourceOrDestination) {
		this.sourceOrDestination = sourceOrDestination;
	}

	public int getFrameLength() {
		return frameLength;
	}

	public void setFrameLength(int frameLength) {
		this.frameLength = frameLength;
	}

	public int getFrameSequenceNumber() {
		return frameSequenceNumber;
	}

	public void setFrameSequenceNumber(int frameSequenceNumber) {
		this.frameSequenceNumber = frameSequenceNumber;
	}

	public byte[] getDataField() {
		return dataField;
	}

	public void setDataField(byte[] dataField) {
		this.dataField = dataField;
	}

}
