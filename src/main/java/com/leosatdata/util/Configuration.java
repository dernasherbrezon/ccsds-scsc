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
}
