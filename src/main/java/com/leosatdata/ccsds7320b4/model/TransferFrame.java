package com.leosatdata.ccsds7320b4.model;

// AOS SPACE DATA LINK PROTOCOL CCSDS 732.0-B-4
public class TransferFrame {

	private TransferFramePrimaryHeader header;
	private byte[] insertZone;
	private byte[] dataField;

	public TransferFramePrimaryHeader getHeader() {
		return header;
	}

	public void setHeader(TransferFramePrimaryHeader header) {
		this.header = header;
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

}
