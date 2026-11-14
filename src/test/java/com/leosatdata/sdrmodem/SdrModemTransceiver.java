package com.leosatdata.sdrmodem;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.CarrierModulation;
import com.leosatdata.model.CommParameters;
import com.leosatdata.model.ErrorCode;
import com.leosatdata.sdrmodem.model.CommSettings;
import com.leosatdata.sdrmodem.model.GfskSettings;
import com.leosatdata.sdrmodem.model.ModemSettings;
import com.leosatdata.sdrmodem.model.ModemType;
import com.leosatdata.sdrmodem.model.Response;
import com.leosatdata.sdrmodem.model.SdrChannelConfig;
import com.leosatdata.sdrmodem.model.SoftBits;
import com.leosatdata.service.Transceiver;
import com.leosatdata.service.TransceiverCallback;
import com.leosatdata.service.TransceiverRxState;
import com.leosatdata.service.TransceiverTxState;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.NamingThreadFactory;
import com.leosatdata.util.Util;

import ru.r2cloud.jradio.Context;
import ru.r2cloud.jradio.RawBeacon;
import ru.r2cloud.jradio.ccsds.CcsdsBeaconSource;
import ru.r2cloud.jradio.ccsds.CcsdsFraming;
import ru.r2cloud.jradio.ccsds.Coding;
import ru.r2cloud.jradio.ccsds.ScramblerType;
import ru.r2cloud.jradio.source.InputStreamSource;

public class SdrModemTransceiver implements Transceiver {

	private final static Logger LOG = LoggerFactory.getLogger(SdrModemTransceiver.class);

	private TransceiverTxState tx = TransceiverTxState.OFF;
	private TransceiverRxState rx = TransceiverRxState.OFF;
	private TransceiverCallback callback;
	private SdrModemClient client;
	private ScheduledExecutorService executor;
	private CcsdsBeaconSource<RawBeacon> deframing;

	private CommParameters rxParams;
	private CommParameters txParams;

	private final Configuration config;
	private final byte[] preamble;

	public SdrModemTransceiver(Configuration config) {
		this.config = config;
		this.preamble = new byte[config.getInteger("sdrmodem_preamble_length", 0)];
		byte preambleType = convertPreambleType(config.getProperty("sdrmodem_preamble_type"));
		for (int i = 0; i < preamble.length; i++) {
			preamble[i] = preambleType;
		}
	}

	@SuppressWarnings("resource")
	public void start() throws IOException {
		String asm = config.getProperty("cs.sublayer.asm");
		byte[] asmBytes = Util.hexToBytes(asm);

		CcsdsFraming framing = new CcsdsFraming();
		framing.setCoding(Coding.UNCODED);
		framing.setFrameLength(config.getInteger("maximum_frame_length", 0));
		framing.setScrambler(ScramblerType.NONE);
		framing.setSyncwordThreshold(1);
		framing.setSyncword(asm);

		FeedableInputStream is = new FeedableInputStream();
		Context context = new Context();
		context.setSoftBits(true);
		deframing = new CcsdsBeaconSource<>(new InputStreamSource(new BufferedInputStream(is), context), framing, ru.r2cloud.jradio.RawBeacon.class);

		client = new SdrModemClient(config.getProperty("sdrmodem_host"), config.getInteger("sdrmodem_port", 0), config.getLong("sdrmodem_timeoutMillis", 0), new SdrModemCallback() {

			@Override
			public void onTx() {
				callback.onTx();
			}

			@Override
			public void onRx(SoftBits data) {
				is.feed(data.getData());
			}

			// sdr-modem is packet modem (currently)
			// ignore back and forth carrier acquired/lost
			@Override
			public void onCarrierAcquired() {
			}

			@Override
			public void onCarrierLost() {
			}
		});
		client.start();

		executor = Executors.newScheduledThreadPool(1, new NamingThreadFactory("transceiver-" + config.getInteger("local_spacecraft_id", 0)));
		executor.execute(new Runnable() {

			private boolean carrierAcquired = false;

			@Override
			public void run() {
				while (deframing.hasNext() && !Thread.currentThread().isInterrupted()) {
					RawBeacon frame = (RawBeacon) deframing.next();
					if (callback != null) {
						if (!carrierAcquired) {
							callback.onCarrierAcquired();
							carrierAcquired = true;
						}
						byte[] payload = new byte[frame.getRawData().length + asmBytes.length];
						System.arraycopy(asmBytes, 0, payload, 0, asmBytes.length);
						System.arraycopy(frame.getRawData(), 0, payload, asmBytes.length, frame.getRawData().length);
						callback.onRx(payload);
					}
				}
			}
		});
	}

	public void stop() throws IOException {
		if (client != null) {
			client.close();
		}
		if (deframing != null) {
			deframing.close();
		}
		if (executor != null) {
			executor.shutdown();
		}
	}

