/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

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
import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.DataRate;
import com.leosatdata.model.Duplex;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.Util;

public class CliNode {

	private final static Logger LOG = LoggerFactory.getLogger(CliNode.class);

	private Configuration groundConfig;
	private TcpTransceiver groundTransceiver;
	private NodeCallback groundCallback;
	private UserDataCallback groundDataCallback;
	private Node ground;

	public static void main(String[] args) throws Exception {
		new CliNode().run(args);
	}

	public void run(String[] args) throws Exception {
		Properties props = new Properties();
		props.putAll(NodeTest.load("src/main/resources/mib.properties"));
		props.putAll(NodeTest.load("src/main/resources/transceiver.properties"));
		props.putAll(NodeTest.load("src/main/resources/custom.properties"));
		for (String cur : args) {
			props.putAll(NodeTest.load(cur));
		}
		groundConfig = new Configuration(props);
		groundTransceiver = new TcpTransceiver(groundConfig);
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

		// Command: duplex <FULL_DUPLEX|HALF_DUPLEX>
		if (lower.startsWith("duplex ")) {
			String mode = input.substring("duplex ".length()).trim().toUpperCase();
			if (mode.equals(Duplex.FULL_DUPLEX.name()) || mode.equals(Duplex.HALF_DUPLEX.name())) {
				ground.setDuplex(Duplex.valueOf(mode));
				System.out.println("✓ Duplex mode set to: " + mode);
			} else {
				System.out.println("✗ Invalid duplex mode. Use " + Duplex.FULL_DUPLEX.name() + " or " + Duplex.HALF_DUPLEX.name());
			}
			return;
		}

		// Command: local <host:port>
		if (lower.startsWith("local ")) {
			String addr = input.substring("local ".length()).trim();
			if (isValidAndSetHostPort("tcp.transceiver", addr)) {
				groundConfig.setProperty("", lower);
				System.out.println("✓ Listen address set to: " + addr);
			} else {
				System.out.println("✗ Invalid format. Use host:port (e.g. 0.0.0.0:8080)");
			}
			return;
		}

		// Command: remote <host:port>
		if (lower.startsWith("remote ")) {
			String addr = input.substring("remote ".length()).trim();
			if (isValidAndSetHostPort("tcp.transceiver.remote", addr)) {
				System.out.println("✓ Remote set to: " + addr);
			} else {
				System.out.println("✗ Invalid format. Use host:port (e.g. localhost:8080)");
			}
			return;
		}

		// Command: listen
		if (lower.equals("listen")) {
			ground.waitForSession();
			return;
		}

		// Command: initiate session
		if (lower.equals("connect")) {
			ground.setCommParameters(NodeTest.createType1CommParameters(DataRate.R128C));
			ground.startSession();
			return;
		}

		// Default: treat as message to send
		ground.send(input.getBytes(StandardCharsets.UTF_8), QualityOfService.EXPEDITED, 0);
	}

	private boolean isValidAndSetHostPort(String prefix, String addr) {
		if (!addr.contains(":"))
			return false;
		String[] parts = addr.split(":", 2);
		if (parts.length != 2)
			return false;
		try {
			int port = Integer.parseInt(parts[1]);
			if (port < 0 || port > 65536) {
				return false;
			}
			groundConfig.setProperty(prefix + ".host", parts[0]);
			groundConfig.setProperty(prefix + ".port", parts[1]);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static void printHelp() {
		// @formatter:off
		System.out.println(
				"Waiting for connection configuration:\n"
						+ "(optional) duplex FULL_DUPLEX|HALF_DUPLEX\n"
						+ "local <host:port>     (e.g. 0.0.0.0:8080)\n"
						+ "remote <host:port>     (e.g. 0.0.0.0:8080)\n"
						+ "listen\n"
						+ "\n"
						+ "Connection initiator:\n"
						+ "(optional) duplex FULL_DUPLEX|HALF_DUPLEX\n"
						+ "local <host:port>     (e.g. 0.0.0.0:8080)\n"
						+ "remote <host:port>     (e.g. 0.0.0.0:8080)\n"
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
		StringsCompleter commands = new StringsCompleter("duplex FULL_DUPLEX", "duplex HALF_DUPLEX", "listen", "connect", "local ", "remote ", "help", "exit", "quit");

		return new AggregateCompleter(commands,
				// You can add more sophisticated argument completers here
				new StringsCompleter("FULL_DUPLEX", "HALF_DUPLEX"));
	}

}
