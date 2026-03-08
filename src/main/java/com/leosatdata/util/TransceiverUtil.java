package com.leosatdata.util;

import com.leosatdata.model.CarrierModulation;
import com.leosatdata.model.Coding;
import com.leosatdata.model.DataEncoding;
import com.leosatdata.model.DataModulation;
import com.leosatdata.model.DataRate;
import com.leosatdata.model.Direction;
import com.leosatdata.model.Frequency;
import com.leosatdata.model.Link2Establishment;
import com.leosatdata.model.LinkDirection;
import com.leosatdata.model.LinkModulation;
import com.leosatdata.model.Mode;
import com.leosatdata.model.ModeSelect;
import com.leosatdata.model.Modulation;
import com.leosatdata.model.ModulationIndex;
import com.leosatdata.model.NegotiationType2;
import com.leosatdata.model.PlExtensions;
import com.leosatdata.model.Polarization;
import com.leosatdata.model.RsCodeType;
import com.leosatdata.model.ScramblerType;
import com.leosatdata.model.TransceiverMode;
import com.leosatdata.model.TxRxParameters;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type5Directive;
import com.leosatdata.service.Transceiver;

public class TransceiverUtil {

	public static Object createHailTxRx(Configuration config) {
		// FIXME check different parameter
		String frameVersion = config.getProperty("frame_version");
		if (frameVersion.equals("3")) {
			return TransceiverUtil.createHailProximityTxRx(config);
		} else if (frameVersion.equals("4")) {
			return TransceiverUtil.createHailUslpTxRx(config);
		} else {
			throw new IllegalArgumentException("unsupported frame_version: " + frameVersion);
		}
	}

	public static Type1Directive createHailProximityTxRx(Configuration config) {
		Type1Directive result = new Type1Directive();
		result.setTxParams(createProximityTxRx(config));
		result.setTxPlExtensions(createProximityPlExtensions(Direction.TRANSMITTER));
		result.setRxParams(createProximityTxRx(config));
		result.setRxPlExtensions(createProximityPlExtensions(Direction.RECEIVER));
		return result;
	}

	private static TxRxParameters createProximityTxRx(Configuration config) {
		// TODO not clear why hailing_data_rate exist
		int symbolRate = config.getInteger("hailing_symbol_rate", -1);
		int channel = config.getInteger("hailing_channel", -1);
		TxRxParameters result = new TxRxParameters();
		result.setDataEncoding(DataEncoding.NONE);
		if (symbolRate != -1) {
			result.setDataRate(DataRate.valueOfCode(symbolRate));
		} else {
			result.setDataRate(DataRate.R8NC); // 8000 non-coherent-psk
		}
		if (channel != -1) {
			result.setFrequency(Frequency.valueOfCode(channel));
		} else {
			result.setFrequency(Frequency.CH1);
		}
		result.setMode(Mode.PROXIMITY1);
		result.setModulation(Modulation.NON_COHERENT_PSK);
		return result;
	}

	private static PlExtensions createProximityPlExtensions(Direction direction) {
		PlExtensions result = new PlExtensions();
		result.setCarrierModulation(CarrierModulation.PSK);
		result.setDataModulation(DataModulation.MANCHESTER);
		result.setDifferentialEncoding(false);
		result.setDirection(direction);
		result.setModeSelect(ModeSelect.RESIDUAL_CARRIER); // default is unclear from the spec
		result.setRsCode(RsCodeType.UNKNOWN);
		result.setScrambler(ScramblerType.BYPASS);
		result.setUseExtendedDataRate(false);
		result.setUseExtendedFreqSet(false);
		return result;
	}

	public static void applyParameters(Object params, Configuration config, Transceiver transceiver) {
		if (params instanceof Type1Directive) {
			applyProximityParameters((Type1Directive) params, config, transceiver);
		} else if (params instanceof Type5Directive) {
			applyUslpParameters((Type5Directive) params, transceiver);
		}
	}

