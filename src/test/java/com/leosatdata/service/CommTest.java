package com.leosatdata.service;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.leosatdata.model.ControllerEvent;
import com.leosatdata.service2.Node2;
import com.leosatdata.util.Configuration;

public class CommTest {

	private Configuration groundConfig;
	private NullTransceiver groundTransceiver;
	private Node2 ground;

	private Configuration satelliteConfig;
	private NullTransceiver satelliteTransceiver;
	private Node2 satellite;

	@Test
	public void testSuccess() throws Exception {
		satellite.waitForSession();
		ground.startSession();
		Thread.sleep(10000);
		// FIXME do some communication
	}

	@Before
	public void start() throws Exception {
		Properties props = new Properties();
		props.putAll(load("src/main/resources/mib.properties"));
		props.putAll(load("src/main/resources/transceiver.properties"));
		props.putAll(load("src/test/resources/ground.properties"));
		groundConfig = new Configuration(props);
		groundTransceiver = new NullTransceiver(groundConfig);

		props = new Properties();
		props.putAll(load("src/main/resources/mib.properties"));
		props.putAll(load("src/main/resources/transceiver.properties"));
		props.putAll(load("src/test/resources/satellite.properties"));
		satelliteConfig = new Configuration(props);
		satelliteTransceiver = new NullTransceiver(satelliteConfig);

		ground = new Node2(groundConfig, groundTransceiver, new NodeCallback() {

			@Override
			public void handle(ControllerEvent event) {
				// TODO Auto-generated method stub

			}
		}, new UserDataCallback() {

			@Override
			public void onUserData(byte[] payload) {
				// TODO Auto-generated method stub

			}
		});
		satellite = new Node2(satelliteConfig, satelliteTransceiver, new NodeCallback() {

			@Override
			public void handle(ControllerEvent event) {
				// TODO Auto-generated method stub

			}
		}, new UserDataCallback() {

			@Override
			public void onUserData(byte[] payload) {
				// TODO Auto-generated method stub

			}
		});

		groundTransceiver.setOtherSide(satelliteTransceiver);
		satelliteTransceiver.setOtherSide(groundTransceiver);

		ground.start();
		satellite.start();
	}
	
	@After
	public void stop() {
		satellite.stop();
		ground.stop();
	}

	private static Properties load(String file) throws Exception {
		Properties result = new Properties();
		try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
			result.load(is);
		}
		return result;
	}

}
