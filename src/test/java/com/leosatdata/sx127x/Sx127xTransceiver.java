package com.leosatdata.sx127x;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.leosatdata.model.CarrierModulation;
import com.leosatdata.model.Coding;
import com.leosatdata.model.Coherency;
import com.leosatdata.model.LineCoding;
import com.leosatdata.model.ModulationIndex;
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

	@Override
	public int setRxSymbolRate(float baud) {
		return impl.sx127x_fsk_ook_set_bitrate(baud);
	}

	@Override
	public int setRxLineCoding(LineCoding modulation) {
		if (modulation.equals(LineCoding.NRZ_L)) {
			return impl.sx127x_fsk_ook_set_packet_encoding(sx127x_packet_encoding_t.SX127X_NRZ);
		} else if (modulation.equals(LineCoding.MANCHESTER)) {
			return impl.sx127x_fsk_ook_set_packet_encoding(sx127x_packet_encoding_t.SX127X_MANCHESTER);
		} else {
			return impl.sx127x_fsk_ook_set_packet_encoding(sx127x_packet_encoding_t.SX127X_SCRAMBLED);
		}
	}

	@Override
	public int setRxFrequency(float frequency) {
		return impl.sx127x_set_frequency((long) frequency);
	}

	@Override
	public int setRxCoding(Coding encoding) {
		if (encoding.equals(Coding.UNCODED)) {
			return 0;
		}
		return 1;
	}

	@Override
	public int setRxPolarization(Polarization polarization) {
		return 0;
	}

	@Override
	public int setRxCoherency(Coherency modulation) {
		return 0;
	}

	@Override
	public int setRxCarrierModulation(CarrierModulation carrierModulation) {
		if (carrierModulation.equals(CarrierModulation.FSK)) {
			return 0;
		}
		return 1;
	}

	@Override
	public int setRxModulationIndex(ModulationIndex modulationIndex) {
		return 0;
	}

	@Override
	public int setRxScrambler(ScramblerType scrambler) {
		return 0;
	}

	@Override
	public int setRxDifferentialEncoding(boolean differentialEncoding) {
		return differentialEncoding ? 1 : 0;
	}

	@Override
	public int setTxSymbolRate(float baud) {
		return impl.sx127x_fsk_ook_set_bitrate(baud);
	}

	@Override
	public int setTxLineCoding(LineCoding modulation) {
		if (modulation.equals(LineCoding.NRZ_L)) {
			return impl.sx127x_fsk_ook_set_packet_encoding(sx127x_packet_encoding_t.SX127X_NRZ);
		} else if (modulation.equals(LineCoding.MANCHESTER)) {
			return impl.sx127x_fsk_ook_set_packet_encoding(sx127x_packet_encoding_t.SX127X_MANCHESTER);
		} else {
			return impl.sx127x_fsk_ook_set_packet_encoding(sx127x_packet_encoding_t.SX127X_SCRAMBLED);
		}
	}

	@Override
	public int setTxFrequency(float frequency) {
		return impl.sx127x_set_frequency((long) frequency);
	}

	@Override
	public int setTxCoding(Coding encoding) {
		if (encoding.equals(Coding.UNCODED)) {
			return 0;
		}
		return 1;
	}

	@Override
	public int setTxPolarization(Polarization polarization) {
		return 0;
	}

	@Override
	public int setTxCoherency(Coherency modulation) {
		return 0;
	}

	@Override
	public int setTxCarrierModulation(CarrierModulation carrierModulation) {
		if (carrierModulation.equals(CarrierModulation.FSK)) {
			return 0;
		}
		return 1;
	}

	@Override
	public int setTxModulationIndex(ModulationIndex modulationIndex) {
		return 0;
	}

	@Override
	public int setTxScrambler(ScramblerType scrambler) {
		return 0;
	}

	@Override
	public int setTxDifferentialEncoding(boolean differentialEncoding) {
		return differentialEncoding ? 1 : 0;
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
				synchronized (state) {
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
		synchronized (state) {
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
