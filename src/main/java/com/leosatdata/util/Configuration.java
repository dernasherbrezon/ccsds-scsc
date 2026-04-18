package com.leosatdata.util;

import java.util.Properties;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type4Directive;
import com.leosatdata.model.Type5Directive;

public class Configuration {

	private final static Logger LOG = LoggerFactory.getLogger(Configuration.class);
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

	public Object removeProperty(String name) {
		return config.remove(name);
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

	public boolean validate() {
		boolean result = true;
		int transmissionWindow = getInteger("transmission_window", 0);
		if (transmissionWindow <= 0 || transmissionWindow > 127) {
			result = false;
			LOG.error("invalid transmission_window parameter: {}", transmissionWindow);
		}
		int spduVersion = getInteger("spdu_version", 0);
		if (spduVersion != Type1Directive.SPDU_VERSION && spduVersion != Type4Directive.SPDU_VERSION && spduVersion != Type5Directive.SPDU_VERSION) {
			result = false;
			LOG.error("unsupported spdu_version: {}", spduVersion);
		}
		int maximumFrameSequenceLength = getInteger("maximum_frame_sequence_length", 0);
		if (maximumFrameSequenceLength != 1 && maximumFrameSequenceLength != 2) {
			result = false;
			LOG.error("invalid maximum_frame_sequence_length: {}", maximumFrameSequenceLength);
		}
		int uFrameVersion = getInteger("u_frame_version", 0);
		if (uFrameVersion < 2 || uFrameVersion > 4) {
			result = false;
			LOG.error("unsupported u_frame_version: {}", uFrameVersion);
		}
		// v2 (ccsds7320b4) support 24 bit
		// v3 (ccsds2110b6) support 8 bit only
		// v4 (ccsds7321b3) dynamic from 8 to 24 and more
		if (spduVersion == Type1Directive.SPDU_VERSION && maximumFrameSequenceLength == 2) {
			result = false;
			LOG.error("u_frame_version {} doesn't support maximum_frame_sequence_length {}", uFrameVersion, maximumFrameSequenceLength);
		}
		return result;
	}
}
