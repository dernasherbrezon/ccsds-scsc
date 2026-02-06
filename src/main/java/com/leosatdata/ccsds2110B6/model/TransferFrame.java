package com.leosatdata.ccsds2110B6.model;

// PROXIMITY-1 CCSDS 211.0-B-6 Transfer Frame version 3
public class TransferFrame {

	private TransferFrameHeader header;
	private byte[] dataField;

	public TransferFrameHeader getHeader() {
		return header;
	}

	public void setHeader(TransferFrameHeader header) {
		this.header = header;
	}

	public byte[] getDataField() {
		return dataField;
	}

	public void setDataField(byte[] dataField) {
		this.dataField = dataField;
	}

}
