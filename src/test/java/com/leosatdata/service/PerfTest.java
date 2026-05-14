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
import com.leosatdata.util.Configuration;
import com.leosatdata.util.NamingThreadFactory;

public class PerfTest {

	private final static Logger LOG = LoggerFactory.getLogger(PerfTest.class);

	public static void main(String[] args) throws Exception {
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
		ground.setCommParameters(NodeTest.createType1CommParameters(DataRate.R128C));
		ground.startSession();

		org.apache.log4j.Logger rx = org.apache.log4j.Logger.getLogger("rx");
		rx.setLevel(Level.FATAL);
		org.apache.log4j.Logger tx = org.apache.log4j.Logger.getLogger("tx");
		tx.setLevel(Level.FATAL);

		ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, new NamingThreadFactory("perftest"));
		Future<?> sender = executor.submit(new Runnable() {

			@Override
			public void run() {
				byte[] data = new byte[200];
				while (!Thread.currentThread().isInterrupted()) {
					satellite.send(data, QualityOfService.EXPEDITED, 0);
				}
			}
		});

		Future<?> logger = executor.schedule(new Runnable() {

			private long previousTime = 0;
			private long previousValue = 0;

			@Override
			public void run() {
				long currentValue = groundDataCallback.getBytesReceived();
				if (previousTime == 0) {
					previousValue = currentValue;
					previousTime = System.currentTimeMillis();
					return;
				}
				long delta = currentValue - previousValue;
				// TODO not always sec use time diff
				LOG.info("{} byte/sec", delta);
				previousValue = currentValue;
				previousTime = System.currentTimeMillis();
			}
		}, 1000, TimeUnit.MILLISECONDS);

		Thread.sleep(10000);

		sender.cancel(true);
		logger.cancel(true);

		executor.shutdown();
	}

}
