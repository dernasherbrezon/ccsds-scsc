package com.leosatdata.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.CarrierModulation;
import com.leosatdata.model.CarrierSuppression;
import com.leosatdata.model.Coding;
import com.leosatdata.model.Coherency;
import com.leosatdata.model.ControlParameters;
import com.leosatdata.model.DataRate;
import com.leosatdata.model.ErrorCode;
import com.leosatdata.model.Frequency;
import com.leosatdata.model.LineCoding;
import com.leosatdata.model.LinkDirection;
import com.leosatdata.model.Mode;
import com.leosatdata.model.ModulationIndex;
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
import com.leosatdata.model.Type4LinkEstablishment;
import com.leosatdata.model.Type4NegotiationType;
import com.leosatdata.model.Type5Directive;
import com.leosatdata.model.Type5LinkEstablishment;
import com.leosatdata.model.Type5NegotiationType;
import com.leosatdata.service.Transceiver;

public class TransceiverUtil {

	private static final Logger LOG = LoggerFactory.getLogger(TransceiverUtil.class);
	private final Transceiver transceiver;
	private final Configuration config;
	private final int spduVersion;
	private final int symbolRate;
	private final int channel;
	private final Type1CarrierModulation type1HailCarrierModulation;
	private final Type4CarrierModulation type4HailCarrierModulation;
	private final Type4CarrierModulation type5HailCarrierModulation;

	public TransceiverUtil(Configuration config, Transceiver transceiver) {
		this.config = config;
		this.transceiver = transceiver;
		this.spduVersion = config.getInteger("spdu_version", 1);
		this.symbolRate = config.getInteger("hailing_symbol_rate", -1);
		this.channel = config.getInteger("hailing_channel", -1);
		String type1HailCarrierModulationStr = config.getProperty("hailing_carrier_modulation_type1");
		if (type1HailCarrierModulationStr == null) {
			type1HailCarrierModulation = Type1CarrierModulation.PCM_PSK_PM;
		} else {
			type1HailCarrierModulation = Type1CarrierModulation.valueOf(type1HailCarrierModulationStr);
		}
		String type4HailCarrierModulationStr = config.getProperty("hailing_carrier_modulation_type4");
		if (type4HailCarrierModulationStr == null) {
			type4HailCarrierModulation = Type4CarrierModulation.PCM_PM_BI_PHASE;
		} else {
			type4HailCarrierModulation = Type4CarrierModulation.valueOf(type4HailCarrierModulationStr);
		}
		String type5HailCarrierModulationStr = config.getProperty("hailing_carrier_modulation_type5");
		if (type5HailCarrierModulationStr == null) {
			type5HailCarrierModulation = Type4CarrierModulation.PCM_PM_BI_PHASE;
		} else {
			type5HailCarrierModulation = Type4CarrierModulation.valueOf(type5HailCarrierModulationStr);
		}
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
		result.setTxPlExtensions(createType1PlExtensions());
		result.setRxParams(createType1TxRx());
		result.setRxPlExtensions(createType1PlExtensions());
		ControlParameters controlParameters = new ControlParameters();
		controlParameters.setRnmd(false);
		controlParameters.setTimeSampleField(0);
		controlParameters.setTransmitToken(false);
		result.setControlParams(controlParameters);
		return result;
	}

