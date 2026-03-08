package com.leosatdata.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import com.leosatdata.model.CarrierModulation;
import com.leosatdata.model.ControlParameters;
import com.leosatdata.model.DataEncoding;
import com.leosatdata.model.DataModulation;
import com.leosatdata.model.DataRate;
import com.leosatdata.model.Direction;
import com.leosatdata.model.Duplex;
import com.leosatdata.model.Frequency;
import com.leosatdata.model.Mode;
import com.leosatdata.model.ModeSelect;
import com.leosatdata.model.Modulation;
import com.leosatdata.model.PlExtensions;
import com.leosatdata.model.ReportRequest;
import com.leosatdata.model.RsCodeType;
import com.leosatdata.model.ScramblerType;
import com.leosatdata.model.Spdu;
import com.leosatdata.model.SpduHeader;
import com.leosatdata.model.TxRxParameters;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Vr;

public class SpduParserTest {

	private SpduParser parser;

	@Test
	public void testSuccess() throws Exception {
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
		props.putAll(load("src/main/resources/mib.properties"));
		props.putAll(load("src/main/resources/transceiver.properties"));
		props.putAll(load("src/main/resources/custom.properties"));
		props.putAll(load("src/test/resources/ground.properties"));
		Configuration config = new Configuration(props);
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

	private static Properties load(String file) throws Exception {
		Properties result = new Properties();
		try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
			result.load(is);
		}
		return result;
	}
}
