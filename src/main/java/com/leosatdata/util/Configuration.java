package com.leosatdata.util;

import java.util.Properties;
import java.util.regex.Pattern;

public class Configuration {

	private final static Pattern UNDERSCORE = Pattern.compile("_");
	private final Properties config;

	public Configuration(Properties config) {
		this.config = config;
	}

	public String getProperty(String name) {
		return config.getProperty(name);
	}

	public void setProperty(String name, String value) {
		config.setProperty(name, value);
	}

	public long getLong(String name, long defaultValue) {
		String result = config.getProperty(name);
		if (result == null || result.trim().length() == 0) {
			return defaultValue;
		}
		return Long.valueOf(UNDERSCORE.matcher(result).replaceAll(""));
	}

	public long getDuration(String name) {
		String clock = config.getProperty("interval_clock");
		String value = config.getProperty(name);
		if (clock == null || value == null) {
			return 0;
		}
		return Long.valueOf(clock) * Long.valueOf(value);
	}

	public int getInteger(String name, int defaultValue) {
		String result = config.getProperty(name);
		if (result == null || result.trim().length() == 0) {
			return defaultValue;
		}
		return Integer.valueOf(result);
	}

	public float getFloat(String name, int defaultValue) {
		String result = config.getProperty(name);
		if (result == null) {
			return defaultValue;
		}
		return Float.valueOf(UNDERSCORE.matcher(result).replaceAll(""));
	}

	public boolean getBoolean(String name) {
		String result = config.getProperty(name);
		if (result == null || result.trim().length() == 0) {
			return false;
		}
		return Boolean.valueOf(result);
	}
}
