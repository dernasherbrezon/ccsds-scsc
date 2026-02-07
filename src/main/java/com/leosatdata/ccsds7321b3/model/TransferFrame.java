package com.leosatdata.ccsds7321b3.model;

// CCSDS 732.1-B-3 Transfer Frame version 4
public class TransferFrame {

	private TransferFramePrimaryHeader header;
//	private byte[] insertZone;
	private byte[] dataField;
	// FIXME is it needed by ccsds235.1?
//	private byte[] operationalControl;
//	private byte[] frameErrorControl;

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

}
