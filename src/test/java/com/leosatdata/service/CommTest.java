package com.leosatdata.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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

public class CommTest {

	private Configuration groundConfig;
	private NullTransceiver groundTransceiver;
	private MockNodeCallback groundCallback;
	private Node ground;

	private Configuration satelliteConfig;
	private NullTransceiver satelliteTransceiver;
	private MockNodeCallback satelliteCallback;
	private Node satellite;

	@Test
	public void testNoConnection() throws Exception {
		ground.setCommParameters(createType1CommParameters());
		ground.startSession();
		assertPersistentActivity(groundCallback.waitForEvent(), false);
	}

	@Test
	public void testHail() throws Exception {
		satellite.waitForSession();
		ground.setCommParameters(createType1CommParameters());
		ground.startSession();
		assertPersistentActivity(groundCallback.waitForEvent(), true);
	}

	@Before
	public void start() throws Exception {
		Properties props = new Properties();
		props.putAll(load("src/main/resources/mib.properties"));
		props.putAll(load("src/main/resources/transceiver.properties"));
		props.putAll(load("src/main/resources/custom.properties"));
		props.putAll(load("src/test/resources/ground.properties"));
		groundConfig = new Configuration(props);
		groundTransceiver = new NullTransceiver(groundConfig);
		groundTransceiver.start();
		groundCallback = new MockNodeCallback();
		ground = new Node(groundConfig, groundTransceiver, groundCallback, new UserDataCallback() {

			@Override
			public void onUserData(byte[] payload) {
				// TODO Auto-generated method stub

			}
		});

		props = new Properties();
		props.putAll(load("src/main/resources/mib.properties"));
		props.putAll(load("src/main/resources/transceiver.properties"));
		props.putAll(load("src/main/resources/custom.properties"));
		props.putAll(load("src/test/resources/satellite.properties"));
		satelliteConfig = new Configuration(props);
		satelliteTransceiver = new NullTransceiver(satelliteConfig);
		satelliteTransceiver.start();
		satelliteCallback = new MockNodeCallback();
		satellite = new Node(satelliteConfig, satelliteTransceiver, satelliteCallback, new UserDataCallback() {

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
		groundTransceiver.stop();
		satelliteTransceiver.stop();
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

	private static void assertPersistentActivity(ControllerEvent hail, boolean expectedStatus) {
		assertNotNull(hail);
		assertEquals(ControllerEventType.PERSISTENT_ACTIVITY, hail.getType());
		PersistentActivity event = (PersistentActivity) hail.getObj();
		assertEquals(expectedStatus, event.isSuccess());
	}

}
