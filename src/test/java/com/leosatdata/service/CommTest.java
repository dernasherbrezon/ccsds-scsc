package com.leosatdata.service;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import com.leosatdata.model.MacMode;
import com.leosatdata.util.Configuration;

public class CommTest {

	private Configuration groundConfig;
	private NullTransceiver groundTransceiver;

	private Configuration satelliteConfig;
	private NullTransceiver satelliteTransceiver;

	@Test
	public void testSuccess() throws Exception {
		Mac satellite = new Mac(satelliteConfig, satelliteTransceiver, new NodeCallback() {
			@Override
			public void onCarrier() {
				// TODO Auto-generated method stub
			}
		});
		satellite.setMode(MacMode.CONNECTING_L);

		Mac ground = new Mac(groundConfig, groundTransceiver, new NodeCallback() {
			@Override
			public void onCarrier() {
				// TODO Auto-generated method stub
			}
		});
		ground.setMode(MacMode.CONNECTING_T);

	}

	@Before
	public void start() throws Exception {
		Properties props = new Properties();
		props.putAll(load("src/main/resources/mib.properties"));
		props.putAll(load("src/main/resources/transceiver.properties"));
		props.putAll(load("src/test/resources/ground.properties"));
		groundConfig = new Configuration(props);

		props = new Properties();
		props.putAll(load("src/main/resources/mib.properties"));
		props.putAll(load("src/main/resources/transceiver.properties"));
		props.putAll(load("src/test/resources/satellite.properties"));
		satelliteConfig = new Configuration(props);

		groundTransceiver = new NullTransceiver(groundConfig);
		satelliteTransceiver = new NullTransceiver(satelliteConfig);

		groundTransceiver.setOtherSide(satelliteTransceiver);
		satelliteTransceiver.setOtherSide(groundTransceiver);
	}

	private static Properties load(String file) throws Exception {
		Properties result = new Properties();
		try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
			result.load(is);
		}
		return result;
	}

}
