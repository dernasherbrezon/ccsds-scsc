package com.leosatdata.util;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import com.leosatdata.model.CarrierSuppression;
import com.leosatdata.model.Coherency;
import com.leosatdata.model.ControlParameters;
import com.leosatdata.model.DataRate;
import com.leosatdata.model.DaySegmented;
import com.leosatdata.model.Duplex;
import com.leosatdata.model.Frequency;
import com.leosatdata.model.LineCoding;
import com.leosatdata.model.Mode;
import com.leosatdata.model.ModulationIndex;
import com.leosatdata.model.PlExtensions;
import com.leosatdata.model.Plcw;
import com.leosatdata.model.PnRaging;
import com.leosatdata.model.Polarization;
import com.leosatdata.model.QueryResponse;
import com.leosatdata.model.RangingCode;
import com.leosatdata.model.RangingModulationIndex;
import com.leosatdata.model.RangingReportRequest;
import com.leosatdata.model.RangingType;
import com.leosatdata.model.ReportRequest;
import com.leosatdata.model.RsCodeType;
import com.leosatdata.model.ScramblerType;
import com.leosatdata.model.Spdu;
import com.leosatdata.model.SpduHeader;
import com.leosatdata.model.StatusReport;
import com.leosatdata.model.TimeDistribution;
import com.leosatdata.model.TimeDistributionType;
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
import com.leosatdata.model.Vr;

public class SpduParserTest {

	private Configuration config;
	private SpduParser parser;

	@Test
	public void testUnsupportedSpdu() throws Exception {
		byte[] payload = new byte[2];
		payload[0] = 0b0110_0000;
		payload[1] = 1;

		Object actual;
		try (BitInputStream bis = new BitInputStream(payload)) {
			actual = parser.decode(bis);
		}
		assertNull(actual);
	}

	@Test
	public void testPlcw() throws Exception {
		config.setProperty("maximum_frame_sequence_length", "1");
		parser = new SpduParser(config);
		SpduHeader header = new SpduHeader();
		header.setFormat(SpduParser.FIXED_SPDU);
		header.setType(Plcw.SHORT_PLCW);

		Plcw plcw = new Plcw();
		plcw.setHeader(header);
		plcw.setExpeditedFrameCounter(2);
		plcw.setPcid(0);
		plcw.setReportValue(3);
		plcw.setRetransmit(false);

		assertDirectives(plcw);

		config.setProperty("maximum_frame_sequence_length", "2");
		parser = new SpduParser(config);
		header = new SpduHeader();
		header.setFormat(SpduParser.FIXED_SPDU);
		header.setType(Plcw.LONG_PLCW);

		plcw.setHeader(header);

		assertDirectives(plcw);
	}

	@Test
	public void testType5() throws Exception {
		Type5Directive type5 = createValidType5();
		parser.validateCommParameters(type5);
		assertDirectives(type5);
	}

	@Test
	public void testType4() throws Exception {
		Type4Directive type4 = createValidType4();
		parser.validateCommParameters(type4);
		assertDirectives(type4);
	}

	@Test
	public void testType3() throws Exception {
		SpduHeader header = new SpduHeader();
		header.setFormat(SpduParser.VARIABLE_SPDU);
		header.setType(SpduParser.TYPE3_DIRECTIVE);

		StatusReport type3 = new StatusReport();
		type3.setHeader(header);
		byte[] report = new byte[16];
		report[0] = 0;
		report[1] = 1;
		report[2] = 2;
		type3.setReport(report);

		assertDirectives(type3);
	}

	@Test
	public void testType2() throws Exception {
		SpduHeader header = new SpduHeader();
		header.setFormat(SpduParser.VARIABLE_SPDU);
		header.setType(SpduParser.TYPE2_DIRECTIVE);

		TimeDistribution type2 = new TimeDistribution();
		type2.setHeader(header);
		type2.setType(TimeDistributionType.NULL);
		type2.setTransceiverClock(System.currentTimeMillis());
		type2.setSendSideDelay(12);
		type2.setOneWayLightTime(15);

		assertDirectives(type2);
	}

