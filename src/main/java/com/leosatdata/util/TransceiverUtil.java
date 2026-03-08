package com.leosatdata.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.CarrierModulation;
import com.leosatdata.model.Coding;
import com.leosatdata.model.DataEncoding;
import com.leosatdata.model.DataModulation;
import com.leosatdata.model.DataRate;
import com.leosatdata.model.Direction;
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
import com.leosatdata.model.PlExtensions;
import com.leosatdata.model.Polarization;
import com.leosatdata.model.RsCodeType;
import com.leosatdata.model.ScramblerType;
import com.leosatdata.model.TransceiverMode;
import com.leosatdata.model.TxRxParameters;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type4Directive;
import com.leosatdata.model.Type5Directive;
import com.leosatdata.service.Transceiver;

public class TransceiverUtil {

	private static final Logger LOG = LoggerFactory.getLogger(TransceiverUtil.class);
	private final Transceiver transceiver;
	private final Configuration config;
	private final int spduVersion;
	private final int symbolRate;
	private final int channel;

	public TransceiverUtil(Configuration config, Transceiver transceiver) {
		this.config = config;
		this.transceiver = transceiver;
		this.spduVersion = config.getInteger("spdu_version", 1);
		this.symbolRate = config.getInteger("hailing_symbol_rate", -1);
		this.channel = config.getInteger("hailing_channel", -1);
	}

	public Object createHailTxRx() {
		if (spduVersion == Type1Directive.SPDU_VERSION) {
			return createHailType1();
		} else if (spduVersion == Type4Directive.SPDU_VERSION) {
			return createHailType4();
		} else if (spduVersion == Type5Directive.SPDU_VERSION) {
			return createHailType5();
		} else {
			LOG.error("unsupported spdu version: {}", spduVersion);
			return null;
		}
	}

	public Type1Directive createHailType1() {
		Type1Directive result = new Type1Directive();
		result.setTxParams(createType1TxRx());
		result.setTxPlExtensions(createType1PlExtensions(Direction.TRANSMITTER));
		result.setRxParams(createType1TxRx());
		result.setRxPlExtensions(createType1PlExtensions(Direction.RECEIVER));
		return result;
	}

	private TxRxParameters createType1TxRx() {
		// TODO not clear why hailing_data_rate exist
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

	private static PlExtensions createType1PlExtensions(Direction direction) {
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

	public void applyParameters(Object params) {
		if (params instanceof Type1Directive) {
			applyType1Parameters((Type1Directive) params);
		} else if (params instanceof Type4Directive) {
			applyType4Parameters((Type4Directive) params);
		} else if (params instanceof Type5Directive) {
			applyType5Parameters((Type5Directive) params);
		}
	}

	public void applyType1Parameters(Type1Directive params) {
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

	public Type5Directive createHailType5() {
		Type5Directive result = new Type5Directive();
		result.setRxLink(createType5TxRx(LinkDirection.RETURN));
		result.setTxLink(createType5TxRx(LinkDirection.FORWARD));
		return result;
	}

	private Link2Establishment createType5TxRx(LinkDirection direction) {
		Link2Establishment result = new Link2Establishment();
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

		int hailingChannel = channel;
		if (channel == -1) {
			hailingChannel = 0;
		}

		// use the same channel for type 4 and type 5 directives
		result.setFrequency(config.getLong("frequency.4.ch" + hailingChannel + direction.getShortCode(), 0));
		result.setModulation(LinkModulation.PCM_PM_BI_PHASE);
		result.setCoding(Coding.LDPC2048_1024);
		result.setModulationIndex(ModulationIndex.M0); // not specified in the spec
		return result;
	}

	public Type4Directive createHailType4() {
		Type4Directive result = new Type4Directive();
		result.setRxLink(createType4TxRx(LinkDirection.RETURN));
		result.setTxLink(createType4TxRx(LinkDirection.FORWARD));
		return result;
	}

	private LinkEstablishment createType4TxRx(LinkDirection direction) {
		LinkEstablishment result = new LinkEstablishment();
		if (symbolRate != -1) {
			result.setSymbolRate(symbolRate);
		} else {
			result.setSymbolRate(2000);
		}
		result.setDirection(direction);
		result.setDemandQuery(NegotiationType.DEMAND); // not specified in the spec
		result.setPolarization(Polarization.LHCP);
		result.setRnmd(false);
		result.setTransmitToken(false);

		int hailingChannel = channel;
		if (channel == -1) {
			hailingChannel = 0;
		}

		result.setFreqChannel(hailingChannel);
		result.setModulation(LinkModulation.PCM_PM_BI_PHASE);
		result.setCoding(Coding.LDPC2048_1024);
		result.setModulationIndex(ModulationIndex.M0); // not specified in the spec
		return result;
	}

	private void applyType4Parameters(Type4Directive directive) {
		LinkEstablishment params = directive.getRxLink();
		transceiver.setRxSymbolRate(params.getSymbolRate());
		if (params.getModulation().equals(LinkModulation.PCM_PM_BI_PHASE)) {
			transceiver.setRxDataModulation(DataModulation.MANCHESTER);
		} // FIXME other options
		transceiver.setRxFrequency(config.getLong("frequency.4.ch" + params.getFreqChannel() + params.getDirection().getShortCode(), 0));
		transceiver.setRxDataEncoding(params.getCoding());
		transceiver.setRxPolarization(params.getPolarization());

		params = directive.getTxLink();
		transceiver.setTxSymbolRate(params.getSymbolRate());
		if (params.getModulation().equals(LinkModulation.PCM_PM_BI_PHASE)) {
			transceiver.setTxDataModulation(DataModulation.MANCHESTER);
		} // FIXME other options
		transceiver.setTxFrequency(config.getLong("frequency.4.ch" + params.getFreqChannel() + params.getDirection().getShortCode(), 0));
		transceiver.setTxDataEncoding(params.getCoding());
		transceiver.setTxPolarization(params.getPolarization());
	}

	public void applyType5Parameters(Type5Directive directive) {
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
