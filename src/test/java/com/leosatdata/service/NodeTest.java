package com.leosatdata.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.model.CarrierModulation;
import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;
import com.leosatdata.model.DataEncoding;
import com.leosatdata.model.DataModulation;
import com.leosatdata.model.DataRate;
import com.leosatdata.model.Frequency;
import com.leosatdata.model.Mode;
import com.leosatdata.model.ModeSelect;
import com.leosatdata.model.Modulation;
import com.leosatdata.model.PersistentActivity;
import com.leosatdata.model.PlExtensions;
import com.leosatdata.model.RsCodeType;
import com.leosatdata.model.ScramblerType;
import com.leosatdata.model.TxRxParameters;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.TestUtil;

public class NodeTest {

	private final static Logger LOG = LoggerFactory.getLogger(NodeTest.class);

	private Configuration groundConfig;
	private NullTransceiver groundTransceiver;
	private MockNodeCallback groundCallback;
	private MockUserDataCallback groundDataCallback;
	private Node ground;

	private Configuration ground2Config;
	private NullTransceiver ground2Transceiver;
	private MockNodeCallback ground2Callback;
	private MockUserDataCallback ground2DataCallback;
	private Node ground2;

	private Configuration satelliteConfig;
	private NullTransceiver satelliteTransceiver;
	private MockNodeCallback satelliteCallback;
	private MockUserDataCallback satelliteDataCallback;
	private Node satellite;

