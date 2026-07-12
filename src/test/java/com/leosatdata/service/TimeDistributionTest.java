package com.leosatdata.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.log4j.Level;

import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;
import com.leosatdata.model.DataRate;
import com.leosatdata.model.Duplex;
import com.leosatdata.model.PersistentActivityType;
import com.leosatdata.model.TimeDelayCalculationResult;
import com.leosatdata.model.TimeDistribution;
import com.leosatdata.model.TimeTag;
import com.leosatdata.model.TimeTagType;
import com.leosatdata.util.CommSettings;
import com.leosatdata.util.Configuration;

public class TimeDistributionTest {

	public static void main(String[] args) throws Exception {
		// disable rx/tx logging
		org.apache.log4j.Logger rx = org.apache.log4j.Logger.getLogger("rx");
		rx.setLevel(Level.FATAL);
		org.apache.log4j.Logger tx = org.apache.log4j.Logger.getLogger("tx");
		tx.setLevel(Level.FATAL);

		Configuration groundConfig;
		NullTransceiver groundTransceiver;
		MockNodeCallback groundCallback;
		PerfUserDataCallback groundDataCallback;
		Node ground;

		Configuration satelliteConfig;
		NullTransceiver satelliteTransceiver;
		MockNodeCallback satelliteCallback;
		MockUserDataCallback satelliteDataCallback;
		Node satellite;

		CommSettings commSettings = new CommSettings(3, 3, 1, 1);

		Properties props = new Properties();
		props.putAll(NodeTest.load("src/main/resources/mib.properties"));
		props.putAll(NodeTest.load("src/main/resources/transceiver.properties"));
		props.putAll(NodeTest.load("src/main/resources/custom.properties"));
		props.putAll(NodeTest.load("src/test/resources/ground.properties"));
		props.putAll(NodeTest.load("src/test/resources/channel.properties"));
		groundConfig = new Configuration(props);
		groundConfig.setProperty("u_frame_version", String.valueOf(commSettings.getuFrameVersion()));
		groundConfig.setProperty("spdu_version", String.valueOf(commSettings.getSpduVersion()));
		groundConfig.setProperty("frame_version", String.valueOf(commSettings.getpFrameVersion()));
		groundConfig.setProperty("maximum_frame_sequence_length", String.valueOf(commSettings.getMaxFrameSequenceLength()));

		groundTransceiver = new NullTransceiver(groundConfig);
		groundTransceiver.start();
		groundCallback = new MockNodeCallback();
		groundDataCallback = new PerfUserDataCallback();
		ground = new Node(groundConfig, groundTransceiver, groundCallback, groundDataCallback);

		props = new Properties();
		props.putAll(NodeTest.load("src/main/resources/mib.properties"));
		props.putAll(NodeTest.load("src/main/resources/transceiver.properties"));
		props.putAll(NodeTest.load("src/main/resources/custom.properties"));
		props.putAll(NodeTest.load("src/test/resources/satellite.properties"));
		props.putAll(NodeTest.load("src/test/resources/channel.properties"));
		satelliteConfig = new Configuration(props);
		satelliteConfig.setProperty("u_frame_version", String.valueOf(commSettings.getuFrameVersion()));
		satelliteConfig.setProperty("spdu_version", String.valueOf(commSettings.getSpduVersion()));
		satelliteConfig.setProperty("frame_version", String.valueOf(commSettings.getpFrameVersion()));
		satelliteConfig.setProperty("maximum_frame_sequence_length", String.valueOf(commSettings.getMaxFrameSequenceLength()));

		satelliteTransceiver = new NullTransceiver(satelliteConfig);
		satelliteTransceiver.start();
		satelliteCallback = new MockNodeCallback();
		satelliteDataCallback = new MockUserDataCallback();
		satellite = new Node(satelliteConfig, satelliteTransceiver, satelliteCallback, satelliteDataCallback);

		groundTransceiver.setOtherSide(satelliteTransceiver);
		satelliteTransceiver.setOtherSide(groundTransceiver);

		ground.start();
		satellite.start();

		Duplex duplex = Duplex.FULL_DUPLEX;
		satellite.setDuplex(duplex);
		satellite.waitForSession();
		ground.setDuplex(duplex);
		if (commSettings.getSpduVersion() == 1) {
			ground.setCommParameters(NodeTest.createType1CommParameters(DataRate.R128C));
		} else if (commSettings.getSpduVersion() == 2) {
			ground.setCommParameters(NodeTest.createType4CommParameters());
		} else if (commSettings.getSpduVersion() == 3) {
			ground.setCommParameters(NodeTest.createType5CommParameters());
		} else {
			throw new IllegalArgumentException("unsupported: " + commSettings.getuFrameVersion());
		}
		ground.startSession();

		// make sure data service
		groundCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY, PersistentActivityType.HAIL);
		satelliteCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY, PersistentActivityType.HAIL);
		Thread.sleep(2 * (groundConfig.getDuration("carrier_only_duration") + groundConfig.getDuration("acquisition_idle_duration")));

		ground.startTimeTagging(10);

		ControllerEvent groundTagged = groundCallback.waitForEvent(ControllerEventType.TIMING_SERVICES_INSTANCE);
		ControllerEvent satelliteTagged = satelliteCallback.waitForEvent(ControllerEventType.TIMING_SERVICES_INSTANCE);

		Map<String, TimeTag> groundIndex = indexTimeTag(groundTagged);
		Map<String, TimeTag> satelliteIndex = indexTimeTag(satelliteTagged);

		long totalRxLatency = 0;
		int totalRx = 0;
		long totalTxLatency = 0;
		int totalTx = 0;
		for (Entry<String, TimeTag> cur : groundIndex.entrySet()) {
			TimeTag curTag = cur.getValue();
			TimeTagType opposite;
			if (curTag.getType().equals(TimeTagType.RX)) {
				opposite = TimeTagType.TX;
			} else {
				opposite = TimeTagType.RX;
			}
			String key = curTag.getFrameSequenceNumber() + "." + curTag.getQos() + "." + opposite;
			TimeTag otherSide = satelliteIndex.get(key);
			if (otherSide == null) {
				continue;
			}
			if (curTag.getType().equals(TimeTagType.RX)) {
				long latency = curTag.getTime() - otherSide.getTime();
				totalRxLatency += latency;
				totalRx++;
			} else {
				long latency = otherSide.getTime() - curTag.getTime();
				totalTxLatency += latency;
				totalTx++;
			}
		}

		System.out.println("one-way light time RX: " + (totalRxLatency / totalRx) + " ms");
		System.out.println("one-way light time TX: " + (totalTxLatency / totalTx) + " ms");

		int internalDelayTotal = 10;
		long totalRxDelay = 0;
		long totalTxDelay = 0;
		for (int i = 0; i < internalDelayTotal; i++) {
			ground.calculateTimeDelay(System.currentTimeMillis());
			ControllerEvent event = groundCallback.waitForEvent(ControllerEventType.TIME_DELAY_CALCULATION);
			TimeDelayCalculationResult result = (TimeDelayCalculationResult) event.getObj();
			long current = System.currentTimeMillis();
			totalRxDelay += (current - result.getRxAsmTimeMillis());
			totalTxDelay += (result.getTxAsmTimeMillis() - result.getInitialTimeMillis());
		}

		System.out.println("internal RX delay: " + (totalRxDelay / internalDelayTotal) + " ms");
		System.out.println("internal TX delay: " + (totalTxDelay / internalDelayTotal) + " ms");

		TimeDistribution time = new TimeDistribution();
		time.setOneWayLightTime((int) (totalTxLatency / totalTx));
		time.setSendSideDelay((int) (totalTxDelay / internalDelayTotal));
		time.setTransceiverClock(System.currentTimeMillis());
		ground.distributeTime(time);

		ControllerEvent event = satelliteCallback.waitForEvent(ControllerEventType.TIME_DISTRIBUTION);
		long current = System.currentTimeMillis();
		TimeDistribution expected = (TimeDistribution) event.getObj();
		// 16 bytes for TimeDistribution directive
		// 4 bytes fo CRC32
		// asm is not included since it is included into internal delay measurement
		long timeToSendPDU = (long) (((16 + 4) * 8 * 1000) / groundTransceiver.getTxSymbolRate());
		// assume rx latency is the same between ground and satellite nodes
		long expectedTime = expected.getTransceiverClock() + expected.getOneWayLightTime() + expected.getSendSideDelay() + totalRxDelay / internalDelayTotal + timeToSendPDU;
		System.out.println("time difference: " + (current - expectedTime) + " ms");

		satellite.stop();
		ground.stop();
		satelliteTransceiver.stop();
		groundTransceiver.stop();
	}

	private static Map<String, TimeTag> indexTimeTag(ControllerEvent event) {
		@SuppressWarnings("unchecked")
		List<TimeTag> tags = (List<TimeTag>) event.getObj();
		Map<String, TimeTag> result = new HashMap<>();
		for (TimeTag cur : tags) {
			result.put(cur.getFrameSequenceNumber() + "." + cur.getQos() + "." + cur.getType(), cur);
		}
		return result;
	}
}
