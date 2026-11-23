/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.sx127x;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.CarrierModulation;
import com.leosatdata.model.Coding;
import com.leosatdata.model.Coherency;
import com.leosatdata.model.CommParameters;
import com.leosatdata.model.ErrorCode;
import com.leosatdata.model.LineCoding;
import com.leosatdata.model.Polarization;
import com.leosatdata.model.ScramblerType;
import com.leosatdata.model.TransceiverMode;
import com.leosatdata.service.Transceiver;
import com.leosatdata.service.TransceiverCallback;
import com.leosatdata.service.TransceiverRxState;
import com.leosatdata.service.TransceiverTxState;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.NamingThreadFactory;
import com.leosatdata.util.Util;

public class Sx127xTransceiver implements Transceiver {

	private final static Logger LOG = LoggerFactory.getLogger(Sx127xTransceiver.class);
	private final static int MAX_FIXED_LENGTH = 2047 - 4; // 4 is for crc32

	private final Sx127x impl;
	private final Configuration config;

	private TransceiverCallback callback;
	private ScheduledExecutorService executor;

	private TransceiverTxState tx = TransceiverTxState.OFF;
	private TransceiverRxState rx = TransceiverRxState.OFF;

	private Future<?> rxTask;
	private Object stateLock = new Object();
	private RxThreadState state;
	private CommParameters rxParams;
	private CommParameters txParams;
	private CommParameters currentCommParameters;

	public Sx127xTransceiver(Sx127x impl, Configuration config) {
		this.impl = impl;
		this.config = config;
		if (config.getBoolean("cs.sublayer.asm.prepend")) {
			throw new IllegalArgumentException("asm should be prepended by transceiver");
		}
	}

	public void start() {
		impl.start();
		impl.reset();
		impl.resetUart();

		impl.sx127x_set_opmod(new OpMode(sx127x_mode_t.SX127X_MODE_SLEEP, sx127x_modulation_t.SX127X_MODULATION_FSK));
		impl.sx127x_rx_set_lna_gain(convertValidateGain(config.getProperty("sx127x_gain")));
		impl.sx127x_rx_set_lna_boost_hf(config.getBoolean("sx127x_gain_boost"));
		impl.sx127x_fsk_ook_set_syncword(config.getProperty("cs.sublayer.asm"));
		impl.sx127x_set_preamble_length(config.getInteger("sx127x_preamble_length", 0));
		impl.sx127x_fsk_set_data_shaping(new FskDataShaping(convertBt(config.getProperty("sx127x_bt")), sx127x_pa_ramp_t.SX127X_PA_RAMP_5));
		String preambleType = config.getProperty("sx127x_preamble_type");
		if (preambleType != null) {
			impl.sx127x_fsk_ook_set_preamble_type(convertPreambleType(preambleType));
		}
		// sx127x produce crc16, not crc32
		// Generate CRC32 in CSSublayer according to CCSDS 211.2
		impl.sx127x_fsk_ook_set_crc(sx127x_crc_type_t.SX127X_CRC_NONE);
		impl.sx127x_fsk_ook_set_packet_encoding(sx127x_packet_encoding_t.SX127X_NRZ);

		String paPin = config.getProperty("sx127x_pa_pin");
		if (paPin != null) {
			impl.sx127x_tx_set_pa_config(new PaConfig(convertPaPin(paPin), config.getInteger("sx127x_power", 4)));
		}

		int uFrameVersion = config.getInteger("default_frame_type", 0);
		if (uFrameVersion != TransceiverMode.USLP.getCode()) {
			throw new IllegalArgumentException("only fixed-length transfer frames (USLP) are supported: default_frame_type=" + uFrameVersion);
		}
		if (!config.getBoolean("framev4.fixedLength")) {
			throw new IllegalArgumentException("only fixed-length transfer frames (USLP) are supported: framev4.fixedLength=" + false);
		}
		int maxFrameLength = config.getInteger("maximum_frame_length", 0);
		if (maxFrameLength > MAX_FIXED_LENGTH) {
			throw new IllegalArgumentException("maximum_frame_length can be up to " + MAX_FIXED_LENGTH + ", got: " + maxFrameLength);
		}
		impl.sx127x_fsk_ook_set_packet_format(new PacketFormat(sx127x_packet_format_t.SX127X_FIXED, maxFrameLength + 4));
		executor = Executors.newScheduledThreadPool(1, new NamingThreadFactory("transceiver-" + config.getInteger("local_spacecraft_id", 0)));
	}

	public void stop() {
		executor.shutdown();
		impl.stop();
	}