	@Override
	public int send(byte[] data) {
		try {
			if (preamble.length > 0) {
				byte[] padded = new byte[preamble.length + data.length];
				System.arraycopy(preamble, 0, padded, 0, preamble.length);
				System.arraycopy(data, 0, padded, preamble.length, data.length);
				client.sendFrame(padded);
			} else {
				client.sendFrame(data);
			}
			return 0;
		} catch (IOException e) {
			LOG.error("unable to send", e);
			return 1;
		}
	}

	private static boolean acceptCommParameters(CommParameters params) {
		if (!params.getCarrierModulation().equals(CarrierModulation.FSK)) {
			LOG.info("{} modulation is not supported. Expected: FSK", params.getCarrierModulation());
			return false;
		}
		return false;
	}

	@Override
	public boolean acceptTxCommParameters(CommParameters params) {
		return acceptCommParameters(params);
	}

	@Override
	public boolean acceptRxCommParameters(CommParameters params) {
		return acceptCommParameters(params);
	}

	private CommSettings convert(CommParameters params, float gain) {
		long sampleRate = config.getLong("sdrmodem_sampleRate", 0);
		long deviation = config.getLong("sdrmodem_deviation", 0);
		int bandwidth = config.getInteger("sdrmodem_bandwidth", 0);
		String syncword = config.getProperty("cs.sublayer.asm");
		SdrChannelConfig channel = new SdrChannelConfig();
		channel.setFrequency((long) params.getFrequency() + config.getLong("sdrmodem_frequency_correction", 0));
		channel.setSampleRate(sampleRate);
		channel.setGain(gain);

		GfskSettings settings = new GfskSettings();
		settings.setSampleRate(sampleRate);
		settings.setBaudRate((int) params.getSymbolRate());
		settings.setDeviation(deviation);
		settings.setBandwidth(bandwidth);
		settings.setBt(0.5f);
		settings.setUseDcBlock(true);
		settings.setSyncword(Long.parseLong(syncword, 16));
		settings.setSyncwordBits(syncword.length() * 4);
		ModemSettings modemSettings = new ModemSettings();
		modemSettings.setGfsk(settings);
		modemSettings.setType(ModemType.GFSK);

		CommSettings result = new CommSettings();
		result.setSdrSettings(channel);
		result.setModemSettings(modemSettings);
		return result;
	}

	@Override
	public int setRxCommParameters(CommParameters params) {
		if (this.rxParams != null && this.rxParams.equals(params)) {
			return 0;
		}
		if (!acceptCommParameters(params)) {
			return ErrorCode.UNSUPPORTED.ordinal();
		}
		try {
			int result = convertCode(client.setRxParameters(convert(params, config.getFloat("sdrmodem_gain", 0))));
			if (result == 0) {
				this.rxParams = params;
			}
			return result;
		} catch (IOException e) {
			LOG.error("unable to set rx comm", e);
			return 1;
		}
	}

	@Override
	public int setTxCommParameters(CommParameters params) {
		if (this.txParams != null && this.txParams.equals(params)) {
			return 0;
		}
		if (!acceptCommParameters(params)) {
			return ErrorCode.UNSUPPORTED.ordinal();
		}
		try {
			int result = convertCode(client.setTxParameters(convert(params, config.getFloat("sdrmodem_power", 0))));
			if (result == 0) {
				this.txParams = params;
			}
			return result;
		} catch (IOException e) {
			LOG.error("unable to set tx comm", e);
			return 1;
		}
	}

	@Override
	public void setCallback(TransceiverCallback callback) {
		this.callback = callback;
	}

	@Override
	public TransceiverCallback getCallback() {
		return callback;
	}

	@Override
	public int setTxState(TransceiverTxState state) {
		// FIXME doesn't support carrier only
		this.tx = state;
		return 0;
	}

	@Override
	public TransceiverTxState getTxState() {
		return tx;
	}

	@Override
	public int setRxState(TransceiverRxState state) {
		int result = 0;
		try {
			if (state.equals(TransceiverRxState.OFF)) {
				result = convertCode(client.rxStop());
			} else if (state.equals(TransceiverRxState.RECEIVING)) {
				result = convertCode(client.rxStart());
			}
		} catch (IOException e) {
			LOG.error("unable to change state", e);
			return 1;
		}
		if (result == 0) {
			this.rx = state;
		}
		return result;
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

	private static int convertCode(Response response) {
		if (!response.isSuccess()) {
			return response.getDetails();
		}
		return 0;
	}

	private static byte convertPreambleType(String str) {
		if (str == null) {
			return 0;
		}
		if (str.equalsIgnoreCase("aa")) {
			return (byte) 0xaa;
		} else if (str.equalsIgnoreCase("55")) {
			return (byte) 0x55;
		} else {
			throw new IllegalArgumentException("unsupported preamble type: " + str);
		}
	}
}
