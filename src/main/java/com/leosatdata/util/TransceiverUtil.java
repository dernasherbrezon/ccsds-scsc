package com.leosatdata.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.CarrierModulation;
import com.leosatdata.model.CarrierSuppression;
import com.leosatdata.model.Coding;
import com.leosatdata.model.Coherency;
import com.leosatdata.model.ControlParameters;
import com.leosatdata.model.DataRate;
import com.leosatdata.model.Direction;
import com.leosatdata.model.Frequency;
import com.leosatdata.model.LineCoding;
import com.leosatdata.model.Link2Establishment;
import com.leosatdata.model.LinkDirection;
import com.leosatdata.model.LinkEstablishment;
import com.leosatdata.model.Mode;
import com.leosatdata.model.ModulationIndex;
import com.leosatdata.model.NegotiationType;
import com.leosatdata.model.NegotiationType2;
import com.leosatdata.model.PlExtensions;
import com.leosatdata.model.Polarization;
import com.leosatdata.model.RsCodeType;
import com.leosatdata.model.ScramblerType;
import com.leosatdata.model.Spdu;
import com.leosatdata.model.TransceiverMode;
import com.leosatdata.model.TxRxParameters;
import com.leosatdata.model.Type1CarrierModulation;
import com.leosatdata.model.Type1Coding;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type4CarrierModulation;
import com.leosatdata.model.Type4Coding;
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

	public Spdu createHailTxRx() {
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

	private Type1Directive createHailType1() {
		Type1Directive result = new Type1Directive();
		result.setTxParams(createType1TxRx());
		result.setTxPlExtensions(createType1PlExtensions(Direction.TRANSMITTER));
		result.setRxParams(createType1TxRx());
		result.setRxPlExtensions(createType1PlExtensions(Direction.RECEIVER));
		ControlParameters controlParameters = new ControlParameters();
		controlParameters.setRnmd(false);
		controlParameters.setTimeSampleField(0);
		controlParameters.setTransmitToken(false);
		result.setControlParams(controlParameters);
		return result;
	}

	private TxRxParameters createType1TxRx() {
		// TODO not clear why hailing_data_rate exist
		TxRxParameters result = new TxRxParameters();
		result.setCoding(Type1Coding.NONE);
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
		result.setCoherency(Coherency.NON_COHERENT_PSK);
		return result;
	}

	private static PlExtensions createType1PlExtensions(Direction direction) {
		PlExtensions result = new PlExtensions();
		result.setCarrierModulation(Type1CarrierModulation.PCM_PSK_PM);
		result.setLineCoding(LineCoding.MANCHESTER);
		result.setDifferentialEncoding(false);
		result.setDirection(direction);
		result.setCarrierSuppression(CarrierSuppression.RESIDUAL_CARRIER); // default is unclear from the spec
		result.setRsCode(RsCodeType.UNKNOWN);
		result.setScrambler(ScramblerType.NONE);
		result.setUseExtendedDataRate(false);
		result.setUseExtendedFreqSet(false);
		return result;
	}

	public void applyParameters(Spdu params, boolean invert) {
		applyRxParameters(params, invert);
		applyTxParameters(params, invert);
	}

	public void applyRxParameters(Spdu params, boolean invert) {
		if (params instanceof Type1Directive) {
			applyType1RxParameters((Type1Directive) params, invert);
		} else if (params instanceof Type4Directive) {
			applyType4RxParameters((Type4Directive) params, invert);
		} else if (params instanceof Type5Directive) {
			applyType5RxParameters((Type5Directive) params, invert);
		}
	}

	public void applyTxParameters(Spdu params, boolean invert) {
		if (params instanceof Type1Directive) {
			applyType1TxParameters((Type1Directive) params, invert);
		} else if (params instanceof Type4Directive) {
			applyType4TxParameters((Type4Directive) params, invert);
		} else if (params instanceof Type5Directive) {
			applyType5TxParameters((Type5Directive) params, invert);
		}
	}

	private void applyType1RxParameters(Type1Directive params, boolean invert) {
		TxRxParameters commParams = invert ? params.getTxParams() : params.getRxParams();
		PlExtensions plParams = invert ? params.getTxPlExtensions() : params.getRxPlExtensions();
		int symbolRate = commParams.getDataRate().getRate();
		if (plParams.isUseExtendedDataRate()) {
			symbolRate = commParams.getDataRate().getExtendedRate();
		}
		transceiver.setRxSymbolRate(symbolRate);
		transceiver.setRxLineCoding(plParams.getLineCoding());
		transceiver.setRxCarrierModulation(convertType1CarrierModulation(plParams.getCarrierModulation()));
		transceiver.setRxModulationIndex(convertType1ModulationIndex(plParams));
		int channelId = commParams.getFrequency().getCode();
		if (plParams.isUseExtendedFreqSet()) {
			channelId += 8;
		}
		String direction = invert ? "f" : "r";
		transceiver.setRxFrequency(config.getLong("frequency.3.ch" + channelId + direction, 0));
		transceiver.setRxCoding(convertType1Coding(commParams.getCoding(), plParams));
		transceiver.setRxCoherency(commParams.getCoherency());
		transceiver.setRxPolarization(Polarization.RHCP); // defined in 211.1
		transceiver.setRxScrambler(plParams.getScrambler());
	}

	private void applyType1TxParameters(Type1Directive params, boolean invert) {
		TxRxParameters commParams = invert ? params.getRxParams() : params.getTxParams();
		PlExtensions plParams = invert ? params.getRxPlExtensions() : params.getTxPlExtensions();
		int symbolRate = commParams.getDataRate().getRate();
		if (plParams.isUseExtendedDataRate()) {
			symbolRate = commParams.getDataRate().getExtendedRate();
		}
		transceiver.setTxSymbolRate(symbolRate);
		transceiver.setTxLineCoding(plParams.getLineCoding());
		transceiver.setTxCarrierModulation(convertType1CarrierModulation(plParams.getCarrierModulation()));
		transceiver.setTxModulationIndex(convertType1ModulationIndex(plParams));
		int channelId = commParams.getFrequency().getCode();
		if (plParams.isUseExtendedFreqSet()) {
			channelId += 8;
		}
		String direction = invert ? "r" : "f";
		transceiver.setTxFrequency(config.getLong("frequency.3.ch" + channelId + direction, 0));
		transceiver.setTxCoding(convertType1Coding(commParams.getCoding(), plParams));
		transceiver.setTxCoherency(commParams.getCoherency());
		transceiver.setTxPolarization(Polarization.RHCP); // defined in 211.1
		transceiver.setTxScrambler(plParams.getScrambler());
	}

	private Type5Directive createHailType5() {
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
		result.setCoherency(Coherency.NON_COHERENT_PSK);

		int hailingChannel = channel;
		if (channel == -1) {
			hailingChannel = 0;
		}

		// use the same channel for type 4 and type 5 directives
		result.setFrequency(config.getLong("frequency.4.ch" + hailingChannel + direction.getShortCode(), 0));
		result.setCarrierModulation(Type4CarrierModulation.PCM_PM_BI_PHASE);
		result.setCoding(Type4Coding.LDPC2048_1024);
		result.setModulationIndex(ModulationIndex.M0); // not specified in the spec
		return result;
	}

	private Type4Directive createHailType4() {
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
		result.setCarrierModulation(Type4CarrierModulation.PCM_PM_BI_PHASE);
		result.setCoding(Type4Coding.LDPC2048_1024);
		result.setModulationIndex(ModulationIndex.M0); // not specified in the spec
		return result;
	}

	private void applyType4RxParameters(Type4Directive directive, boolean invert) {
		LinkEstablishment params = invert ? directive.getTxLink() : directive.getRxLink();
		transceiver.setRxSymbolRate(params.getSymbolRate());
		if (params.getCarrierModulation().equals(Type4CarrierModulation.PCM_PM_BI_PHASE)) {
			transceiver.setRxLineCoding(LineCoding.MANCHESTER);
		} else if (params.getCarrierModulation().equals(Type4CarrierModulation.PCM_PM_NRZL)) {
			transceiver.setRxLineCoding(LineCoding.NRZ_L);
		} else {
			transceiver.setRxLineCoding(LineCoding.NONE);
		}
		transceiver.setRxCarrierModulation(convertType4CarrierModulation(params.getCarrierModulation()));
		transceiver.setRxModulationIndex(params.getModulationIndex());
		transceiver.setRxFrequency(config.getLong("frequency.4.ch" + params.getFreqChannel() + params.getDirection().getShortCode(), 0));
		transceiver.setRxCoding(convertType4Coding(params.getCoding()));
		transceiver.setRxCoherency(Coherency.UNKNOWN); // not clear from the spec
		transceiver.setRxPolarization(params.getPolarization());
		transceiver.setRxScrambler(ScramblerType.NONE); // not defined in the spec. assume none
	}

	private void applyType4TxParameters(Type4Directive directive, boolean invert) {
		LinkEstablishment params = invert ? directive.getRxLink() : directive.getTxLink();
		transceiver.setTxSymbolRate(params.getSymbolRate());
		if (params.getCarrierModulation().equals(Type4CarrierModulation.PCM_PM_BI_PHASE)) {
			transceiver.setTxLineCoding(LineCoding.MANCHESTER);
		} else if (params.getCarrierModulation().equals(Type4CarrierModulation.PCM_PM_NRZL)) {
			transceiver.setTxLineCoding(LineCoding.NRZ_L);
		} else {
			transceiver.setTxLineCoding(LineCoding.NONE);
		}
		transceiver.setTxCarrierModulation(convertType4CarrierModulation(params.getCarrierModulation()));
		transceiver.setTxModulationIndex(params.getModulationIndex());
		transceiver.setTxFrequency(config.getLong("frequency.4.ch" + params.getFreqChannel() + params.getDirection().getShortCode(), 0));
		transceiver.setTxCoding(convertType4Coding(params.getCoding()));
		transceiver.setTxCoherency(Coherency.UNKNOWN); // not clear from the spec
		transceiver.setTxPolarization(params.getPolarization());
		transceiver.setTxScrambler(ScramblerType.NONE); // not defined in the spec. assume none
	}

	private void applyType5RxParameters(Type5Directive directive, boolean invert) {
		Link2Establishment params = invert ? directive.getTxLink() : directive.getRxLink();
		transceiver.setRxSymbolRate(params.getSymbolRate());
		if (params.getCarrierModulation().equals(Type4CarrierModulation.PCM_PM_BI_PHASE)) {
			transceiver.setRxLineCoding(LineCoding.MANCHESTER);
		} else if (params.getCarrierModulation().equals(Type4CarrierModulation.PCM_PM_NRZL)) {
			transceiver.setRxLineCoding(LineCoding.NRZ_L);
		} else {
			transceiver.setRxLineCoding(LineCoding.NONE);
		}
		transceiver.setRxCarrierModulation(convertType4CarrierModulation(params.getCarrierModulation()));
		transceiver.setRxModulationIndex(params.getModulationIndex());
		transceiver.setRxFrequency(params.getFrequency());
		transceiver.setRxCoding(convertType4Coding(params.getCoding()));
		transceiver.setRxCoherency(params.getCoherency());
		transceiver.setRxPolarization(params.getPolarization());
		transceiver.setRxScrambler(ScramblerType.NONE); // not defined in the spec. assume none
	}

	private void applyType5TxParameters(Type5Directive directive, boolean invert) {
		Link2Establishment params = invert ? directive.getRxLink() : directive.getTxLink();
		transceiver.setTxSymbolRate(params.getSymbolRate());
		if (params.getCarrierModulation().equals(Type4CarrierModulation.PCM_PM_BI_PHASE)) {
			transceiver.setTxLineCoding(LineCoding.MANCHESTER);
		} else if (params.getCarrierModulation().equals(Type4CarrierModulation.PCM_PM_NRZL)) {
			transceiver.setTxLineCoding(LineCoding.NRZ_L);
		} else {
			transceiver.setTxLineCoding(LineCoding.NONE);
		}
		transceiver.setTxCarrierModulation(convertType4CarrierModulation(params.getCarrierModulation()));
		transceiver.setTxModulationIndex(params.getModulationIndex());
		transceiver.setTxFrequency(params.getFrequency());
		transceiver.setTxCoding(convertType4Coding(params.getCoding()));
		transceiver.setTxCoherency(params.getCoherency());
		transceiver.setTxPolarization(params.getPolarization());
		transceiver.setTxScrambler(ScramblerType.NONE); // not defined in the spec. assume none
	}

	private static ModulationIndex convertType1ModulationIndex(PlExtensions params) {
		if (params != null && params.getCarrierSuppression().equals(CarrierSuppression.SUPPRESSED_CARRIER)) {
			return ModulationIndex.MPI_2; // Suppressed Carrier (Requires transmit side utilize Modulation Index of 90°)
		}
		return ModulationIndex.MPI_3; // 60deg according to CCSDS 211.1
	}

	private static Coding convertType1Coding(Type1Coding encoding, PlExtensions params) {
		if (encoding.equals(Type1Coding.CONVOLUTIONAL)) {
			return Coding.CONVOLUTIONAL;
		} else if (encoding.equals(Type1Coding.LDPC)) {
			return Coding.LDPC2048_1024;
		} else if (encoding.equals(Type1Coding.CONCANTENATED)) {
			if (params != null && params.getRsCode().equals(RsCodeType.RS255_239)) {
				return Coding.CONCANTENATED_RS255_239;
			}
			// default if PlExtensions missing
			return Coding.CONCANTENATED_RS204_188;
		} else {
			return Coding.UNCODED;
		}
	}

	private static Coding convertType4Coding(Type4Coding coding) {
		switch (coding) {
		case CONVOLUTIONAL:
			return Coding.CONVOLUTIONAL;
		case LDPC20480_16384:
			return Coding.LDPC20480_16384;
		case LDPC2048_1024:
			return Coding.LDPC2048_1024;
		case LDPC24576_16384:
			return Coding.LDPC24576_16384;
		case LDPC32768_16384:
			return Coding.LDPC32768_16384;
		case LDPC6144_4096:
			return Coding.LDPC6144_4096;
		case LDPC8160_7136:
			return Coding.LDPC8160_7136;
		case LDPC8192_4096:
			return Coding.LDPC8192_4096;
		case UNCODED:
			return Coding.UNCODED;
		default:
			return Coding.UNKNOWN;
		}
	}

	private static CarrierModulation convertType1CarrierModulation(Type1CarrierModulation mod) {
		switch (mod) {
		case FSK:
			return CarrierModulation.FSK;
		case NO_MODULATION:
			return CarrierModulation.NO_MODULATION;
		case PCM_PSK_PM:
			return CarrierModulation.PCM_PSK_PM;
		case QPSK:
			return CarrierModulation.QPSK;
		default:
			return CarrierModulation.UNKNOWN;
		}
	}

	private static CarrierModulation convertType4CarrierModulation(Type4CarrierModulation mod) {
		switch (mod) {
		case BPSK:
			return CarrierModulation.BPSK;
		case GMSK:
			return CarrierModulation.GMSK;
		case OQPSK:
			return CarrierModulation.OQPSK;
		case PCM_PM_BI_PHASE:
		case PCM_PM_NRZL:
			return CarrierModulation.PCM_PM;
		case PCM_PSK_PM:
			return CarrierModulation.PCM_PSK_PM;
		default:
			return CarrierModulation.UNKNOWN;
		}
	}

}