	@Override
	public int send(byte[] data) {
		executor.execute(new Runnable() {

			@Override
			public void run() {
				impl.sx127x_fsk_ook_tx_set_for_transmission(Util.bytesToHex(data));
				impl.tx(sx127x_modulation_t.SX127X_MODULATION_FSK);
				callback.onTx();
			}
		});
		return 0;
	}

	private int setCommParameters(CommParameters params) {
		if (!acceptCommParameters(params)) {
			return ErrorCode.UNSUPPORTED.ordinal();
		}
		if (currentCommParameters != null && currentCommParameters.equals(params)) {
			return ErrorCode.SUCCESS.ordinal();
		}
		int code = impl.sx127x_set_opmod(new OpMode(sx127x_mode_t.SX127X_MODE_STANDBY, sx127x_modulation_t.SX127X_MODULATION_FSK));
		if (code != 0) {
			return code;
		}
		if (currentCommParameters == null || currentCommParameters.getSymbolRate() != params.getSymbolRate()) {
			code = impl.sx127x_fsk_ook_set_bitrate(params.getSymbolRate());
			if (code != 0) {
				return code;
			}
			code = impl.sx127x_fsk_set_fdev(params.getSymbolRate() * config.getFloat("sx127x_modulation_index", 0.625f) / 2);
			if (code != 0) {
				return code;
			}
		}
		if (currentCommParameters == null || currentCommParameters.getFrequency() != params.getFrequency()) {
			code = impl.sx127x_set_frequency((long) params.getFrequency());
			if (code != 0) {
				return code;
			}
		}
		currentCommParameters = params;
		return ErrorCode.SUCCESS.ordinal();
	}

	private static boolean acceptCommParameters(CommParameters params) {
		if (!params.getCarrierModulation().equals(CarrierModulation.FSK)) {
			LOG.info("carrier modulation {} is not supported. only gmsk is supported", params.getCarrierModulation());
			return false;
		}
		if (params.getFrequency() < 137_000_000 || params.getFrequency() > 1020_000_000) {
			LOG.info("frequency {} outside of supported range: 137Mhz - 1020Mhz", params.getFrequency());
			return false;
		}
		if (params.getSymbolRate() < 1200 || params.getSymbolRate() > 300_000) {
			LOG.info("symbol rate {} outside of supported range: 1200 - 300000", params.getSymbolRate());
			return false;
		}
		if (params.isDifferentialEncoding()) {
			LOG.info("differential encoding is not supported");
			return false;
		}
		if (params.getCoding() != null && !params.getCoding().equals(Coding.UNCODED)) {
			LOG.info("{} encoding is not supported", params.getCoding());
			return false;
		}
		if (params.getPolarization() != null && !params.getPolarization().equals(Polarization.UNKNOWN)) {
			LOG.info("{} polarization is not supported", params.getPolarization());
			return false;
		}
		if (params.getScrambler() != null && !params.getScrambler().equals(ScramblerType.NONE)) {
			LOG.info("{} scrambler is not supported", params.getScrambler());
			return false;
		}
		if (params.getCoherency() != null && !params.getCoherency().equals(Coherency.NON_COHERENT)) {
			LOG.info("{} coherency is not supported", params.getCoherency());
			return false;
		}
		// sx127x apply manchester coding to the payload only
		// while ccsds 211.1-B-3 expect Bi-Phase-L (Manchester) applied right before/after RF modulator
		if (params.getLineCoding() != null && params.getLineCoding().equals(LineCoding.MANCHESTER)) {
			LOG.info("{} line coding is not supported", params.getLineCoding());
			return false;
		}
		return true;
	}

	@Override
	public boolean acceptRxCommParameters(CommParameters params) {
		return acceptCommParameters(params);
	}

	@Override
	public int setRxCommParameters(CommParameters params) {
		if (!acceptCommParameters(params)) {
			return ErrorCode.UNSUPPORTED.ordinal();
		}
		this.rxParams = params;
		return ErrorCode.SUCCESS.ordinal();
	}

	@Override
	public boolean acceptTxCommParameters(CommParameters params) {
		return acceptCommParameters(params);
	}

	@Override
	public int setTxCommParameters(CommParameters params) {
		if (!acceptCommParameters(params)) {
			return ErrorCode.UNSUPPORTED.ordinal();
		}
		this.txParams = params;
		return ErrorCode.SUCCESS.ordinal();
	}

	@Override
	public TransceiverCallback getCallback() {
		return callback;
	}

