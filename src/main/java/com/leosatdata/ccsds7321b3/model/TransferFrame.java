package com.leosatdata.ccsds7321b3.model;

import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;

// CCSDS 732.1-B-3 Transfer Frame version 4
public class TransferFrame {

	// header
	private int versionNumber;
	private int scid;
	private SourceDestination sourceOrDestination;
	private int vcid; // Virtual Channel Identifier
	private int mapid; // Multiplexer Access Point Identifier
	private boolean headerTruncated; // if truncated then next fields are not initialized
	private int frameLength;
	private QualityOfService sequenceControl;
	private PduTypeId protocolControl;
	private boolean ocf;
	private int vcfCountLength;
	private int vcfCount;

	private byte[] insertZone;
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

	public SourceDestination getSourceOrDestination() {
		return sourceOrDestination;
	}

	public void setSourceOrDestination(SourceDestination sourceOrDestination) {
		this.sourceOrDestination = sourceOrDestination;
	}

	public int getVcid() {
		return vcid;
	}

	public void setVcid(int vcid) {
		this.vcid = vcid;
	}

	public int getMapid() {
		return mapid;
	}

	public void setMapid(int mapid) {
		this.mapid = mapid;
	}

	public boolean isHeaderTruncated() {
		return headerTruncated;
	}

	public void setHeaderTruncated(boolean headerTruncated) {
		this.headerTruncated = headerTruncated;
	}

	public int getFrameLength() {
		return frameLength;
	}

	public void setFrameLength(int frameLength) {
		this.frameLength = frameLength;
	}

	public QualityOfService getSequenceControl() {
		return sequenceControl;
	}

	public void setSequenceControl(QualityOfService sequenceControl) {
		this.sequenceControl = sequenceControl;
	}

	public PduTypeId getProtocolControl() {
		return protocolControl;
	}

	public void setProtocolControl(PduTypeId protocolControl) {
		this.protocolControl = protocolControl;
	}

	public boolean isOcf() {
		return ocf;
	}

	public void setOcf(boolean ocf) {
		this.ocf = ocf;
	}

	public int getVcfCountLength() {
		return vcfCountLength;
	}

	public void setVcfCountLength(int vcfCountLength) {
		this.vcfCountLength = vcfCountLength;
	}

	public int getVcfCount() {
		return vcfCount;
	}

	public void setVcfCount(int vcfCount) {
		this.vcfCount = vcfCount;
	}

	public byte[] getInsertZone() {
		return insertZone;
	}

	public void setInsertZone(byte[] insertZone) {
		this.insertZone = insertZone;
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
