package com.leosatdata.util;

import static org.junit.Assert.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import com.leosatdata.model.CarrierModulation;
import com.leosatdata.model.Coding;
import com.leosatdata.model.ControlParameters;
import com.leosatdata.model.DataEncoding;
import com.leosatdata.model.DataModulation;
import com.leosatdata.model.DataRate;
import com.leosatdata.model.DaySegmented;
import com.leosatdata.model.Direction;
import com.leosatdata.model.Duplex;
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
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type4Directive;
import com.leosatdata.model.Type5Directive;
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
		config.setProperty("spdu_version", "1");
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

		config.setProperty("spdu_version", "2");
		parser = new SpduParser(config);
		header = new SpduHeader();
		header.setFormat(SpduParser.FIXED_SPDU);
		header.setType(Plcw.LONG_PLCW);

		plcw.setHeader(header);

		assertDirectives(plcw);
	}

	@Test
	public void testType5() throws Exception {
		SpduHeader header = new SpduHeader();
		header.setFormat(SpduParser.VARIABLE_SPDU);
		header.setType(SpduParser.TYPE5_DIRECTIVE);

		Link2Establishment rx = new Link2Establishment();
		rx.setRnmd(false);
		rx.setCoding(Coding.CONVOLUTIONAL);
		rx.setDuplex(Duplex.FULL_DUPLEX);
		rx.setLinkSnr(2);
		rx.setCoherency(Modulation.COHERENT_PSK);
		rx.setDirection(LinkDirection.RETURN);
		rx.setFrequency(2240000000f);
		rx.setSymbolRate(8400);
		rx.setDemandQuery(NegotiationType2.DEMAND);
		rx.setModulationIndex(ModulationIndex.M0);
		rx.setModulation(LinkModulation.BPSK);
		rx.setPolarization(Polarization.LHCP);
		rx.setTransmitToken(false);
		rx.setAosFrameLength(23);
		rx.setTransceiverMode(TransceiverMode.USLP);

		Link2Establishment tx = new Link2Establishment();
		tx.setRnmd(false);
		tx.setCoding(Coding.CONVOLUTIONAL);
		tx.setDuplex(Duplex.FULL_DUPLEX);
		tx.setLinkSnr(2);
		tx.setCoherency(Modulation.COHERENT_PSK);
		tx.setDirection(LinkDirection.FORWARD);
		tx.setFrequency(2250000000f);
		tx.setSymbolRate(8400);
		tx.setDemandQuery(NegotiationType2.DEMAND);
		tx.setModulationIndex(ModulationIndex.M0);
		tx.setModulation(LinkModulation.BPSK);
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

		assertDirectives(type5);
	}

	@Test
	public void testType4() throws Exception {
		SpduHeader header = new SpduHeader();
		header.setFormat(SpduParser.VARIABLE_SPDU);
		header.setType(SpduParser.TYPE4_DIRECTIVE);

		ReportRequest reportReq = new ReportRequest();
		reportReq.setPcid0PlcwRequest(true);
		reportReq.setPcid1PlcwRequest(false);
		reportReq.setStatusReportRequest(1);
		reportReq.setTimeTagRequest(2);

		LinkEstablishment rx = new LinkEstablishment();
		rx.setCoding(Coding.CONVOLUTIONAL);
		rx.setDemandQuery(NegotiationType.DEMAND);
		rx.setDirection(LinkDirection.RETURN);
		rx.setDuplex(Duplex.FULL_DUPLEX);
		rx.setFreqChannel(3);
		rx.setLinkSnr(5);
		rx.setModulation(LinkModulation.BPSK);
		rx.setModulationIndex(ModulationIndex.M0);
		rx.setPolarization(Polarization.LHCP);
		rx.setQueryResponse(QueryResponse.UNKNOWN);
		rx.setRnmd(false);
		rx.setSymbolRate(4800);
		rx.setTransmitToken(false);

		LinkEstablishment tx = new LinkEstablishment();
		tx.setCoding(Coding.CONVOLUTIONAL);
		tx.setDemandQuery(NegotiationType.DEMAND);
		tx.setDirection(LinkDirection.FORWARD);
		tx.setDuplex(Duplex.FULL_DUPLEX);
		tx.setFreqChannel(5);
		tx.setLinkSnr(1);
		tx.setModulation(LinkModulation.BPSK);
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
		type2.setTransceiverClock(System.currentTimeMillis() / 1000);
		type2.setSendSideDelay(12);
		type2.setOneWayLightTime(15);

		assertDirectives(type2);
	}

	@Test
	public void testType1() throws Exception {
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
		rxParams.setDataEncoding(DataEncoding.CONCANTENATED);
		rxParams.setDataRate(DataRate.R128NC);
		rxParams.setFrequency(Frequency.CH3);
		rxParams.setMode(Mode.PROXIMITY1);
		rxParams.setModulation(Modulation.COHERENT_PSK);

		PlExtensions rxPlExt = new PlExtensions();
		rxPlExt.setCarrierModulation(CarrierModulation.PSK);
		rxPlExt.setDataModulation(DataModulation.NRZ_L);
		rxPlExt.setDifferentialEncoding(true);
		rxPlExt.setDirection(Direction.RECEIVER);
		rxPlExt.setModeSelect(ModeSelect.RESIDUAL_CARRIER);
		rxPlExt.setRsCode(RsCodeType.RS255_239);
		rxPlExt.setScrambler(ScramblerType.CCITT);
		rxPlExt.setUseExtendedDataRate(false);
		rxPlExt.setUseExtendedFreqSet(false);

		TxRxParameters txParams = new TxRxParameters();
		txParams.setDataEncoding(DataEncoding.CONCANTENATED);
		txParams.setDataRate(DataRate.R128NC);
		txParams.setFrequency(Frequency.CH6);
		txParams.setMode(Mode.PROXIMITY1);
		txParams.setModulation(Modulation.COHERENT_PSK);

		PlExtensions txPlExt = new PlExtensions();
		txPlExt.setCarrierModulation(CarrierModulation.PSK);
		txPlExt.setDataModulation(DataModulation.NRZ_L);
		txPlExt.setDifferentialEncoding(true);
		txPlExt.setDirection(Direction.RECEIVER);
		txPlExt.setModeSelect(ModeSelect.RESIDUAL_CARRIER);
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

		assertDirectives(type1);

		// type1 can't contain all possible directives to the length size limitation
		type1 = new Type1Directive();
		type1.setHeader(header);
		type1.setSourceSpacecraftId(45);
		type1.setControlParams(controlParameters);
		type1.setReportReq(reportReq);
		type1.setVr(vr);

		assertDirectives(type1);
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
