package com.leosatdata.util;

import java.util.Properties;

public class Configuration {

	private final Properties config;

	public Configuration(Properties config) {
		this.config = config;
	}

	public String getProperty(String name) {
		return config.getProperty(name);
	}

	public long getLong(String name, long defaultValue) {
		String result = config.getProperty(name);
		if (result == null || result.trim().length() == 0) {
			return defaultValue;
		}
		return Long.valueOf(result);
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
}