	public static void applyProximityParameters(Type1Directive params, Configuration config, Transceiver transceiver) {
		int symbolRate = params.getRxParams().getDataRate().getRate();
		if (params.getRxPlExtensions().isUseExtendedDataRate()) {
			symbolRate = params.getRxParams().getDataRate().getExtendedRate();
		}
		transceiver.setRxSymbolRate(symbolRate);
		transceiver.setRxDataModulation(params.getRxPlExtensions().getDataModulation());
		int channelId = params.getRxParams().getFrequency().getCode();
		if (params.getRxPlExtensions().isUseExtendedFreqSet()) {
			channelId += 8;
		}
		transceiver.setRxFrequency(config.getLong("frequency.3.ch" + channelId + "r", 0));
		transceiver.setRxDataEncoding(convertDataEncoding(params.getRxParams().getDataEncoding()));
		transceiver.setRxModulation(params.getRxParams().getModulation());

		symbolRate = params.getTxParams().getDataRate().getRate();
		if (params.getTxPlExtensions().isUseExtendedDataRate()) {
			symbolRate = params.getTxParams().getDataRate().getExtendedRate();
		}
		transceiver.setTxSymbolRate(symbolRate);
		transceiver.setTxDataModulation(params.getTxPlExtensions().getDataModulation());
		channelId = params.getTxParams().getFrequency().getCode();
		if (params.getTxPlExtensions().isUseExtendedFreqSet()) {
			channelId += 8;
		}
		transceiver.setTxFrequency(config.getLong("frequency.3.ch" + channelId + "f", 0));
		transceiver.setTxDataEncoding(convertDataEncoding(params.getTxParams().getDataEncoding()));
		transceiver.setTxModulation(params.getTxParams().getModulation());
	}

	// from the initial hail channel type4 or type5 doesn't matter much
	public static Type5Directive createHailUslpTxRx(Configuration config) {
		Type5Directive result = new Type5Directive();
		result.setRxLink(createUslpTxRx(LinkDirection.RETURN, config));
		result.setTxLink(createUslpTxRx(LinkDirection.FORWARD, config));
		return result;
	}

	private static Link2Establishment createUslpTxRx(LinkDirection direction, Configuration config) {
		Link2Establishment result = new Link2Establishment();
		float symbolRate = config.getFloat("hailing_symbol_rate", -1);
		if (symbolRate != -1) {
			result.setSymbolRate(symbolRate);
		} else {
			result.setSymbolRate(2000);
		}
		result.setDirection(direction);
		result.setTransceiverMode(TransceiverMode.USLP);
		result.setDemandQuery(NegotiationType2.DEMAND); // not specified in the spec
		result.setPolarization(Polarization.LHCP);
		result.setRnmd(false);
		result.setTransmitToken(false);
		result.setCoherency(Modulation.NON_COHERENT_PSK);

		int channel = config.getInteger("hailing_channel", -1);
		if (channel == -1) {
			channel = 0;
		}

		result.setFrequency(config.getLong("frequency.4.ch" + channel + direction.getShortCode(), channel));
		result.setModulation(LinkModulation.PCM_PM_BI_PHASE);
		result.setCoding(Coding.LDPC2048_1024);
		result.setModulationIndex(ModulationIndex.M0); // not specified in the spec
		return result;
	}

	public static void applyUslpParameters(Type5Directive directive, Transceiver transceiver) {
		Link2Establishment params = directive.getRxLink();
		transceiver.setRxSymbolRate(params.getSymbolRate());
		if (params.getModulation().equals(LinkModulation.PCM_PM_BI_PHASE)) {
			transceiver.setRxDataModulation(DataModulation.MANCHESTER);
		} // FIXME other options
		transceiver.setRxFrequency(params.getFrequency());
		transceiver.setRxDataEncoding(params.getCoding());
		transceiver.setRxPolarization(params.getPolarization());
		transceiver.setRxModulation(params.getCoherency());

		params = directive.getTxLink();
		transceiver.setTxSymbolRate(params.getSymbolRate());
		if (params.getModulation().equals(LinkModulation.PCM_PM_BI_PHASE)) {
			transceiver.setTxDataModulation(DataModulation.MANCHESTER);
		} // FIXME other options
		transceiver.setTxFrequency(params.getFrequency());
		transceiver.setTxDataEncoding(params.getCoding());
		transceiver.setTxPolarization(params.getPolarization());
		transceiver.setTxModulation(params.getCoherency());
	}

	private static Coding convertDataEncoding(DataEncoding encoding) {
		switch (encoding) {
		case CONVOLUTIONAL:
			return Coding.CONVOLUTIONAL;
		case LDPC:
			return Coding.LDPC2048_1024;
		case NONE:
			return Coding.UNCODED;
		default:
			return Coding.UNKNOWN;
		}
	}

}
