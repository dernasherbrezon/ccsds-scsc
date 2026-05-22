package com.leosatdata.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.model.CarrierModulation;
import com.leosatdata.model.Coding;
import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;
import com.leosatdata.model.DataEncoding;
import com.leosatdata.model.DataModulation;
import com.leosatdata.model.DataRate;
import com.leosatdata.model.Duplex;
import com.leosatdata.model.Frequency;
import com.leosatdata.model.Link2Establishment;
import com.leosatdata.model.LinkDirection;
import com.leosatdata.model.LinkEstablishment;
import com.leosatdata.model.LinkModulation;
import com.leosatdata.model.Mode;
import com.leosatdata.model.ModeSelect;
import com.leosatdata.model.Modulation;
import com.leosatdata.model.ModulationIndex;
import com.leosatdata.model.NegotiationType;
import com.leosatdata.model.NegotiationType2;
import com.leosatdata.model.PersistentActivity;
import com.leosatdata.model.PlExtensions;
import com.leosatdata.model.Plcw;
import com.leosatdata.model.Polarization;
import com.leosatdata.model.QueryResponse;
import com.leosatdata.model.RsCodeType;
import com.leosatdata.model.ScramblerType;
import com.leosatdata.model.Spdu;
import com.leosatdata.model.TransceiverMode;
import com.leosatdata.model.TxRxParameters;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type4Directive;
import com.leosatdata.model.Type5Directive;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.CommSettings;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.SpduParser;
import com.leosatdata.util.TestUtil;