	@Override
	public void setCallback(TransceiverCallback callback) {
		this.callback = callback;
	}

	@Override
	public int setTxState(TransceiverTxState state) {
		int result = 0;
		if (state.equals(TransceiverTxState.OFF)) {
			if (rx.equals(TransceiverRxState.OFF)) {
				result = impl.sx127x_set_opmod(new OpMode(sx127x_mode_t.SX127X_MODE_STANDBY, sx127x_modulation_t.SX127X_MODULATION_FSK));
			}
		} else if (state.equals(TransceiverTxState.CARRIER_ONLY)) {
			stopRx();
			setCommParameters(txParams);
			result = impl.sx127x_set_opmod(new OpMode(sx127x_mode_t.SX127X_MODE_FSTX, sx127x_modulation_t.SX127X_MODULATION_FSK));
		} else if (state.equals(TransceiverTxState.MODULATION)) {
			stopRx();
			// there is no outgoing random signal here
		}
		this.tx = state;
		return result;
	}

	@Override
	public TransceiverTxState getTxState() {
		return tx;
	}

	@Override
	public int setRxState(TransceiverRxState state) {
		int result = 0;
		if (state.equals(TransceiverRxState.OFF)) {
			stopRx();
		} else {
			setCommParameters(rxParams);
			startRx();
			result = impl.sx127x_set_opmod(new OpMode(sx127x_mode_t.SX127X_MODE_RX_CONT, sx127x_modulation_t.SX127X_MODULATION_FSK));
		}
		this.rx = state;
		return result;
	}

	private void startRx() {
		state = RxThreadState.RUNNING;
		rxTask = executor.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				List<sx127x_frame_t> frames;
				synchronized (stateLock) {
					if (state.equals(RxThreadState.SHUTDOWN)) {
						return;
					}
					frames = impl.pull();
				}
				for (sx127x_frame_t cur : frames) {
					LOG.info("{} / {} - {}", cur.getRssi(), cur.getFrequency_error(), cur.getMessage());
					callback.onRx(Util.hexToBytes(cur.getMessage()));
				}
			}
		}, 0, config.getLong("interval_clock", 1000) / 2, TimeUnit.MILLISECONDS);
	}

	private void stopRx() {
		if (rxTask == null) {
			return;
		}
		rxTask.cancel(true);
		synchronized (stateLock) {
			state = RxThreadState.SHUTDOWN;
		}
		rxTask = null;
	}

	@Override
	public TransceiverRxState getRxState() {
		return rx;
	}

	@Override
	public int enableAsmRxCallback(boolean enable) {
		return ErrorCode.UNSUPPORTED.ordinal();
	}

	@Override
	public int enableAsmTxCallback(boolean enable) {
		return ErrorCode.UNSUPPORTED.ordinal();
	}

	private static sx127x_preamble_type_t convertPreambleType(String preambleType) {
		if (preambleType.equalsIgnoreCase("aa")) {
			return sx127x_preamble_type_t.SX127X_PREAMBLE_AA;
		} else if (preambleType.equalsIgnoreCase("55")) {
			return sx127x_preamble_type_t.SX127X_PREAMBLE_55;
		} else {
			throw new IllegalArgumentException("unsupported preamble type: " + preambleType + " expected: 55 or aa");
		}
	}

	private static sx127x_pa_pin_t convertPaPin(String str) {
		if (str.equalsIgnoreCase("boost")) {
			return sx127x_pa_pin_t.SX127X_PA_PIN_BOOST;
		} else if (str.equalsIgnoreCase("rfo")) {
			return sx127x_pa_pin_t.SX127X_PA_PIN_RFO;
		} else {
			throw new IllegalArgumentException("unsupported pa pin: " + str + " expected: boost or rfo");
		}
	}

	private static sx127x_fsk_data_shaping_t convertBt(String str) {
		if (str.equalsIgnoreCase("0.3")) {
			return sx127x_fsk_data_shaping_t.SX127X_BT_0_3;
		} else if (str.equalsIgnoreCase("0.5")) {
			return sx127x_fsk_data_shaping_t.SX127X_BT_0_5;
		} else if (str.equalsIgnoreCase("1.0")) {
			return sx127x_fsk_data_shaping_t.SX127X_BT_1_0;
		} else {
			throw new IllegalArgumentException("unsupported bt: " + str);
		}
	}

	private static int convertValidateGain(String str) {
		int result = Integer.parseInt(str);
		if (result < 0 || result > 6) {
			throw new IllegalArgumentException("invalid gain setting: " + str);
		}
		return result;
	}
}