	@Test
	public void testNoConnection() throws Exception {
		ground.setCommParameters(createType1CommParameters());
		ground.startSession();
		assertEvent(groundCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), false);
	}

	@Test
	public void testValiteSourceFrame() throws Exception {
		// remote_spacecraft_id is configured
		establishSession();

		ground2.setCommParameters(createType1CommParameters());
		ground2Transceiver.setFrameFilter(null); // ignore rx/tx mismatch
		ground2.startSession();
		assertEvent(ground2Callback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), false);
		assertNotNull(satelliteCallback.waitForEvent(ControllerEventType.INVALID_FRAME_SOURCE));
	}

	@Test
	public void testValiteSourceFrame2() throws Exception {
		// remote_spacecraft_id will be taken from the first message
		satelliteConfig.removeProperty("remote_spacecraft_id");
		satellite.stop();
		satellite = new Node(satelliteConfig, satelliteTransceiver, satelliteCallback, satelliteDataCallback);
		satellite.start();
		establishSession();

		ground2.setCommParameters(createType1CommParameters());
		ground2Transceiver.setFrameFilter(null); // ignore rx/tx mismatch
		ground2.startSession();
		assertEvent(ground2Callback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), false);
		assertNotNull(satelliteCallback.waitForEvent(ControllerEventType.INVALID_FRAME_SOURCE));
	}

	@Test
	public void testDestinationFiltering() throws Exception {
		groundConfig.setProperty("remote_spacecraft_id", "5"); // not supported
		groundConfig.setProperty("source_destination_id", String.valueOf(SourceDestination.DESTINATION.getCode()));
		ground.stop();
		ground = new Node(groundConfig, groundTransceiver, groundCallback, groundDataCallback);
		ground.start();

		satellite.waitForSession();
		ground.setCommParameters(createType1CommParameters());
		ground.startSession();
		assertEvent(groundCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), false);
	}

	@Test
	public void testTemporaryCarrierLoss() throws Exception {
		establishSession();

		ground.onCarrierLost();
		satellite.onCarrierLost();

		Thread.sleep(groundConfig.getDuration("carrier_loss_timer_duration") / 2);

		ground.onCarrierAcquired();
		satellite.onCarrierAcquired();

		assertTestPacket();
	}

	@Test
	public void testReconnectAfterCarrierLoss() throws Exception {
		establishSession();

		ground.onCarrierLost();
		satellite.onCarrierLost();

		Thread.sleep(2 * groundConfig.getDuration("carrier_loss_timer_duration"));

		assertEvent(groundCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), true);
		assertEvent(satelliteCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), true);

		assertTestPacket();
	}

	@Test
	public void testStartSendStop() throws Exception {
		establishSession();

		testComm(ground, groundDataCallback, groundTransceiver, satellite, satelliteDataCallback, satelliteTransceiver);
		testComm(satellite, satelliteDataCallback, satelliteTransceiver, ground, groundDataCallback, groundTransceiver);

		satellite.stopSession();

		assertNotNull(groundCallback.waitForEvent(ControllerEventType.END_OF_SESSION));
		assertNotNull(satelliteCallback.waitForEvent(ControllerEventType.END_OF_SESSION));
	}

	private void testComm(Node from, MockUserDataCallback fromCallback, NullTransceiver fromTransceiver, Node to, MockUserDataCallback toCallback, NullTransceiver toTransceiver) throws Exception {
		int maxFrameLength = groundConfig.getInteger("maximum_frame_length", 0);
		QualityOfService qos[] = new QualityOfService[] { QualityOfService.EXPEDITED, QualityOfService.SEQUENCE_CONTROLLED };
		for (QualityOfService curQos : qos) {
			// test small packet
			byte[] packet = TestUtil.createTestData(20);
			from.send(packet, curQos, 0);
			byte[] actual = toCallback.waitForData();
			assertNotNull(actual);
			assertArrayEquals(packet, actual);

			// test packet span over several frames
			packet = TestUtil.createTestData(2 * maxFrameLength);
			from.send(packet, curQos, 0);
			actual = toCallback.waitForData();
			assertNotNull(actual);
			assertArrayEquals(packet, actual);
		}

		// test recovery
		byte[] packet = TestUtil.createTestData(3 * maxFrameLength);
		String payload = UUID.randomUUID().toString();
		byte[] payloadBytes = payload.getBytes();
		fromTransceiver.setFrameFilter(new FrameFilterOnce(payload));
		System.arraycopy(payloadBytes, 0, packet, maxFrameLength, payloadBytes.length);
		from.send(packet, QualityOfService.SEQUENCE_CONTROLLED, 0);

		byte[] actual = toCallback.waitForData(15000);
		assertNotNull(actual);
		assertArrayEquals(packet, actual);
	}

	@Before
	public void start() throws Exception {
		Properties props = new Properties();
		props.putAll(load("src/main/resources/mib.properties"));
		props.putAll(load("src/main/resources/transceiver.properties"));
		props.putAll(load("src/main/resources/custom.properties"));
		props.putAll(load("src/test/resources/ground.properties"));
		props.putAll(load("src/test/resources/channel.properties"));
		groundConfig = new Configuration(props);
		groundTransceiver = new NullTransceiver(groundConfig);
		groundTransceiver.start();
		groundCallback = new MockNodeCallback();
		groundDataCallback = new MockUserDataCallback();
		ground = new Node(groundConfig, groundTransceiver, groundCallback, groundDataCallback);

		props = new Properties();
		props.putAll(load("src/main/resources/mib.properties"));
		props.putAll(load("src/main/resources/transceiver.properties"));
		props.putAll(load("src/main/resources/custom.properties"));
		props.putAll(load("src/test/resources/ground2.properties"));
		props.putAll(load("src/test/resources/channel.properties"));
		ground2Config = new Configuration(props);
		ground2Transceiver = new NullTransceiver(ground2Config);
		ground2Transceiver.start();
		ground2Callback = new MockNodeCallback();
		ground2DataCallback = new MockUserDataCallback();
		ground2 = new Node(ground2Config, ground2Transceiver, ground2Callback, ground2DataCallback);

		props = new Properties();
		props.putAll(load("src/main/resources/mib.properties"));
		props.putAll(load("src/main/resources/transceiver.properties"));
		props.putAll(load("src/main/resources/custom.properties"));
		props.putAll(load("src/test/resources/satellite.properties"));
		props.putAll(load("src/test/resources/channel.properties"));
		satelliteConfig = new Configuration(props);
		satelliteTransceiver = new NullTransceiver(satelliteConfig);
		satelliteTransceiver.start();
		satelliteCallback = new MockNodeCallback();
		satelliteDataCallback = new MockUserDataCallback();
		satellite = new Node(satelliteConfig, satelliteTransceiver, satelliteCallback, satelliteDataCallback);

		groundTransceiver.setOtherSide(satelliteTransceiver);
		ground2Transceiver.setOtherSide(satelliteTransceiver);
		satelliteTransceiver.setOtherSide(groundTransceiver);

		ground.start();
		ground2.start();
		satellite.start();
	}

	@After
	public void stop() {
		groundTransceiver.stop();
		satelliteTransceiver.stop();
		satellite.stop();
		ground2.stop();
		ground.stop();
	}

	private void assertTestPacket() {
		byte[] packet = TestUtil.createTestData(20);
		ground.send(packet, QualityOfService.EXPEDITED, 0);
		byte[] actual = satelliteDataCallback.waitForData();
		assertNotNull(actual);
		assertArrayEquals(packet, actual);
	}

	private void establishSession() throws InterruptedException {
		satellite.waitForSession();
		ground.setCommParameters(createType1CommParameters());
		ground.startSession();
		assertEvent(groundCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), true);
		assertEvent(satelliteCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), true);

		// according to spec extra timeout between callback to vehicle and transitioning
		// to s40 state (data services)
		Thread.sleep(2 * (groundConfig.getDuration("carrier_only_duration") + groundConfig.getDuration("acquisition_idle_duration")));
	}

	private static Properties load(String file) throws Exception {
		Properties result = new Properties();
		try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
			result.load(is);
		}
		return result;
	}

	private static Type1Directive createType1CommParameters() {
		TxRxParameters tx = new TxRxParameters();
		tx.setDataEncoding(DataEncoding.CONVOLUTIONAL);
		tx.setDataRate(DataRate.R8C);
		tx.setFrequency(Frequency.CH3);
		tx.setMode(Mode.PROXIMITY1);
		tx.setModulation(Modulation.COHERENT_PSK);

		PlExtensions txExt = new PlExtensions();
		txExt.setCarrierModulation(CarrierModulation.PSK);
		txExt.setDataModulation(DataModulation.NRZ_L);
		txExt.setDifferentialEncoding(false);
		txExt.setModeSelect(ModeSelect.RESIDUAL_CARRIER);
		txExt.setRsCode(RsCodeType.RS255_239);
		txExt.setScrambler(ScramblerType.CCITT);
		txExt.setUseExtendedDataRate(false);
		txExt.setUseExtendedFreqSet(false);

		TxRxParameters rx = new TxRxParameters();
		rx.setDataEncoding(DataEncoding.CONVOLUTIONAL);
		rx.setDataRate(DataRate.R8C);
		rx.setFrequency(Frequency.CH3);
		rx.setMode(Mode.PROXIMITY1);
		rx.setModulation(Modulation.COHERENT_PSK);

		PlExtensions rxExt = new PlExtensions();
		rxExt.setCarrierModulation(CarrierModulation.PSK);
		rxExt.setDataModulation(DataModulation.NRZ_L);
		rxExt.setDifferentialEncoding(false);
		rxExt.setModeSelect(ModeSelect.RESIDUAL_CARRIER);
		rxExt.setRsCode(RsCodeType.RS255_239);
		rxExt.setScrambler(ScramblerType.CCITT);
		rxExt.setUseExtendedDataRate(false);
		rxExt.setUseExtendedFreqSet(false);

		Type1Directive result = new Type1Directive();
		result.setTxParams(tx);
		result.setTxPlExtensions(txExt);
		result.setRxParams(rx);
		result.setRxPlExtensions(rxExt);
		return result;
	}

	private static void assertEvent(ControllerEvent hail, boolean expectedStatus) {
		assertNotNull(hail);
		if (hail.getType().equals(ControllerEventType.PERSISTENT_ACTIVITY)) {
			PersistentActivity event = (PersistentActivity) hail.getObj();
			assertEquals(expectedStatus, event.isSuccess());
		}
	}
}
