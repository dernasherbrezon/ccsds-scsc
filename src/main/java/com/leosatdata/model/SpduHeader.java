package com.leosatdata.model;

public class SpduHeader {

	private int format;
	private int type;

	public int getFormat() {
		return format;
	}

	public void setFormat(int format) {
		this.format = format;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	@Override
	public String toString() {
		return format + "/" + type;
	}

}
