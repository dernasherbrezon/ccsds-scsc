## User guide

This document aims to cover several typical use cases. All code explained line by line. If something is not covered here, please check unit tests under ./src/test/java directory.

### Key concepts

#### Configuration

All configuration is made through .properties file. This properties can be combined from multiple locations, override each other to provide final configuration. The key class is `com.leosatdata.util.Configuration` which wraps these properties and provide some basic validation.

```java
Properties props = new Properties();
props.putAll(load("src/main/resources/mib.properties"));
props.putAll(load("src/main/resources/transceiver.properties"));
props.putAll(load("src/main/resources/custom.properties"));
props.putAll(load("src/test/resources/ground.properties"));
props.putAll(load("src/test/resources/channel.properties"));
Configuration groundConfig = new Configuration(props);
```

This implementation contains several .properties files:

 * **mib.properties** - This is set of MIB properties as defined in CCSDS 235.1
 * **transceiver.properties** - Set of pre-defined frequency channels to use for each mode. Type1 and Type4 directives use frequency channel number instead of the actual frequency value.
 * **custom.properties** - This is set of implementation-specific set of properties. All values have reasonable default and commentary

#### Transceiver

`com.leosatdata.service.Transceiver` - is an interface to the physical layer. This interface has several implementations:

 * `com.leosatdata.service.NullTransceiver` - This transceiver is used in tests, when sender and receiver are running within the same JVM. It has several features:
   * Asynchronous RX and TX_COMPELETE
   * Simulate radio channel latencies
   * Simulate TX modulation latencies
   * Simulate message loss and smart filtering
   * Simulate RX and TX lifecycles. I.e. receiver cannot RX when transmitter is generating carrier only.
   * Drop messages and report errors if receiver's RX and transmitter's TX on different frequencies or use different symbol rates
 * `com.leosatdata.service.HalfDuplexTransceiver` - Same as above, but additionally simulate half duplex transceiver behaivour: switch RX OFF when TX is active and vice versa
 * `com.leosatdata.service.TcpTransceiver` - This transceiver is used in the TCP-based examples. It can:
   * Transfer PDU over TCP frames. TCP can naturally split PDU into several blocks. TcpTransceiver has special C&S layer that can do PDU synchronization and report received PDU as one byte array
   * Simulate RX and TX link via separate sockets
   * Simulate radio channel latencies
   * Simulate TX modulation latencies

This interface is designed to be as close as possible to the hardware. For example, all set/get methods can be implemented as SPI registers. All callbacks can be implemented as interrupts from the hardware.   

#### Node

`com.leosatdata.service.Node` is the entry point to the library. This is main class responsible for initialization, handling and shutdown of all communications.

### Basic usage

Basic use case can be the following:

 * start responder and go into listen state (connecting-L)
 * start caller and initiate session with responder (connecting-T)
 * send sample packet
 * gracefully terminate session
 
First step will be loading configuration:

```java
Properties props = new Properties();
props.putAll(load("src/main/resources/mib.properties"));
props.putAll(load("src/main/resources/transceiver.properties"));
props.putAll(load("src/main/resources/custom.properties"));
props.putAll(load("src/test/resources/ground.properties"));
props.putAll(load("src/test/resources/channel.properties"));
Configuration groundConfig = new Configuration(props);
```

Where `load` can be defined as:

```java
public static Properties load(String file) throws Exception {
	Properties result = new Properties();
	try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
		result.load(is);
	}
	return result;
}
```

This will set up `Configuration` class with all required (and reasonably default) properties. Both receiver and transmitter can have the same configuration. In this example, they are different to have different spacecraftId (scid).

The next step is choosing and starting `Transceiver` physical layer. Since both receiver and trasmitter will be running within the same JVM, `NullTransceiver` might be a good choice. 

```java
NullTransceiver groundTransceiver = new NullTransceiver(groundConfig);
groundTransceiver.start();

NullTransceiver satelliteTransceiver = new NullTransceiver(satelliteConfig);
satelliteTransceiver.start();
```

Both responder and caller are different instances of the same class `Node`. Each `Node` should have their own instance of `Transceiver`.

