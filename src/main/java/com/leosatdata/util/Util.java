package com.leosatdata.util;

public class Util {

	public static byte[] hexStringToByteArray(String s) {
		int len = 0;
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == ' ') {
				continue;
			}
			len++;
		}
		byte[] data = new byte[len / 2];
		int index = 0;
		for (int i = 0; i < s.length();) {
			if (s.charAt(i) == ' ') {
				i++;
				continue;
			}
			data[index] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
			i += 2;
			index++;
		}
		return data;
	}
	
	private Util() {
		// do nothing
	}
}