	private TxRxParameters createType1TxRx() {
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

	private PlExtensions createType1PlExtensions() {
		PlExtensions result = new PlExtensions();
		result.setCarrierModulation(type1HailCarrierModulation);
		result.setLineCoding(LineCoding.MANCHESTER);
		result.setDifferentialEncoding(false);
		result.setCarrierSuppression(CarrierSuppression.RESIDUAL_CARRIER); // default is unclear from the spec
		result.setRsCode(RsCodeType.UNKNOWN);
		result.setScrambler(ScramblerType.NONE);
		result.setUseExtendedDataRate(false);
		result.setUseExtendedFreqSet(false);
		return result;
	}

	public int applyParameters(Spdu params, boolean invert) {
		int result = applyRxParameters(params, invert);
		if (result != 0) {
			return result;
		}
		result = applyTxParameters(params, invert);
		if (result != 0) {
			return result;
		}
		return ErrorCode.SUCCESS.ordinal();
	}

	public int applyRxParameters(Spdu params, boolean invert) {
		int result = ErrorCode.SUCCESS.ordinal();
		if (params instanceof Type1Directive) {
			result = applyType1RxParameters((Type1Directive) params, invert);
		} else if (params instanceof Type4Directive) {
			result = applyType4RxParameters((Type4Directive) params, invert);
		} else if (params instanceof Type5Directive) {
			result = applyType5RxParameters((Type5Directive) params, invert);
		} else {
			result = ErrorCode.INVALID_ARGUMENT.ordinal();
		}
		if (result == ErrorCode.SUCCESS.ordinal()) {
			return result;
		}
		return ErrorCode.TRANSCEIVER_ERROR.ordinal() + result;
	}

	public int applyTxParameters(Spdu params, boolean invert) {
		int result = ErrorCode.SUCCESS.ordinal();
		if (params instanceof Type1Directive) {
			result = applyType1TxParameters((Type1Directive) params, invert);
		} else if (params instanceof Type4Directive) {
			result = applyType4TxParameters((Type4Directive) params, invert);
		} else if (params instanceof Type5Directive) {
			result = applyType5TxParameters((Type5Directive) params, invert);
		} else {
			result = ErrorCode.INVALID_ARGUMENT.ordinal();
		}
		if (result == ErrorCode.SUCCESS.ordinal()) {
			return result;
		}
		return ErrorCode.TRANSCEIVER_ERROR.ordinal() + result;
	}

	private int applyType1RxParameters(Type1Directive params, boolean invert) {
		TxRxParameters commParams = invert ? params.getTxParams() : params.getRxParams();
		PlExtensions plParams = invert ? params.getTxPlExtensions() : params.getRxPlExtensions();
		int symbolRate = commParams.getDataRate().getRate();
		if (plParams != null && plParams.isUseExtendedDataRate()) {
			symbolRate = commParams.getDataRate().getExtendedRate();
		}
		int code = transceiver.setRxSymbolRate(symbolRate);
		if (code != 0)
			return code;
		if (plParams != null) {
			code = transceiver.setRxLineCoding(plParams.getLineCoding());
		} else {
			code = transceiver.setRxLineCoding(LineCoding.MANCHESTER);
		}
		if (code != 0)
			return code;
		if (plParams != null) {
			code = transceiver.setRxCarrierModulation(convertType1CarrierModulation(plParams.getCarrierModulation()));
		} else {
			code = transceiver.setRxCarrierModulation(convertType1CarrierModulation(Type1CarrierModulation.PCM_PSK_PM));
		}
		if (code != 0)
			return code;
		code = transceiver.setRxModulationIndex(convertType1ModulationIndex(plParams));
		if (code != 0)
			return code;
		int channelId = commParams.getFrequency().getCode();
		if (plParams != null && plParams.isUseExtendedFreqSet()) {
			channelId += 8;
		}
		String direction = invert ? "f" : "r";
		code = transceiver.setRxFrequency(config.getLong("frequency.3.ch" + channelId + direction, 0));
		if (code != 0)
			return code;
		code = transceiver.setRxCoding(convertType1Coding(commParams.getCoding(), plParams));
		if (code != 0)
			return code;
		code = transceiver.setRxCoherency(commParams.getCoherency());
		if (code != 0)
			return code;
		code = transceiver.setRxPolarization(Polarization.RHCP); // defined in 211.1
		if (code != 0)
			return code;
		if (plParams != null) {
			code = transceiver.setRxScrambler(plParams.getScrambler());
		} else {
			code = transceiver.setRxScrambler(ScramblerType.NONE);
		}
		if (code != 0)
			return code;
		if (plParams != null) {
			code = transceiver.setRxDifferentialEncoding(plParams.isDifferentialEncoding());
		} else {
			code = transceiver.setRxDifferentialEncoding(false);
		}
		return code;
	}

	private int applyType1TxParameters(Type1Directive params, boolean invert) {
		TxRxParameters commParams = invert ? params.getRxParams() : params.getTxParams();
		PlExtensions plParams = invert ? params.getRxPlExtensions() : params.getTxPlExtensions();
		int symbolRate = commParams.getDataRate().getRate();
		if (plParams != null && plParams.isUseExtendedDataRate()) {
			symbolRate = commParams.getDataRate().getExtendedRate();
		}
		int code = transceiver.setTxSymbolRate(symbolRate);
		if (code != 0)
			return code;
		if (plParams != null) {
			code = transceiver.setTxLineCoding(plParams.getLineCoding());
		} else {
			code = transceiver.setTxLineCoding(LineCoding.MANCHESTER);
		}
		if (code != 0)
			return code;
		if (plParams != null) {
			code = transceiver.setTxCarrierModulation(convertType1CarrierModulation(plParams.getCarrierModulation()));
		} else {
			code = transceiver.setTxCarrierModulation(CarrierModulation.PCM_PSK_PM);
		}
		if (code != 0)
			return code;
		code = transceiver.setTxModulationIndex(convertType1ModulationIndex(plParams));
		if (code != 0)
			return code;
		int channelId = commParams.getFrequency().getCode();
		if (plParams != null && plParams.isUseExtendedFreqSet()) {
			channelId += 8;
		}
		String direction = invert ? "r" : "f";
		code = transceiver.setTxFrequency(config.getLong("frequency.3.ch" + channelId + direction, 0));
		if (code != 0)
			return code;
		code = transceiver.setTxCoding(convertType1Coding(commParams.getCoding(), plParams));
		if (code != 0)
			return code;
		code = transceiver.setTxCoherency(commParams.getCoherency());
		if (code != 0)
			return code;
		code = transceiver.setTxPolarization(Polarization.RHCP); // defined in 211.1
		if (code != 0)
			return code;
		if (plParams != null) {
			code = transceiver.setTxScrambler(plParams.getScrambler());
		} else {
			code = transceiver.setTxScrambler(ScramblerType.NONE);
		}
		if (code != 0)
			return code;
		if (plParams != null) {
			code = transceiver.setTxDifferentialEncoding(plParams.isDifferentialEncoding());
		} else {
			code = transceiver.setTxDifferentialEncoding(false);
		}
		return code;
	}

	private Type5Directive createHailType5() {
		Type5Directive result = new Type5Directive();
		result.setRxLink(createType5TxRx(LinkDirection.RETURN));
		result.setTxLink(createType5TxRx(LinkDirection.FORWARD));
		return result;
	}

	private Type5LinkEstablishment createType5TxRx(LinkDirection direction) {
		Type5LinkEstablishment result = new Type5LinkEstablishment();
		if (symbolRate != -1) {
			result.setSymbolRate(symbolRate);
		} else {
			result.setSymbolRate(2000);
		}
		result.setTransceiverMode(TransceiverMode.USLP);
		result.setDemandQuery(Type5NegotiationType.DEMAND); // not specified in the spec
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
		result.setCarrierModulation(type5HailCarrierModulation);
		result.setCoding(Type4Coding.LDPC2048_1024);
		result.setModulationIndex(ModulationIndex.M0); // not specified in the spec
		return result;
	}

	private Type4Directive createHailType4() {
		Type4Directive result = new Type4Directive();
		result.setRxLink(createType4TxRx());
		result.setTxLink(createType4TxRx());
		return result;
	}

	private Type4LinkEstablishment createType4TxRx() {
		Type4LinkEstablishment result = new Type4LinkEstablishment();
		if (symbolRate != -1) {
			result.setSymbolRate(symbolRate);
		} else {
			result.setSymbolRate(2000);
		}
		result.setDemandQuery(Type4NegotiationType.DEMAND); // not specified in the spec
		result.setPolarization(Polarization.LHCP);
		result.setRnmd(false);
		result.setTransmitToken(false);

		int hailingChannel = channel;
		if (channel == -1) {
			hailingChannel = 0;
		}

		result.setFreqChannel(hailingChannel);
		result.setCarrierModulation(type4HailCarrierModulation);
		result.setCoding(Type4Coding.LDPC2048_1024);
		result.setModulationIndex(ModulationIndex.M0); // not specified in the spec
		return result;
	}

	private int applyType4RxParameters(Type4Directive directive, boolean invert) {
		Type4LinkEstablishment params = invert ? directive.getTxLink() : directive.getRxLink();
		LinkDirection direction = invert ? LinkDirection.FORWARD : LinkDirection.RETURN;
		int code = transceiver.setRxSymbolRate(params.getSymbolRate());
		if (code != 0)
			return code;
		if (params.getCarrierModulation().equals(Type4CarrierModulation.PCM_PM_BI_PHASE)) {
			code = transceiver.setRxLineCoding(LineCoding.MANCHESTER);
		} else if (params.getCarrierModulation().equals(Type4CarrierModulation.PCM_PM_NRZL)) {
			code = transceiver.setRxLineCoding(LineCoding.NRZ_L);
		} else {
			code = transceiver.setRxLineCoding(LineCoding.NONE);
		}
		if (code != 0)
			return code;
		code = transceiver.setRxCarrierModulation(convertType4CarrierModulation(params.getCarrierModulation()));
		if (code != 0)
			return code;
		code = transceiver.setRxModulationIndex(params.getModulationIndex());
		if (code != 0)
			return code;
		code = transceiver.setRxFrequency(config.getLong("frequency.4.ch" + params.getFreqChannel() + direction.getShortCode(), 0));
		if (code != 0)
			return code;
		code = transceiver.setRxCoding(convertType4Coding(params.getCoding()));
		if (code != 0)
			return code;
		code = transceiver.setRxCoherency(Coherency.UNKNOWN); // not clear from the spec
		if (code != 0)
			return code;
		code = transceiver.setRxPolarization(params.getPolarization());
		if (code != 0)
			return code;
		code = transceiver.setRxScrambler(ScramblerType.NONE); // not defined in the spec. assume none
		if (code != 0)
			return code;
		code = transceiver.setRxDifferentialEncoding(false); // not defined in the spec. assume false
		return code;
	}

	private int applyType4TxParameters(Type4Directive directive, boolean invert) {
		Type4LinkEstablishment params = invert ? directive.getRxLink() : directive.getTxLink();
		LinkDirection direction = invert ? LinkDirection.RETURN : LinkDirection.FORWARD;
		int code = transceiver.setTxSymbolRate(params.getSymbolRate());
		if (code != 0)
			return code;
		if (params.getCarrierModulation().equals(Type4CarrierModulation.PCM_PM_BI_PHASE)) {
			code = transceiver.setTxLineCoding(LineCoding.MANCHESTER);
		} else if (params.getCarrierModulation().equals(Type4CarrierModulation.PCM_PM_NRZL)) {
			code = transceiver.setTxLineCoding(LineCoding.NRZ_L);
		} else {
			code = transceiver.setTxLineCoding(LineCoding.NONE);
		}
		if (code != 0)
			return code;
		code = transceiver.setTxCarrierModulation(convertType4CarrierModulation(params.getCarrierModulation()));
		if (code != 0)
			return code;
		code = transceiver.setTxModulationIndex(params.getModulationIndex());
		if (code != 0)
			return code;
		code = transceiver.setTxFrequency(config.getLong("frequency.4.ch" + params.getFreqChannel() + direction.getShortCode(), 0));
		if (code != 0)
			return code;
		code = transceiver.setTxCoding(convertType4Coding(params.getCoding()));
		if (code != 0)
			return code;
		code = transceiver.setTxCoherency(Coherency.UNKNOWN); // not clear from the spec
		if (code != 0)
			return code;
		code = transceiver.setTxPolarization(params.getPolarization());
		if (code != 0)
			return code;
		code = transceiver.setTxScrambler(ScramblerType.NONE); // not defined in the spec. assume none
		if (code != 0)
			return code;
		code = transceiver.setTxDifferentialEncoding(false); // not defined in the spec. assume false
		return code;
	}

	private int applyType5RxParameters(Type5Directive directive, boolean invert) {
		Type5LinkEstablishment params = invert ? directive.getTxLink() : directive.getRxLink();
		int code = transceiver.setRxSymbolRate(params.getSymbolRate());
		if (code != 0)
			return code;
		if (params.getCarrierModulation().equals(Type4CarrierModulation.PCM_PM_BI_PHASE)) {
			code = transceiver.setRxLineCoding(LineCoding.MANCHESTER);
		} else if (params.getCarrierModulation().equals(Type4CarrierModulation.PCM_PM_NRZL)) {
			code = transceiver.setRxLineCoding(LineCoding.NRZ_L);
		} else {
			code = transceiver.setRxLineCoding(LineCoding.NONE);
		}
		if (code != 0)
			return code;
		code = transceiver.setRxCarrierModulation(convertType4CarrierModulation(params.getCarrierModulation()));
		if (code != 0)
			return code;
		code = transceiver.setRxModulationIndex(params.getModulationIndex());
		if (code != 0)
			return code;
		code = transceiver.setRxFrequency(params.getFrequency());
		if (code != 0)
			return code;
		code = transceiver.setRxCoding(convertType4Coding(params.getCoding()));
		if (code != 0)
			return code;
		code = transceiver.setRxCoherency(params.getCoherency());
		if (code != 0)
			return code;
		code = transceiver.setRxPolarization(params.getPolarization());
		if (code != 0)
			return code;
		code = transceiver.setRxScrambler(ScramblerType.NONE); // not defined in the spec. assume none
		if (code != 0)
			return code;
		code = transceiver.setRxDifferentialEncoding(false); // not defined in the spec. assume false
		return code;
	}

	private int applyType5TxParameters(Type5Directive directive, boolean invert) {
		Type5LinkEstablishment params = invert ? directive.getRxLink() : directive.getTxLink();
		int code = transceiver.setTxSymbolRate(params.getSymbolRate());
		if (code != 0)
			return code;
		if (params.getCarrierModulation().equals(Type4CarrierModulation.PCM_PM_BI_PHASE)) {
			code = transceiver.setTxLineCoding(LineCoding.MANCHESTER);
		} else if (params.getCarrierModulation().equals(Type4CarrierModulation.PCM_PM_NRZL)) {
			code = transceiver.setTxLineCoding(LineCoding.NRZ_L);
		} else {
			code = transceiver.setTxLineCoding(LineCoding.NONE);
		}
		if (code != 0)
			return code;
		code = transceiver.setTxCarrierModulation(convertType4CarrierModulation(params.getCarrierModulation()));
		if (code != 0)
			return code;
		code = transceiver.setTxModulationIndex(params.getModulationIndex());
		if (code != 0)
			return code;
		code = transceiver.setTxFrequency(params.getFrequency());
		if (code != 0)
			return code;
		code = transceiver.setTxCoding(convertType4Coding(params.getCoding()));
		if (code != 0)
			return code;
		code = transceiver.setTxCoherency(params.getCoherency());
		if (code != 0)
			return code;
		code = transceiver.setTxPolarization(params.getPolarization());
		if (code != 0)
			return code;
		code = transceiver.setTxScrambler(ScramblerType.NONE); // not defined in the spec. assume none
		if (code != 0)
			return code;
		code = transceiver.setTxDifferentialEncoding(false); // not defined in the spec. assume false
		return code;
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
