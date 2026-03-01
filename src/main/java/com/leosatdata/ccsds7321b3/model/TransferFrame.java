package com.leosatdata.ccsds7321b3.model;

// CCSDS 732.1-B-3 Transfer Frame version 4
public class TransferFrame {

	private TransferFramePrimaryHeader header;
	private byte[] insertZone;
	private byte[] dataField;
	private byte[] operationalControl;
	private byte[] frameErrorControl;

	public byte[] getInsertZone() {
		return insertZone;
	}

	public void setInsertZone(byte[] insertZone) {
		this.insertZone = insertZone;
	}

	public TransferFramePrimaryHeader getHeader() {
		return header;
	}

	public void setHeader(TransferFramePrimaryHeader header) {
		this.header = header;
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
