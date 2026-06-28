package com.leosatdata.sx127x;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.model.CarrierSuppression;
import com.leosatdata.model.Coherency;
import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.DataRate;
import com.leosatdata.model.Duplex;
import com.leosatdata.model.Frequency;
import com.leosatdata.model.LineCoding;
import com.leosatdata.model.Mode;
import com.leosatdata.model.PlExtensions;
import com.leosatdata.model.RsCodeType;
import com.leosatdata.model.ScramblerType;
import com.leosatdata.model.TxRxParameters;
import com.leosatdata.model.Type1CarrierModulation;
import com.leosatdata.model.Type1Coding;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.service.Node;
import com.leosatdata.service.NodeCallback;
import com.leosatdata.service.NodeTest;
import com.leosatdata.service.UserDataCallback;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.Util;

public class Sx127xNode {

	private final static Logger LOG = LoggerFactory.getLogger(Sx127xNode.class);

	private Configuration groundConfig;
	private Sx127xTransceiver groundTransceiver;
	private NodeCallback groundCallback;
	private UserDataCallback groundDataCallback;
	private Node ground;

	public static void main(String[] args) throws Exception {
		new Sx127xNode().run(args);
	}

	public void run(String[] args) throws Exception {
		Properties props = new Properties();
		props.putAll(NodeTest.load("src/main/resources/mib.properties"));
		props.putAll(NodeTest.load("src/test/resources/sx127x.properties"));
		props.putAll(NodeTest.load("src/main/resources/custom.properties"));
		for (String cur : args) {
			props.putAll(NodeTest.load(cur));
		}
		groundConfig = new Configuration(props);
		Sx127x device = new Sx127x(groundConfig.getProperty("sx127x_port"), groundConfig.getInteger("sx127x_timeout", 0), groundConfig.getProperty("local_spacecraft_id"));
		groundTransceiver = new Sx127xTransceiver(device, groundConfig);
		groundTransceiver.start();
		groundCallback = new NodeCallback() {

			@Override
			public void handle(ControllerEvent event) {
				LOG.info("received event: {}", event);
			}
		};
		groundDataCallback = new UserDataCallback() {

			@Override
			public void onPacketData(byte[] packet, int portId) {
				System.out.println("Received: \"" + new String(packet) + "\" (" + Util.bytesToHex(packet) + ")");
			}

			@Override
			public void onPacketStream(byte[] data, int portId, int gapFramesCount, int firstHeaderPointer) {
				System.out.println("Received: \"" + new String(data) + "\" (" + Util.bytesToHex(data) + ")");
			}
		};
		ground = new Node(groundConfig, groundTransceiver, groundCallback, groundDataCallback);
		ground.start();
		// only half duplex supported
		ground.setDuplex(Duplex.HALF_DUPLEX);

		try (Terminal terminal = TerminalBuilder.builder().build()) {
			LineReader reader = LineReaderBuilder.builder().terminal(terminal).completer(createCompleter()).build();

			System.out.println("=== CCSDS 235.1 Simulator ===");
			System.out.println("Type commands or messages. Type 'help' for available commands.");
			System.out.println("Press Tab for auto-completion.\n");

			while (true) {
				try {
					String line = reader.readLine("> ").trim();
					if (line.isEmpty())
						continue;

					if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
						break;
					}

					if (line.equalsIgnoreCase("help")) {
						printHelp();
						continue;
					}

					handleInput(line);
				} catch (UserInterruptException | EndOfFileException e) {
					System.out.println("\nExiting...");
					break;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		ground.stop();
		groundTransceiver.stop();
	}

	private void handleInput(String input) {
		String lower = input.toLowerCase().trim();

		// Command: listen
		if (lower.equals("listen")) {
			ground.waitForSession();
			return;
		}

		// Command: initiate session
		if (lower.equals("connect")) {
			ground.setCommParameters(createType1CommParameters(DataRate.R8NC));
			ground.startSession();
			return;
		}

		// Default: treat as message to send
		ground.send(input.getBytes(StandardCharsets.UTF_8), QualityOfService.EXPEDITED, 0);
	}
	
	private static Type1Directive createType1CommParameters(DataRate dataRate) {
		TxRxParameters tx = new TxRxParameters();
		tx.setCoding(Type1Coding.NONE);
		tx.setDataRate(dataRate);
		tx.setFrequency(Frequency.CH3);
		tx.setMode(Mode.PROXIMITY1);
		tx.setCoherency(Coherency.COHERENT_PSK);

		PlExtensions txExt = new PlExtensions();
		txExt.setCarrierModulation(Type1CarrierModulation.FSK);
		txExt.setLineCoding(LineCoding.MANCHESTER);
		txExt.setDifferentialEncoding(false);
		txExt.setCarrierSuppression(CarrierSuppression.RESIDUAL_CARRIER);
		txExt.setRsCode(RsCodeType.RS255_239);
		txExt.setScrambler(ScramblerType.CCITT);
		txExt.setUseExtendedDataRate(false);
		txExt.setUseExtendedFreqSet(false);

		TxRxParameters rx = new TxRxParameters();
		rx.setCoding(Type1Coding.NONE);
		rx.setDataRate(dataRate);
		rx.setFrequency(Frequency.CH3);
		rx.setMode(Mode.PROXIMITY1);
		rx.setCoherency(Coherency.COHERENT_PSK);

		PlExtensions rxExt = new PlExtensions();
		rxExt.setCarrierModulation(Type1CarrierModulation.FSK);
		rxExt.setLineCoding(LineCoding.MANCHESTER);
		rxExt.setDifferentialEncoding(false);
		rxExt.setCarrierSuppression(CarrierSuppression.RESIDUAL_CARRIER);
		rxExt.setRsCode(RsCodeType.RS255_239);
		rxExt.setScrambler(ScramblerType.CCITT);
		rxExt.setUseExtendedDataRate(false);
		rxExt.setUseExtendedFreqSet(false);

		Type1Directive result = new Type1Directive();
		result.setTxParams(tx);
		result.setTxPlExtensions(txExt);
		result.setRxParams(rx);
		result.setRxPlExtensions(rxExt);
		return result;
	}

	private static void printHelp() {
		// @formatter:off
		System.out.println(
				"Waiting for connection configuration:\n"
						+ "listen\n"
						+ "\n"
						+ "Connection initiator:\n"
						+ "connect\n"
						+ "\n"
						+ "If session established, then any data treated as data to send."
						+ "Other commands:"
						+ "- exit\n"
						+ "- help (this help)");
		// @formatter:on
	}

	private static Completer createCompleter() {
		// Command completers
		StringsCompleter commands = new StringsCompleter("listen", "connect", "help", "exit", "quit");

		return new AggregateCompleter(commands);
	}

}
