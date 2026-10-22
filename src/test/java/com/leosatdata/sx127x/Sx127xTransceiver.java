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

import com.leosatdata.model.CarrierModulation;
import com.leosatdata.model.Coding;
import com.leosatdata.model.CommParameters;
import com.leosatdata.model.LineCoding;
import com.leosatdata.model.Polarization;
import com.leosatdata.model.ScramblerType;
import com.leosatdata.service.Transceiver;
import com.leosatdata.service.TransceiverCallback;
import com.leosatdata.service.TransceiverRxState;
import com.leosatdata.service.TransceiverTxState;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.NamingThreadFactory;
import com.leosatdata.util.Util;

public class Sx127xTransceiver implements Transceiver {

	private final Sx127x impl;
	private final Configuration config;

	private TransceiverCallback callback;
	private ScheduledExecutorService executor;

	private TransceiverTxState tx = TransceiverTxState.OFF;
	private TransceiverRxState rx = TransceiverRxState.OFF;

	private Future<?> rxTask;
	private Object stateLock = new Object();
	private RxThreadState state;

	public Sx127xTransceiver(Sx127x impl, Configuration config) {
		this.impl = impl;
		this.config = config;
		if (config.getBoolean("cs.sublayer.asm.prepend")) {
			throw new IllegalArgumentException("asm should be prepended by transceiver");
		}
		if (config.getBoolean("cs.sublayer.crc.append")) {
			throw new IllegalArgumentException("crc should be appended by transceiver");
		}
	}

	public void start() {
		impl.start();
		impl.reset();
		impl.resetUart();

		impl.sx127x_set_opmod(new OpMode(sx127x_mode_t.SX127X_MODE_SLEEP, sx127x_modulation_t.SX127X_MODULATION_FSK));
		impl.sx127x_fsk_ook_set_syncword(config.getProperty("cs.sublayer.asm"));
		// This is not crc32 as stated by the CCSDS 211.2
		impl.sx127x_fsk_ook_set_crc(sx127x_crc_type_t.SX127X_CRC_CCITT);
		// extra parameters can be configured in the property file
		impl.sx127x_tx_set_pa_config(new PaConfig(sx127x_pa_pin_t.SX127X_PA_PIN_BOOST, 4));

		int uFrameVersion = config.getInteger("u_frame_version", 0);
		if (uFrameVersion == 2) {
			throw new IllegalArgumentException("only variable transfer frames are supported: u_frame_version=" + uFrameVersion);
		}
		int maxFrameLength = config.getInteger("maximum_frame_length", 0);
		if (maxFrameLength > 255) {
			throw new IllegalArgumentException("maximum_frame_length can be up to 255, got: " + maxFrameLength);
		}
		impl.sx127x_fsk_ook_set_packet_format(new PacketFormat(sx127x_packet_format_t.SX127X_VARIABLE, maxFrameLength));
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
		int code = impl.sx127x_fsk_ook_set_bitrate(params.getSymbolRate());
		if (code != 0) {
			return code;
		}
		if (params.getLineCoding().equals(LineCoding.NRZ_L)) {
			code = impl.sx127x_fsk_ook_set_packet_encoding(sx127x_packet_encoding_t.SX127X_NRZ);
		} else if (params.getLineCoding().equals(LineCoding.MANCHESTER)) {
			code = impl.sx127x_fsk_ook_set_packet_encoding(sx127x_packet_encoding_t.SX127X_MANCHESTER);
		} else {
			code = impl.sx127x_fsk_ook_set_packet_encoding(sx127x_packet_encoding_t.SX127X_SCRAMBLED);
		}
		if (code != 0) {
			return code;
		}
		code = impl.sx127x_set_frequency((long) params.getFrequency());
		if (code != 0) {
			return code;
		}
		if (params.getScrambler() != null && !params.getScrambler().equals(ScramblerType.NONE)) {
			return 1;
		}
		if (params.getPolarization() != null && !params.getPolarization().equals(Polarization.UNKNOWN)) {
			return 1;
		}
		if (params.getEncoding() != null && !params.getEncoding().equals(Coding.UNCODED)) {
			return 1;
		}
		if (params.getCarrierModulation() != null && !params.getCarrierModulation().equals(CarrierModulation.FSK)) {
			return 1;
		}
		if (params.isDifferentialEncoding()) {
			return 1;
		}
		return 0;
	}

	@Override
	public int setRxCommParameters(CommParameters params) {
		return setCommParameters(params);
	}

	// for now both rx and tx are the same
	// can be different though
	@Override
	public int setTxCommParameters(CommParameters params) {
		return setCommParameters(params);
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
				synchronized (stateLock) {
					if (state.equals(RxThreadState.SHUTDOWN)) {
						return;
					}
					List<sx127x_frame_t> frames = impl.pull();
					for (sx127x_frame_t cur : frames) {
						callback.onRx(Util.hexToBytes(cur.getMessage()));
					}
				}
			}
		}, 1000, 1000, TimeUnit.MILLISECONDS);
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
		return 0;
	}

	@Override
	public int enableAsmTxCallback(boolean enable) {
		return 0;
	}

}
