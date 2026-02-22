package com.leosatdata.ccsds2110b6.model;

public class TransferFrameHeader {

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

}
