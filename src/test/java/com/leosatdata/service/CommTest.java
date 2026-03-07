package com.leosatdata.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;
import com.leosatdata.model.PersistentActivity;
import com.leosatdata.service2.Node2;
import com.leosatdata.util.Configuration;

public class CommTest {

	private Configuration groundConfig;
	private NullTransceiver groundTransceiver;
	private MockNodeCallback groundCallback;
	private Node2 ground;

	private Configuration satelliteConfig;
	private NullTransceiver satelliteTransceiver;
	private MockNodeCallback satelliteCallback;
	private Node2 satellite;

	@Test
	public void testSuccess() throws Exception {
		satellite.waitForSession();
		ground.startSession();
		ControllerEvent hail = groundCallback.waitForEvent();
		assertNotNull(hail);
		assertEquals(ControllerEventType.PERSISTENT_ACTIVITY, hail.getType());
		PersistentActivity event = (PersistentActivity) hail.getObj();
		assertTrue(event.isSuccess());
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
		groundCallback = new MockNodeCallback();
		ground = new Node2(groundConfig, groundTransceiver, groundCallback, new UserDataCallback() {

			@Override
			public void onUserData(byte[] payload) {
				// TODO Auto-generated method stub

			}
		});

		props = new Properties();
		props.putAll(load("src/main/resources/mib.properties"));
		props.putAll(load("src/main/resources/transceiver.properties"));
		props.putAll(load("src/test/resources/satellite.properties"));
		satelliteConfig = new Configuration(props);
		satelliteTransceiver = new NullTransceiver(satelliteConfig);
		satelliteCallback = new MockNodeCallback();
		satellite = new Node2(satelliteConfig, satelliteTransceiver, satelliteCallback, new UserDataCallback() {

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
