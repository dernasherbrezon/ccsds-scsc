package com.leosatdata.service;

import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import com.leosatdata.model.MacMode;
import com.leosatdata.util.Configuration;

public class CommTest {

	private Configuration config;
	private NullTransceiver satelliteTransceiver;
	private NullTransceiver groundTransceiver;

	@Test
	public void testSuccess() throws Exception {
		Mac satellite = new Mac(config, satelliteTransceiver, new NodeCallback() {
		});
		satellite.setMode(MacMode.CONNECTING_L);

		Mac ground = new Mac(config, groundTransceiver, new NodeCallback() {
		});
		ground.setMode(MacMode.CONNECTING_T);
		
	}

	@Before
	public void start() {
		// FIXME load from mib.properties
		config = new Configuration(new Properties());

		groundTransceiver = new NullTransceiver(config);
		satelliteTransceiver = new NullTransceiver(config);

		groundTransceiver.setOtherSide(satelliteTransceiver);
		satelliteTransceiver.setOtherSide(groundTransceiver);
	}

}