	@Test
	public void testType1() throws Exception {
		Type1Directive type1 = createValidType1();
		parser.validateCommParameters(type1);
		assertDirectives(type1);

		// type1 can't contain all possible directives due to the length size limitation
		Type1Directive type1Small = new Type1Directive();
		type1Small.setHeader(type1.getHeader());
		type1Small.setSourceSpacecraftId(45);
		type1Small.setControlParams(type1.getControlParams());
		type1Small.setReportReq(type1.getReportReq());
		type1Small.setVr(type1.getVr());

		assertDirectives(type1Small);

	}

	@Test
	public void testValidateCommParameters() throws Exception {
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type1Directive type1 = createValidType1();
				type1.setRxParams(null);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type1Directive type1 = createValidType1();
				type1.setTxParams(null);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type1Directive type1 = createValidType1();
				type1.getRxParams().setFrequency(null);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type1Directive type1 = createValidType1();
				type1.getRxParams().setFrequency(Frequency.UNKNOWN);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type1Directive type1 = createValidType1();
				type1.getRxParams().setDataRate(null);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type1Directive type1 = createValidType1();
				type1.getRxParams().setDataRate(DataRate.UNKNOWN);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type1Directive type1 = createValidType1();
				type1.getTxParams().setFrequency(null);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type1Directive type1 = createValidType1();
				type1.getTxParams().setDataRate(null);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type4Directive type1 = createValidType4();
				type1.setRxLink(null);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type4Directive type1 = createValidType4();
				type1.getRxLink().setFreqChannel(-1);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type4Directive type1 = createValidType4();
				type1.getRxLink().setFreqChannel(32);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type4Directive type1 = createValidType4();
				type1.getRxLink().setSymbolRate(-1);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type4Directive type1 = createValidType4();
				type1.setTxLink(null);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type4Directive type1 = createValidType4();
				type1.getTxLink().setFreqChannel(-1);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type4Directive type1 = createValidType4();
				type1.getTxLink().setFreqChannel(32);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type4Directive type1 = createValidType4();
				type1.getTxLink().setSymbolRate(-1);
				parser.validateCommParameters(type1);
			}
		});

		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type5Directive type1 = createValidType5();
				type1.setTxLink(null);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type5Directive type1 = createValidType5();
				type1.getTxLink().setAosFrameLength(-1);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type5Directive type1 = createValidType5();
				type1.getTxLink().setAosFrameLength(65536);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type5Directive type1 = createValidType5();
				type1.getTxLink().setFrequency(-1);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type5Directive type1 = createValidType5();
				type1.getTxLink().setSymbolRate(-1);
				parser.validateCommParameters(type1);
			}
		});
		assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				Type5Directive type1 = createValidType5();
				type1.setRxLink(null);
				parser.validateCommParameters(type1);
			}
		});
	}

	@Before
	public void start() throws Exception {
		Properties props = new Properties();
		props.putAll(TestUtil.load("src/main/resources/mib.properties"));
		props.putAll(TestUtil.load("src/main/resources/transceiver.properties"));
		props.putAll(TestUtil.load("src/main/resources/custom.properties"));
		props.putAll(TestUtil.load("src/test/resources/ground.properties"));
		config = new Configuration(props);
		parser = new SpduParser(config);
	}

	private static Type1Directive createValidType1() {
		SpduHeader header = new SpduHeader();
		header.setFormat(SpduParser.VARIABLE_SPDU);
		header.setType(SpduParser.TYPE1_DIRECTIVE);

		ControlParameters controlParameters = new ControlParameters();
		controlParameters.setDuplex(Duplex.FULL_DUPLEX);
		controlParameters.setRnmd(true);
		controlParameters.setTimeSampleField(12);
		controlParameters.setTransmitToken(true);

		ReportRequest reportReq = new ReportRequest();
		reportReq.setPcid0PlcwRequest(true);
		reportReq.setPcid1PlcwRequest(false);
		reportReq.setStatusReportRequest(1);
		reportReq.setTimeTagRequest(2);

		TxRxParameters rxParams = new TxRxParameters();
		rxParams.setCoding(Type1Coding.CONCANTENATED);
		rxParams.setDataRate(DataRate.R128NC);
		rxParams.setFrequency(Frequency.CH3);
		rxParams.setMode(Mode.PROXIMITY1);
		rxParams.setCoherency(Coherency.COHERENT_PSK);

		PlExtensions rxPlExt = new PlExtensions();
		rxPlExt.setCarrierModulation(Type1CarrierModulation.PCM_PSK_PM);
		rxPlExt.setLineCoding(LineCoding.MANCHESTER);
		rxPlExt.setDifferentialEncoding(true);
		rxPlExt.setCarrierSuppression(CarrierSuppression.RESIDUAL_CARRIER);
		rxPlExt.setRsCode(RsCodeType.RS255_239);
		rxPlExt.setScrambler(ScramblerType.CCITT);
		rxPlExt.setUseExtendedDataRate(false);
		rxPlExt.setUseExtendedFreqSet(false);

		TxRxParameters txParams = new TxRxParameters();
		txParams.setCoding(Type1Coding.CONCANTENATED);
		txParams.setDataRate(DataRate.R128NC);
		txParams.setFrequency(Frequency.CH6);
		txParams.setMode(Mode.PROXIMITY1);
		txParams.setCoherency(Coherency.COHERENT_PSK);

		PlExtensions txPlExt = new PlExtensions();
		txPlExt.setCarrierModulation(Type1CarrierModulation.PCM_PSK_PM);
		txPlExt.setLineCoding(LineCoding.NRZ_L);
		txPlExt.setDifferentialEncoding(true);
		txPlExt.setCarrierSuppression(CarrierSuppression.RESIDUAL_CARRIER);
		txPlExt.setRsCode(RsCodeType.RS255_239);
		txPlExt.setScrambler(ScramblerType.CCITT);
		txPlExt.setUseExtendedDataRate(false);
		txPlExt.setUseExtendedFreqSet(false);

		Vr vr = new Vr();
		vr.setFrameSequenceNumber(123);

		Type1Directive type1 = new Type1Directive();
		type1.setHeader(header);
		type1.setRxParams(rxParams);
		type1.setRxPlExtensions(rxPlExt);
		type1.setTxParams(txParams);
		type1.setTxPlExtensions(txPlExt);
		return type1;
	}

	private static Type5Directive createValidType5() {
		SpduHeader header = new SpduHeader();
		header.setFormat(SpduParser.VARIABLE_SPDU);
		header.setType(SpduParser.TYPE5_DIRECTIVE);

		Type5LinkEstablishment rx = new Type5LinkEstablishment();
		rx.setRnmd(false);
		rx.setCoding(Type4Coding.CONVOLUTIONAL);
		rx.setDuplex(Duplex.FULL_DUPLEX);
		rx.setLinkSnr(2);
		rx.setCoherency(Coherency.COHERENT_PSK);
		rx.setFrequency(2240000000f);
		rx.setSymbolRate(8400);
		rx.setDemandQuery(Type5NegotiationType.DEMAND);
		rx.setModulationIndex(ModulationIndex.M0);
		rx.setCarrierModulation(Type4CarrierModulation.BPSK);
		rx.setPolarization(Polarization.LHCP);
		rx.setTransmitToken(false);
		rx.setAosFrameLength(23);
		rx.setTransceiverMode(TransceiverMode.USLP);

		Type5LinkEstablishment tx = new Type5LinkEstablishment();
		tx.setRnmd(false);
		tx.setCoding(Type4Coding.CONVOLUTIONAL);
		tx.setDuplex(Duplex.FULL_DUPLEX);
		tx.setLinkSnr(2);
		tx.setCoherency(Coherency.COHERENT_PSK);
		tx.setFrequency(2250000000f);
		tx.setSymbolRate(8400);
		tx.setDemandQuery(Type5NegotiationType.DEMAND);
		tx.setModulationIndex(ModulationIndex.M0);
		tx.setCarrierModulation(Type4CarrierModulation.BPSK);
		tx.setPolarization(Polarization.LHCP);
		tx.setTransmitToken(false);
		tx.setAosFrameLength(23);
		tx.setTransceiverMode(TransceiverMode.USLP);

		PnRaging pnRaging = new PnRaging();
		pnRaging.setChipRateK(3);
		pnRaging.setChipRateL(2);
		pnRaging.setChipRateM(3);
		pnRaging.setPnEpochTime(new DaySegmented());
		pnRaging.setRangingCode(RangingCode.T2B);
		pnRaging.setRangingType(RangingType.ONE_WAY);
		pnRaging.setReportRequest(RangingReportRequest.OBSERVABLE);
		pnRaging.setModulationIndex(RangingModulationIndex.D70);

		Vr vr = new Vr();
		vr.setFrameSequenceNumber(123);

		ReportRequest reportReq = new ReportRequest();
		reportReq.setPcid0PlcwRequest(true);
		reportReq.setPcid1PlcwRequest(false);
		reportReq.setStatusReportRequest(1);
		reportReq.setTimeTagRequest(2);

		Type5Directive type5 = new Type5Directive();
		type5.setHeader(header);
		type5.setVr(vr);
		type5.setRxLink(rx);
		type5.setTxLink(tx);
		type5.setPnRanging(pnRaging);
		type5.setReportRequest(reportReq);
		type5.setSourceSpacecraftId(14);
		return type5;
	}

	private static Type4Directive createValidType4() {
		SpduHeader header = new SpduHeader();
		header.setFormat(SpduParser.VARIABLE_SPDU);
		header.setType(SpduParser.TYPE4_DIRECTIVE);

		ReportRequest reportReq = new ReportRequest();
		reportReq.setPcid0PlcwRequest(true);
		reportReq.setPcid1PlcwRequest(false);
		reportReq.setStatusReportRequest(1);
		reportReq.setTimeTagRequest(2);

		Type4LinkEstablishment rx = new Type4LinkEstablishment();
		rx.setCoding(Type4Coding.CONVOLUTIONAL);
		rx.setDemandQuery(Type4NegotiationType.DEMAND);
		rx.setDuplex(Duplex.FULL_DUPLEX);
		rx.setFreqChannel(3);
		rx.setLinkSnr(5);
		rx.setCarrierModulation(Type4CarrierModulation.BPSK);
		rx.setModulationIndex(ModulationIndex.M0);
		rx.setPolarization(Polarization.LHCP);
		rx.setQueryResponse(QueryResponse.UNKNOWN);
		rx.setRnmd(false);
		rx.setSymbolRate(4800);
		rx.setTransmitToken(false);

		Type4LinkEstablishment tx = new Type4LinkEstablishment();
		tx.setCoding(Type4Coding.CONVOLUTIONAL);
		tx.setDemandQuery(Type4NegotiationType.DEMAND);
		tx.setDuplex(Duplex.FULL_DUPLEX);
		tx.setFreqChannel(5);
		tx.setLinkSnr(1);
		tx.setCarrierModulation(Type4CarrierModulation.BPSK);
		tx.setModulationIndex(ModulationIndex.M0);
		tx.setPolarization(Polarization.LHCP);
		tx.setQueryResponse(QueryResponse.UNKNOWN);
		tx.setRnmd(false);
		tx.setSymbolRate(4800);
		tx.setTransmitToken(false);

		Vr vr = new Vr();
		vr.setFrameSequenceNumber(123);

		Type4Directive type4 = new Type4Directive();
		type4.setHeader(header);
		type4.setReportRequest(reportReq);
		type4.setRxLink(rx);
		type4.setTxLink(tx);
		type4.setSourceSpacecraftId(5);
		type4.setVr(vr);
		return type4;
	}

	private void assertDirectives(Spdu expected) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (BitOutputStream bos = new BitOutputStream(baos)) {
			parser.encode(expected, bos);
		}

		Object actual;
		try (BitInputStream bis = new BitInputStream(baos.toByteArray())) {
			actual = parser.decode(bis);
		}
		TestUtil.assertObjectEqual(expected, actual);
	}

}
