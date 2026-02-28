package com.leosatdata.service;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import com.leosatdata.util.Configuration;

public class CommTest {

	private Configuration groundConfig;
	private Node ground;

	private Configuration satelliteConfig;
	private Node satellite;

	@Test
	public void testSuccess() throws Exception {
		// FIXME do some communication
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

		ground = new Node(groundConfig);
		satellite = new Node(satelliteConfig);

		ground.getTransceiver().setOtherSide(satellite.getTransceiver());
		satellite.getTransceiver().setOtherSide(ground.getTransceiver());

		ground.start();
		satellite.start();
	}

	private static Properties load(String file) throws Exception {
		Properties result = new Properties();
		try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
			result.load(is);
		}
		return result;
	}

}
