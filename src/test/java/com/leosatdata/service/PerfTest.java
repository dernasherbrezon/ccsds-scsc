package com.leosatdata.service;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.model.DataRate;
import com.leosatdata.model.Duplex;
import com.leosatdata.util.CommSettings;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.NamingThreadFactory;

public class PerfTest {

	private final static Logger LOG = LoggerFactory.getLogger(PerfTest.class);

	public static void main(String[] args) throws Exception {
		// disable rx/tx logging
		org.apache.log4j.Logger rx = org.apache.log4j.Logger.getLogger("rx");
		rx.setLevel(Level.FATAL);
		org.apache.log4j.Logger tx = org.apache.log4j.Logger.getLogger("tx");
		tx.setLevel(Level.FATAL);

		for (CommSettings cur : NodeTest.getSupportedSettingsCombination()) {
			LOG.info("comm settings: {}", cur);
			stress(cur);
			LOG.info("=================");
		}
	}

	private static void stress(CommSettings commSettings) throws Exception, InterruptedException {
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

		Properties props = new Properties();
		props.putAll(NodeTest.load("src/main/resources/mib.properties"));
		props.putAll(NodeTest.load("src/main/resources/transceiver.properties"));
		props.putAll(NodeTest.load("src/main/resources/custom.properties"));
		props.putAll(NodeTest.load("src/test/resources/ground.properties"));
		props.putAll(NodeTest.load("src/test/resources/channel.properties"));
		props.setProperty("channel.delayMillis", "0");
		groundConfig = new Configuration(props);
		groundConfig.setProperty("u_frame_version", String.valueOf(commSettings.getuFrameVersion()));
		groundConfig.setProperty("spdu_version", String.valueOf(commSettings.getSpduVersion()));
		groundConfig.setProperty("frame_version", String.valueOf(commSettings.getpFrameVersion()));
		groundConfig.setProperty("maximum_frame_sequence_length", String.valueOf(commSettings.getMaxFrameSequenceLength()));

		groundTransceiver = new NullTransceiver(groundConfig);
		groundTransceiver.setTxDelay(0L);
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
		props.setProperty("channel.delayMillis", "0");
		satelliteConfig = new Configuration(props);
		satelliteConfig.setProperty("u_frame_version", String.valueOf(commSettings.getuFrameVersion()));
		satelliteConfig.setProperty("spdu_version", String.valueOf(commSettings.getSpduVersion()));
		satelliteConfig.setProperty("frame_version", String.valueOf(commSettings.getpFrameVersion()));
		satelliteConfig.setProperty("maximum_frame_sequence_length", String.valueOf(commSettings.getMaxFrameSequenceLength()));

		satelliteTransceiver = new NullTransceiver(satelliteConfig);
		satelliteTransceiver.setTxDelay(0L);
		satelliteTransceiver.start();
		satelliteCallback = new MockNodeCallback();
		satelliteDataCallback = new MockUserDataCallback();
		satellite = new Node(satelliteConfig, satelliteTransceiver, satelliteCallback, satelliteDataCallback);

		groundTransceiver.setOtherSide(satelliteTransceiver);
		satelliteTransceiver.setOtherSide(groundTransceiver);

		ground.start();
		satellite.start();

		satellite.setDuplex(Duplex.FULL_DUPLEX);
		satellite.waitForSession();
		ground.setDuplex(Duplex.FULL_DUPLEX);
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

		stress(QualityOfService.EXPEDITED, 20, groundDataCallback, satellite);
		stress(QualityOfService.EXPEDITED, 200, groundDataCallback, satellite);
		stress(QualityOfService.EXPEDITED, 2000, groundDataCallback, satellite);

		stress(QualityOfService.SEQUENCE_CONTROLLED, 20, groundDataCallback, satellite);
		stress(QualityOfService.SEQUENCE_CONTROLLED, 200, groundDataCallback, satellite);
		stress(QualityOfService.SEQUENCE_CONTROLLED, 2000, groundDataCallback, satellite);

		satellite.stop();
		ground.stop();
		satelliteTransceiver.stop();
		groundTransceiver.stop();
	}

	private static void stress(QualityOfService qos, int payloadLength, PerfUserDataCallback groundDataCallback, Node satellite) throws InterruptedException {
		ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, new NamingThreadFactory("perftest"));
		Future<?> sender = executor.submit(new Runnable() {

			@Override
			public void run() {
				byte[] data = new byte[payloadLength];
				while (!Thread.currentThread().isInterrupted()) {
					satellite.send(data, qos, 0);
				}
			}
		});

		Future<?> logger = executor.scheduleAtFixedRate(new Runnable() {

			private long previousValue = 0;

			@Override
			public void run() {
				long currentValue = groundDataCallback.getBytesReceived();
				if (previousValue == 0) {
					previousValue = currentValue;
					return;
				}
				long delta = currentValue - previousValue;
				LOG.info("{} {} {} byte/s", qos, payloadLength, delta);
				previousValue = currentValue;
			}
		}, 1000, 1000, TimeUnit.MILLISECONDS);

		Thread.sleep(10000);

		sender.cancel(true);
		logger.cancel(true);

		executor.shutdown();
	}

}
