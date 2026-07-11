package com.leosatdata.util;

import java.util.Properties;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;
import com.leosatdata.model.PersistentActivity;
import com.leosatdata.model.PersistentActivityType;
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
		String result = config.getProperty(name);
		if (result == null || result.equals("")) {
			return null;
		}
		return result;
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
		// for 2 byte frame sequence numbers upper limit can be more than 127,
		// keep this limit anyway
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
		int pFrameVersion = getInteger("frame_version", 0);
		if (pFrameVersion < 3 || pFrameVersion > 4) {
			result = false;
			LOG.error("unsupported frame_version: {}", pFrameVersion);
		}
		// v2 (ccsds7320b4) support 24 bit
		// v3 (ccsds2110b6) support 8 bit only
		// v4 (ccsds7321b3) dynamic from 8 to 24 and more
		if (spduVersion == Type1Directive.SPDU_VERSION && maximumFrameSequenceLength == 2) {
			result = false;
			LOG.error("u_frame_version {} doesn't support maximum_frame_sequence_length {}", uFrameVersion, maximumFrameSequenceLength);
		}
		long carrierOnlyDuration = getDuration("carrier_only_duration");
		long acquisitionIdleDuration = getDuration("acquisition_idle_duration");
		long hailWaitDuration = getDuration("hail_wait_duration");
		if (hailWaitDuration < carrierOnlyDuration + acquisitionIdleDuration) {
			result = false;
			LOG.error("hail_wait_duration should be more than carrier_only_duration + acquisition_idle_duration");
		}
		int pcid = getInteger("local_pcid", 0);
		if (pcid > 1 || pcid < 0) {
			result = false;
			LOG.error("invalid local_pcid value: {}. Supported: 0 or 1", pcid);
		}
		pcid = getInteger("remote_pcid", 0);
		if (pcid > 1 || pcid < 0) {
			result = false;
			LOG.error("invalid remote_pcid value: {}. Supported: 0 or 1", pcid);
		}
		int localScid = getInteger("local_spacecraft_id", 0);
		int remoteScid = getInteger("remote_spacecraft_id", 0);
		if (uFrameVersion == 2 || pFrameVersion == 2) {
			result |= validate(com.leosatdata.ccsds7320b4.util.TransferFrameParser.SPACECRAFT_LENGTH_BITS, "local_spacecraft_id", localScid);
			result |= validate(com.leosatdata.ccsds7320b4.util.TransferFrameParser.SPACECRAFT_LENGTH_BITS, "remote_spacecraft_id", remoteScid);
		}
		if (uFrameVersion == 3 || pFrameVersion == 3) {
			result |= validate(com.leosatdata.ccsds2110b6.util.TransferFrameParser.SPACECRAFT_LENGTH_BITS, "local_spacecraft_id", localScid);
			result |= validate(com.leosatdata.ccsds2110b6.util.TransferFrameParser.SPACECRAFT_LENGTH_BITS, "remote_spacecraft_id", remoteScid);
		}
		if (uFrameVersion == 4 || pFrameVersion == 4) {
			result |= validate(com.leosatdata.ccsds7321b3.util.TransferFrameParser.SPACECRAFT_LENGTH_BITS, "local_spacecraft_id", localScid);
			result |= validate(com.leosatdata.ccsds7321b3.util.TransferFrameParser.SPACECRAFT_LENGTH_BITS, "remote_spacecraft_id", remoteScid);
		}
		int sourceOrDestination = getInteger("source_destination_id", 0);
		if (sourceOrDestination > 1 || sourceOrDestination < 0) {
			result = false;
			LOG.error("invalid source_destination_id value: {}. Supported: 0 or 1", pcid);
		}
		return result;
	}

	private static boolean validate(int numberOfBits, String fieldName, int value) {
		int maxValue = (1 << numberOfBits) - 1;
		if (maxValue < value) {
			LOG.error("invalid " + fieldName + ": " + value + " maxValue: " + maxValue);
			return false;
		}
		return true;
	}

	public ControllerEvent createNotification(String prefix, String failureMessage) {
		PersistentActivity activity = new PersistentActivity();
		activity.setLifetime(getInteger(prefix + "_lifetime", 0));
		activity.setWaitingPeriod(getInteger(prefix + "_wait_duration", 0));
		activity.setNotification(PersistentActivityType.valueOf(getProperty(prefix + "_notification")));
		activity.setResponse(getInteger(prefix + "_response", 0));
		if (failureMessage == null) {
			return new ControllerEvent(ControllerEventType.PERSISTENT_ACTIVITY, activity);
		}
		return new ControllerEvent(ControllerEventType.PERSISTENT_ACTIVITY, activity, 1, failureMessage);
	}

	public ControllerEvent createNotification(String prefix) {
		return createNotification(prefix, null);
	}
}