@RunWith(Parameterized.class)
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

	private Duplex duplex;

	public NodeTest(Duplex duplex) {
		this.duplex = duplex;
	}

	@Test
	public void testStartStopStartSession() throws Exception {
		establishType1Session();

		assertTestExpPacket();

		satellite.stopSession();
		ground.stopSession();

		assertNotNull(groundCallback.waitForEvent(ControllerEventType.END_OF_SESSION));
		assertNotNull(satelliteCallback.waitForEvent(ControllerEventType.END_OF_SESSION));

		assertEquals(TransceiverTxState.OFF, groundTransceiver.getTxState());
		assertEquals(TransceiverTxState.OFF, satelliteTransceiver.getTxState());
		assertEquals(TransceiverRxState.OFF, groundTransceiver.getRxState());
		assertEquals(TransceiverRxState.OFF, satelliteTransceiver.getRxState());

		satellite.waitForSession();
		ground.startSession();

		assertEvent(groundCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), true);
		assertEvent(satelliteCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), true);

		waitPersistenceActivityCompletion();

		assertTestExpPacket();
	}

	@Test
	public void testResync1() throws Exception {
		establishType1Session();

		assertTestSeqPacket(1);

		// less than expected
		ground.onRx(createPlcw(false, 0));
		assertEvent(groundCallback.waitForEvent(ControllerEventType.COP_P_LOSS_SYNCHRONIZATION), true);

		assertTestSeqPacket(2);
	}

	@Test
	public void testResync2() throws Exception {
		establishType1Session();

		assertTestSeqPacket(1);

		// more than expected
		ground.onRx(createPlcw(false, 3));
		assertEvent(groundCallback.waitForEvent(ControllerEventType.COP_P_LOSS_SYNCHRONIZATION), true);

		assertTestSeqPacket(2);
	}

	@Test
	public void testResync3() throws Exception {
		establishType1Session();

		assertTestSeqPacket(1);

		ground.onRx(createPlcw(true, 1));
		assertEvent(groundCallback.waitForEvent(ControllerEventType.COP_P_LOSS_SYNCHRONIZATION), true);

		assertTestSeqPacket(2);
	}

	@Test
	public void testNoConnection() throws Exception {
		ground.setCommParameters(createType1CommParameters());
		ground.startSession();
		assertEvent(groundCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), false);
	}

	@Test
	public void testCommChange() throws Exception {
		establishType1Session();

		// switch to the different tx channel. rx stays the same
		TxRxParameters tx = new TxRxParameters();
		tx.setDataEncoding(DataEncoding.CONVOLUTIONAL);
		tx.setDataRate(DataRate.R8C);
		tx.setFrequency(Frequency.CH5);
		tx.setMode(Mode.PROXIMITY1);
		tx.setModulation(Modulation.COHERENT_PSK);
		Type1Directive result = new Type1Directive();
		result.setTxParams(tx);

		ground.setCommParameters(result);

		assertEvent(groundCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), true);
		waitPersistenceActivityCompletion();
		assertTestExpPacket();
	}

	@Test
	public void testValidateSourceFrame() throws Exception {
		// remote_spacecraft_id is configured
		establishType1Session();

		ground2.setCommParameters(createType1CommParameters());
		ground2Transceiver.setTxFrameFilter(null); // ignore rx/tx mismatch
		ground2Transceiver.setSkipCallbacks(true);
		ground2.startSession();
		assertEvent(ground2Callback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), false);
		assertNotNull(satelliteCallback.waitForEvent(ControllerEventType.INVALID_FRAME_SOURCE));
	}

	@Test
	public void testValidateSourceFrame2() throws Exception {
		// remote_spacecraft_id will be taken from the first message
		satelliteConfig.removeProperty("remote_spacecraft_id");
		satellite.stop();
		satellite = new Node(satelliteConfig, satelliteTransceiver, satelliteCallback, satelliteDataCallback);
		satellite.start();
		establishType1Session();

		ground2.setCommParameters(createType1CommParameters());
		ground2Transceiver.setTxFrameFilter(null); // ignore rx/tx mismatch
		ground2Transceiver.setSkipCallbacks(true);
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
		establishType1Session();

		ground.onCarrierLost();
		satellite.onCarrierLost();

		Thread.sleep(groundConfig.getDuration("carrier_loss_timer_duration") / 2);

		ground.onCarrierAcquired();
		satellite.onCarrierAcquired();

		assertTestExpPacket();
	}

	@Test
	public void testReconnectAfterCarrierLoss() throws Exception {
		establishType1Session();

		ground.onCarrierLost();
		satellite.onCarrierLost();

		Thread.sleep(2 * groundConfig.getDuration("carrier_loss_timer_duration"));

		assertNotNull(groundCallback.waitForEvent(ControllerEventType.CARRIER_LOSS));
		assertNotNull(satelliteCallback.waitForEvent(ControllerEventType.CARRIER_LOSS));

		// then automatic recover

		assertEvent(groundCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), true);
		assertEvent(satelliteCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), true);

		assertTestExpPacket();
	}

	@Test
	public void testPrioritization() throws Exception {
		establishType1Session();

		PausingFrameFilter pausingFilter = new PausingFrameFilter();
		satelliteTransceiver.setTxFrameFilter(pausingFilter);

		byte[] expedited = new byte[1];
		expedited[0] = (byte) QualityOfService.EXPEDITED.getCode();

		byte[] sequenced = new byte[1];
		sequenced[0] = (byte) QualityOfService.SEQUENCE_CONTROLLED.getCode();

		satellite.send(expedited, QualityOfService.EXPEDITED, 0);
		satellite.send(sequenced, QualityOfService.SEQUENCE_CONTROLLED, 0);
		satellite.send(expedited, QualityOfService.EXPEDITED, 0);

		groundDataCallback.setCollectMulti(true);

		pausingFilter.resume();

		int numberOfPackets = 3;
		List<byte[]> packets = groundDataCallback.waitForData(numberOfPackets, 15000);
		assertEquals(numberOfPackets, packets.size());
		// skip first packet, because its order is dependant on the current state of
		// state machine
		// check only 2 remaining packets, because they must be re-ordered
		assertEquals((byte) QualityOfService.EXPEDITED.getCode(), packets.get(1)[0]);
		assertEquals((byte) QualityOfService.SEQUENCE_CONTROLLED.getCode(), packets.get(2)[0]);
	}

	@Test
	public void testSequenceOverflow1() throws Exception {
		// make test a little bit faster
		groundConfig.setProperty("maximum_frame_length", "255");
		ground.stop();
		ground = new Node(groundConfig, groundTransceiver, groundCallback, groundDataCallback);
		ground.start();

		satelliteConfig.setProperty("maximum_frame_length", "255");
		satellite.stop();
		satellite = new Node(satelliteConfig, satelliteTransceiver, satelliteCallback, satelliteDataCallback);
		satellite.start();

		establishType1Session(DataRate.R256C);

		assertSequenceOverflowPackets();
	}

	@Test
	public void testSequenceOverflow2() throws Exception {
		// make test a little bit faster
		groundConfig.setProperty("maximum_frame_length", "255");
		groundConfig.setProperty("u_frame_version", "4");
		groundConfig.setProperty("frame_version", "4");
		groundConfig.setProperty("spdu_version", "2");
		groundConfig.setProperty("maximum_frame_sequence_length", "2");

		ground.stop();
		ground = new Node(groundConfig, groundTransceiver, groundCallback, groundDataCallback);
		ground.start();

		satelliteConfig.setProperty("maximum_frame_length", "255");
		satelliteConfig.setProperty("u_frame_version", "4");
		satelliteConfig.setProperty("frame_version", "4");
		satelliteConfig.setProperty("spdu_version", "2");
		satelliteConfig.setProperty("maximum_frame_sequence_length", "2");

		satellite.stop();
		satellite = new Node(satelliteConfig, satelliteTransceiver, satelliteCallback, satelliteDataCallback);
		satellite.start();

		establishType4Session();

		assertSequenceOverflowPackets();
	}

	private void assertSequenceOverflowPackets() {
		int maxFrameLength = groundConfig.getInteger("maximum_frame_length", 0);
		// test sequence reset
		// 2 is important here, it will ensure packet will be partially transffered
		// across sequence overflow
		int rate = 2;
		byte[] packet = TestUtil.createTestData(rate * maxFrameLength);
		int maxNumber = groundConfig.getInteger("maximum_frame_sequence_length", 1) == 1 ? 0xFF : 0xFFFF;
		resetSequences(maxNumber - 10);
		int totalPackets = 10;
		LOG.info("sending {} packets. each ~ {} frames", totalPackets, (rate + 1));
		for (int i = 0; i < totalPackets; i++) {
			ground.send(packet, QualityOfService.SEQUENCE_CONTROLLED, 0);
			byte[] actual = satelliteDataCallback.waitForData();
			assertNotNull(actual);
			assertArrayEquals(packet, actual);
		}
	}

	@Test
	public void testStartSendStop() throws Exception {
		for (CommSettings cur : getSupportedSettingsCombination()) {
			LOG.info("testing: {}", cur);
			groundConfig.setProperty("u_frame_version", String.valueOf(cur.getuFrameVersion()));
			groundConfig.setProperty("spdu_version", String.valueOf(cur.getSpduVersion()));
			groundConfig.setProperty("frame_version", String.valueOf(cur.getpFrameVersion()));
			groundConfig.setProperty("maximum_frame_sequence_length", String.valueOf(cur.getMaxFrameSequenceLength()));

			satelliteConfig.setProperty("u_frame_version", String.valueOf(cur.getuFrameVersion()));
			satelliteConfig.setProperty("spdu_version", String.valueOf(cur.getSpduVersion()));
			satelliteConfig.setProperty("frame_version", String.valueOf(cur.getpFrameVersion()));
			satelliteConfig.setProperty("maximum_frame_sequence_length", String.valueOf(cur.getMaxFrameSequenceLength()));

			ground.stop();
			ground = new Node(groundConfig, groundTransceiver, groundCallback, groundDataCallback);
			ground.start();

			satellite.stop();
			satellite = new Node(satelliteConfig, satelliteTransceiver, satelliteCallback, satelliteDataCallback);
			satellite.start();

			if (cur.getSpduVersion() == 1) {
				establishType1Session();
			} else if (cur.getSpduVersion() == 2) {
				establishType4Session();
			} else if (cur.getSpduVersion() == 3) {
				establishType5Session();
			} else {
				throw new IllegalArgumentException("unsupported: " + cur.getuFrameVersion());
			}

			testComm(ground, groundTransceiver, satelliteDataCallback);
			testComm(satellite, satelliteTransceiver, groundDataCallback);

			satellite.stopSession();
			ground.stopSession();

			assertNotNull(groundCallback.waitForEvent(ControllerEventType.END_OF_SESSION));
			assertNotNull(satelliteCallback.waitForEvent(ControllerEventType.END_OF_SESSION));
		}
	}

	private void testComm(Node from, NullTransceiver fromTransceiver, MockUserDataCallback toCallback) throws Exception {
		int maxFrameLength = groundConfig.getInteger("maximum_frame_length", 0);
		QualityOfService qos[] = new QualityOfService[] { QualityOfService.EXPEDITED, QualityOfService.SEQUENCE_CONTROLLED };
		// extra timeout for half-duplex
		long timeout = duplex.equals(Duplex.FULL_DUPLEX) ? 10000 : 20000;
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
			actual = toCallback.waitForData(timeout);
			assertNotNull(actual);
			assertArrayEquals(packet, actual);
		}

		// test recovery
		byte[] packet = TestUtil.createTestData(3 * maxFrameLength);
		String payload = UUID.randomUUID().toString();
		byte[] payloadBytes = payload.getBytes();
		fromTransceiver.setTxFrameFilter(new FrameFilterOnce(payload));
		System.arraycopy(payloadBytes, 0, packet, maxFrameLength, payloadBytes.length);
		from.send(packet, QualityOfService.SEQUENCE_CONTROLLED, 0);

		timeout = duplex.equals(Duplex.FULL_DUPLEX) ? 15000 : 60000;
		byte[] actual = toCallback.waitForData(timeout);
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
		if (duplex.equals(Duplex.HALF_DUPLEX)) {
			groundTransceiver = new HalfDuplexTransceiver(groundConfig);
		} else {
			groundTransceiver = new NullTransceiver(groundConfig);
		}
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
		if (duplex.equals(Duplex.HALF_DUPLEX)) {
			ground2Transceiver = new HalfDuplexTransceiver(ground2Config);
		} else {
			ground2Transceiver = new NullTransceiver(ground2Config);
		}
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
		if (duplex.equals(Duplex.HALF_DUPLEX)) {
			satelliteTransceiver = new HalfDuplexTransceiver(satelliteConfig);
		} else {
			satelliteTransceiver = new NullTransceiver(satelliteConfig);
		}
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
		satellite.stop();
		ground2.stop();
		ground.stop();
		satelliteTransceiver.stop();
		groundTransceiver.stop();
	}

	private void assertTestExpPacket() {
		byte[] packet = TestUtil.createTestData(20);
		ground.send(packet, QualityOfService.EXPEDITED, 0);
		byte[] actual = satelliteDataCallback.waitForData();
		assertNotNull(actual);
		assertArrayEquals(packet, actual);
	}

	private void assertTestSeqPacket(int vr) {
		CountDownLatch latch = new CountDownLatch(1);
		groundTransceiver.setRxFrameFilter(new ParsedFrameFilter(groundConfig) {
			@Override
			boolean onSpdu(Spdu spdu) {
				if (!(spdu instanceof Plcw)) {
					return true;
				}
				Plcw plcw = (Plcw) spdu;
				if (plcw.getReportValue() == vr) {
					latch.countDown();
				}
				return true;
			}

			@Override
			boolean onUserData(Object frame) {
				return true;
			}
		});
		byte[] packet = TestUtil.createTestData(20);
		ground.send(packet, QualityOfService.SEQUENCE_CONTROLLED, 0);
		int timeoutMillis = 10000;
		byte[] actual = satelliteDataCallback.waitForData(timeoutMillis);
		assertNotNull(actual);
		assertArrayEquals(packet, actual);
		// wait for ack to the sender
		try {
			latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void establishType5Session() throws InterruptedException {
		satellite.waitForSession();
		ground.setCommParameters(createType5CommParameters());
		ground.startSession();
		assertEvent(groundCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), true);
		assertEvent(satelliteCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), true);

		waitPersistenceActivityCompletion();
	}

	private void establishType4Session() throws InterruptedException {
		satellite.waitForSession();
		ground.setCommParameters(createType4CommParameters());
		ground.startSession();
		assertEvent(groundCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), true);
		assertEvent(satelliteCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), true);

		waitPersistenceActivityCompletion();
	}

	private void establishType1Session() throws InterruptedException {
		establishType1Session(DataRate.R8C);
	}

	public static List<CommSettings> getSupportedSettingsCombination() {
		List<CommSettings> settings = new ArrayList<>();
		// different combinations of transfer frames, spdu directives and seq numbers
		settings.add(new CommSettings(3, 3, 1, 1));
		settings.add(new CommSettings(4, 4, 1, 1));
		settings.add(new CommSettings(4, 4, 2, 1));
		settings.add(new CommSettings(4, 4, 3, 1));
		settings.add(new CommSettings(4, 4, 2, 2));
		settings.add(new CommSettings(4, 4, 3, 2));
		return settings;
	}

	// @formatter:off
	@Parameterized.Parameters(name = "{0}")
	public static Collection<?> parameters() {
		return Arrays.asList(new Object[][] { 
			{ Duplex.HALF_DUPLEX }, 
//			{ Duplex.FULL_DUPLEX } 
		});
	}
	// @formatter:on

	private void establishType1Session(DataRate dataRate) throws InterruptedException {
		satellite.setDuplex(duplex);
		satellite.waitForSession();
		ground.setDuplex(duplex);
		ground.setCommParameters(createType1CommParameters(dataRate));
		ground.startSession();
		assertEvent(groundCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), true);
		assertEvent(satelliteCallback.waitForEvent(ControllerEventType.PERSISTENT_ACTIVITY), true);

		waitPersistenceActivityCompletion();
	}

	private void resetSequences(int seq) {
		ground.getState().setVs(seq);
		ground.getState().setVvs(seq);
		ground.getState().setNnr(seq);
		ground.getState().setVr(seq);
		ground.setVrFromCallback(seq);
		satellite.getState().setVs(seq);
		satellite.getState().setVvs(seq);
		satellite.getState().setNnr(seq);
		satellite.getState().setVr(seq);
		satellite.setVrFromCallback(seq);
	}

	private void waitPersistenceActivityCompletion() throws InterruptedException {
		// according to the spec extra timeout between callback to vehicle and
		// transitioning
		// to s40 state (data services)
		Thread.sleep(2 * (groundConfig.getDuration("carrier_only_duration") + groundConfig.getDuration("acquisition_idle_duration")));
	}

	public static Properties load(String file) throws Exception {
		Properties result = new Properties();
		try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
			result.load(is);
		}
		return result;
	}

	private static Type1Directive createType1CommParameters() {
		return createType1CommParameters(DataRate.R8C);
	}

	public static Type5Directive createType5CommParameters() {
		Link2Establishment rx = new Link2Establishment();
		rx.setAosFrameLength(0);
		rx.setCoding(Coding.CONVOLUTIONAL);
		rx.setCoherency(Modulation.COHERENT_PSK);
		rx.setDemandQuery(NegotiationType2.DEMAND);
		rx.setDirection(LinkDirection.RETURN);
		rx.setFrequency(2_100_000_000f);
		rx.setLinkSnr(0);
		rx.setModulation(LinkModulation.GMSK);
		rx.setModulationIndex(ModulationIndex.M0_4);
		rx.setPolarization(Polarization.LHCP);
		rx.setRnmd(false);
		rx.setSymbolRate(9600);
		rx.setTransceiverMode(TransceiverMode.USLP);
		rx.setTransmitToken(false);

		Link2Establishment tx = new Link2Establishment();
		tx.setAosFrameLength(0);
		tx.setCoding(Coding.CONVOLUTIONAL);
		tx.setCoherency(Modulation.COHERENT_PSK);
		tx.setDemandQuery(NegotiationType2.DEMAND);
		tx.setDirection(LinkDirection.FORWARD);
		tx.setFrequency(2_200_000_000f);
		tx.setLinkSnr(0);
		tx.setModulation(LinkModulation.GMSK);
		tx.setModulationIndex(ModulationIndex.M0_4);
		tx.setPolarization(Polarization.LHCP);
		tx.setRnmd(false);
		tx.setSymbolRate(9600);
		tx.setTransceiverMode(TransceiverMode.USLP);
		tx.setTransmitToken(false);

		Type5Directive result = new Type5Directive();
		result.setRxLink(rx);
		result.setTxLink(tx);
		return result;
	}

	public static Type4Directive createType4CommParameters() {
		LinkEstablishment rx = new LinkEstablishment();
		rx.setCoding(Coding.CONVOLUTIONAL);
		rx.setDemandQuery(NegotiationType.DEMAND);
		rx.setDirection(LinkDirection.RETURN);
		rx.setFreqChannel(1);
		rx.setLinkSnr(0);
		rx.setModulation(LinkModulation.GMSK);
		rx.setModulationIndex(ModulationIndex.M0_4);
		rx.setPolarization(Polarization.LHCP);
		rx.setQueryResponse(QueryResponse.ACK);
		rx.setRnmd(false);
		rx.setSymbolRate(9600);
		rx.setTransmitToken(false);

		LinkEstablishment tx = new LinkEstablishment();
		tx.setCoding(Coding.CONVOLUTIONAL);
		tx.setDemandQuery(NegotiationType.DEMAND);
		tx.setDirection(LinkDirection.FORWARD);
		tx.setFreqChannel(1);
		tx.setLinkSnr(0);
		tx.setModulation(LinkModulation.GMSK);
		tx.setModulationIndex(ModulationIndex.M0_4);
		tx.setPolarization(Polarization.LHCP);
		tx.setQueryResponse(QueryResponse.ACK);
		tx.setRnmd(false);
		tx.setSymbolRate(9600);
		tx.setTransmitToken(false);

		Type4Directive result = new Type4Directive();
		result.setRxLink(rx);
		result.setTxLink(tx);
		return result;
	}

	public static Type1Directive createType1CommParameters(DataRate dataRate) {
		TxRxParameters tx = new TxRxParameters();
		tx.setDataEncoding(DataEncoding.CONVOLUTIONAL);
		tx.setDataRate(dataRate);
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
		rx.setDataRate(dataRate);
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

	private byte[] createPlcw(boolean retransmit, int reportValue) throws IOException {
		com.leosatdata.ccsds2110b6.util.TransferFrameParser framev3Parser = new com.leosatdata.ccsds2110b6.util.TransferFrameParser(satelliteConfig);
		SpduParser spduParser = new SpduParser(satelliteConfig);

		Plcw plcw = new Plcw();
		plcw.setReportValue(reportValue);
		plcw.setRetransmit(retransmit);
		plcw.setExpeditedFrameCounter(0);
		plcw.setPcid(0);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (BitOutputStream bos = new BitOutputStream(baos)) {
			spduParser.encode(plcw, bos);
		}
		com.leosatdata.ccsds2110b6.model.TransferFrame frame = framev3Parser.createPFrame(baos.toByteArray());
		baos = new ByteArrayOutputStream();
		try (BitOutputStream bos = new BitOutputStream(baos)) {
			com.leosatdata.ccsds2110b6.util.TransferFrameParser.encode(frame, bos);
		}
		CsSublayer csSublayer = new CsSublayer(satelliteConfig);
		return csSublayer.encode(baos.toByteArray());
	}

	private static void assertEvent(ControllerEvent hail, boolean expectedStatus) {
		assertNotNull(hail);
		if (hail.getType().equals(ControllerEventType.PERSISTENT_ACTIVITY)) {
			PersistentActivity event = (PersistentActivity) hail.getObj();
			assertEquals(expectedStatus, event.isSuccess());
		}
	}
}
