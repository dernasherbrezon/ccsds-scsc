package com.leosatdata.model;

public enum RsCodeType {

	RS204_188, RS255_239, UNKNOWN;

	public static RsCodeType valueOfCode(int code) {
		if (code == 0) {
			return RS204_188;
		}
		if (code == 1) {
			return RS255_239;
		}
		return UNKNOWN;
	}

}
