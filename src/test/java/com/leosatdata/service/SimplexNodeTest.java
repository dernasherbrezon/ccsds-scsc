/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.model.DataRate;
import com.leosatdata.model.Duplex;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.TestUtil;

public class SimplexNodeTest {

	private Configuration groundConfig;
	private NullTransceiver groundTransceiver;
	private MockNodeCallback groundCallback;
	private MockUserDataCallback groundDataCallback;
	private Node ground;

	private Configuration satelliteConfig;
	private NullTransceiver satelliteTransceiver;
	private MockNodeCallback satelliteCallback;
	private MockUserDataCallback satelliteDataCallback;
	private Node satellite;

	@Test
	public void testStartSendStop() throws Exception {
		Type1Directive commParameters = NodeTest.createType1CommParameters(DataRate.R8C);

		satellite.setDuplex(Duplex.SIMPLEX_RECEIVE);
		satellite.setCommParameters(commParameters);
		satellite.waitForSession();
		ground.setDuplex(Duplex.SIMPLEX_TRANSMIT);
		ground.setCommParameters(commParameters);
		ground.startSession();

		byte[] packet = TestUtil.createTestData(20);
		ground.send(packet, QualityOfService.EXPEDITED, 0);
		byte[] actual = satelliteDataCallback.waitForData();
		assertNotNull(actual);
		assertArrayEquals(packet, actual);
	}

	@Before
	public void start() throws Exception {
		Properties props = new Properties();
		props.putAll(NodeTest.load("src/main/resources/mib.properties"));
		props.putAll(NodeTest.load("src/main/resources/transceiver.properties"));
		props.putAll(NodeTest.load("src/main/resources/custom.properties"));
		props.putAll(NodeTest.load("src/test/resources/ground.properties"));
		props.putAll(NodeTest.load("src/test/resources/channel.properties"));
		groundConfig = new Configuration(props);
		groundTransceiver = new NullTransceiver(groundConfig);
		groundTransceiver.start();
		groundCallback = new MockNodeCallback();
		groundDataCallback = new MockUserDataCallback();
		ground = new Node(groundConfig, groundTransceiver, groundCallback, groundDataCallback);

		props = new Properties();
		props.putAll(NodeTest.load("src/main/resources/mib.properties"));
		props.putAll(NodeTest.load("src/main/resources/transceiver.properties"));
		props.putAll(NodeTest.load("src/main/resources/custom.properties"));
		props.putAll(NodeTest.load("src/test/resources/satellite.properties"));
		props.putAll(NodeTest.load("src/test/resources/channel.properties"));
		satelliteConfig = new Configuration(props);
		satelliteTransceiver = new NullTransceiver(satelliteConfig);
		satelliteTransceiver.start();
		satelliteCallback = new MockNodeCallback();
		satelliteDataCallback = new MockUserDataCallback();
		satellite = new Node(satelliteConfig, satelliteTransceiver, satelliteCallback, satelliteDataCallback);

		groundTransceiver.setOtherSide(satelliteTransceiver);
		satelliteTransceiver.setOtherSide(groundTransceiver);

		ground.start();
		satellite.start();
	}

	@After
	public void stop() {
		satellite.stop();
		ground.stop();
		satelliteTransceiver.stop();
		groundTransceiver.stop();
	}
}