```java
NodeCallback groundCallback = new NodeCallback() {

	@Override
	public void handle(ControllerEvent event) {
		LOG.info("received event: {}", event);
	}
};
UserDataCallback groundDataCallback = new UserDataCallback() {

	@Override
	public void onPacketData(byte[] packet, int portId) {
		System.out.println("Received: \"" + new String(packet) + "\" (" + Util.bytesToHex(packet) + ")");
	}
	@Override
	public void onPacketStream(byte[] data, int portId, int gapFramesCount, int firstHeaderPointer) {
		System.out.println("Received: \"" + new String(data) + "\" (" + Util.bytesToHex(data) + ")");
	}
};
Node ground = new Node(groundConfig, groundTransceiver, groundCallback, groundDataCallback);
ground.start();
```

There are 2 notable callbacks configured:

 * Instance of `com.leosatdata.service.NodeCallback`. This callback being called by Node on any system event. See Annex G "NOTIFICATIONS TO VEHICLE CONTROLLER" for all possible notification types. The main program (or vehicle controller or OBC) can decided what to do. In some scenarios notifications are recoverable, in some not.
 * Instance of `com.leosatdata.service.UserDataCallback`. This is main callback to handle all incoming (RX) data. In the implementation above, it will log all incoming data into standard output.
 
Once both nodes initialized and started, their transceivers can be cross connected. This is only required for `NullTransceiver`.

```java
groundTransceiver.setOtherSide(satelliteTransceiver);
satelliteTransceiver.setOtherSide(groundTransceiver);
```

The next step is duplex configuration. By default Node starts in FULL_DUPLEX mode. But if any other mode is required, then it should be configured (SET DUPLEX directive):

```java
ground.setDuplex(Duplex.FULL_DUPLEX);
satellite.setDuplex(Duplex.FULL_DUPLEX);
```
The next step is to put responder into "connecting-L" mode:

```java
satellite.waitForSession();
```

Responder will initialize transceiver's RX chain with default HAIL parameters. For proximity-1 it will be data rate: R8NC, channel 1, NON_COHERENT_PSK &etc. Once initialized it will wait for HAIL directive on that frequency.

The next step is to configure comm parameters on the caller's side. These is where responder's and caller's transceivers will switch after performing HAIL. Setting comm parameters can be done via `setCommParameters` method (SET RECEIVER PARAMETERS directive):

```java
ground.setCommParameters(createType1CommParameters(dataRate));
```

> Note: Comm parameters should be the same as in configuration (parameter "spdu_version"). For spdu_version=1 it should be `com.leosatdata.model.Type1Directive`, for spdu_version=2 it should be `com.leosatdata.model.Type4Directive`, for spdu_version=3 it should be `com.leosatdata.model.Type5Directive`.

Comm parameters should include configuration for both RX and TX chains. For example:

```java
public static Type1Directive createType1CommParameters(DataRate dataRate) {
	TxRxParameters tx = new TxRxParameters();
	tx.setCoding(Type1Coding.CONVOLUTIONAL);
	tx.setDataRate(dataRate);
	tx.setFrequency(Frequency.CH3);
	tx.setMode(Mode.PROXIMITY1);
	tx.setCoherency(Coherency.COHERENT_PSK);

	TxRxParameters rx = new TxRxParameters();
	rx.setCoding(Type1Coding.CONVOLUTIONAL);
	rx.setDataRate(dataRate);
	rx.setFrequency(Frequency.CH3);
	rx.setMode(Mode.PROXIMITY1);
	rx.setCoherency(Coherency.COHERENT_PSK);

	Type1Directive result = new Type1Directive();
	result.setTxParams(tx);
	result.setRxParams(rx);
	return result;
}
```

Once configured, caller can start HAIL:

```java
ground.startSession();
```

HAIL procedure is asynchronous. It might take some time before caller and responder agree on the frequency and both switch onto it. `PERSISTENT_ACTIVITY` event type received at `NodeCallback` can be used to detect successful HAIL.

Once HAIL completed, it is possible to send data.

```java
ground.send(packet, QualityOfService.EXPEDITED, 0);
```

This operation is blocking. If internal queues are full, then the calling thread will be blocked until queues have some free space. There are 2 independent queues: one for expedited quality of service (qos), another for sequence controlled.

Communication is fully asynchronous. If response data is expected, then it will be delivered to `UserDataCallback`.

Session can be terminated gracefully using the following code:

```java
ground.stopSession();
```

This will trigger LOCAL_NO_MORE_DATA (LNMD) directive and notify remote end to terminate the session. The session will be considered terminated if both ends stop it.

Full shutdown require additional stop of `Transceiver` and `Node`:

```java
ground.stop();
groundTransceiver.stop();
```








