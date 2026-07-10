## User guide

This document describes several typical use cases. All code examples are explained line by line. For topics not covered in this document, refer to the unit tests located in the `./src/test/java` directory.

### Key concepts

#### Configuration

Configuration is performed through `.properties` files. Properties loaded from multiple locations are combined, with later values overriding earlier ones to produce the final configuration. The class `com.leosatdata.util.Configuration` wraps these properties and performs basic validation.

```java
Properties props = new Properties();
props.putAll(load("src/main/resources/mib.properties"));
props.putAll(load("src/main/resources/transceiver.properties"));
props.putAll(load("src/main/resources/custom.properties"));
props.putAll(load("src/test/resources/ground.properties"));
props.putAll(load("src/test/resources/channel.properties"));
Configuration groundConfig = new Configuration(props);
```

This implementation uses several `.properties` files:

 * **mib.properties** - Contains Management Information Base (MIB) properties as defined in CCSDS 235.1.
 * **transceiver.properties** - Defines pre-configured frequency channels for each mode. Type 1 and Type 4 directives use frequency channel numbers rather than explicit frequency values. This file provides the mapping from channel number to actual frequency.
 * **custom.properties** - Contains implementation-specific properties. All values include reasonable defaults and comments.

#### Transceiver

The interface `com.leosatdata.service.Transceiver` provides access to the physical layer. Several implementations are available:

 * `com.leosatdata.service.NullTransceiver` - Used for testing when sender and receiver execute within the same JVM. Features include:
   * Asynchronous RX and TX_COMPELETE
   * Simulation of radio channel latencies
   * Simulation of TX modulation latencies
   * Simulation of message loss and smart filtering
   * Simulation of RX and TX lifecycles (e.g., the receiver cannot perform RX while the transmitter is generating a carrier only)
   * Dropping of messages and error reporting when RX and TX use different frequencies or symbol rates
 * `com.leosatdata.service.HalfDuplexTransceiver` - Extends the above with simulation of half-duplex behavior (RX is disabled during TX and vice versa).
 * `com.leosatdata.service.TcpTransceiver` - Used in TCP-based examples. Capabilities include:
   * Transfer of PDUs over TCP frames (TCP may fragment PDUs)
   * PDU synchronization and reassembly via a custom framing layer.
   * Simulation of RX and TX links over separate sockets
   * Simulation of radio channel latencies
   * Simulation of TX modulation latencies

This interface is designed for close alignment with hardware implementations. For example, `set`/`get` methods can map to SPI registers, and callbacks can map to hardware interrupts.

#### Node

The class `com.leosatdata.service.Node` serves as the primary entry point to the library. It manages initialization, operation, and shutdown of communications.

### Basic usage

A typical use case consists of the following steps:

 * The responder is started and enters the listening state (connecting-L).
 * The caller is started and initiates a session with the responder (connecting-T).
 * A sample packet is sent.
 * The session is terminated gracefully.
 
#### Configuration Loading

```java
Properties props = new Properties();
props.putAll(load("src/main/resources/mib.properties"));
props.putAll(load("src/main/resources/transceiver.properties"));
props.putAll(load("src/main/resources/custom.properties"));
props.putAll(load("src/test/resources/ground.properties"));
props.putAll(load("src/test/resources/channel.properties"));
Configuration groundConfig = new Configuration(props);
```

The helper method may be defined as follows:

```java
public static Properties load(String file) throws Exception {
	Properties result = new Properties();
	try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
		result.load(is);
	}
	return result;
}
```

This process initializes the `Configuration` with all required properties (including reasonable defaults). The same configuration may be used for both receiver and transmitter sides. Separate configurations are used in the example to assign different spacecraft IDs (SCIDs).

#### Transceiver Initialization

```java
NullTransceiver groundTransceiver = new NullTransceiver(groundConfig);
groundTransceiver.start();

NullTransceiver satelliteTransceiver = new NullTransceiver(satelliteConfig);
satelliteTransceiver.start();
```

Each `Node` requires its own `Transceiver` instance.

#### Node Creation and Callbacks

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

Two callbacks are configured:

 * `com.leosatdata.service.NodeCallback` — Invoked by the `Node` for system events. Refer to Annex G of CCSDS 235.1 ("Notifications to Vehicle Controller") for notification types. The vehicle controller or onboard computer determines the appropriate response. Some notifications are recoverable; others are not.
 * `com.leosatdata.service.UserDataCallback` — Handles all incoming (RX) user data. In the example above, received data is logged to standard output.

#### Cross-Connection of Transceivers (NullTransceiver only)

```java
groundTransceiver.setOtherSide(satelliteTransceiver);
satelliteTransceiver.setOtherSide(groundTransceiver);
```

#### Duplex Mode Configuration

By default, the Node starts in full-duplex mode. To select another mode, apply the SET DUPLEX directive:

```java
ground.setDuplex(Duplex.FULL_DUPLEX);
satellite.setDuplex(Duplex.FULL_DUPLEX);
```
#### Responder (Listening) Initialization

```java
satellite.waitForSession();
```

The responder initializes the transceiver RX chain with default HAIL parameters (for Proximity-1: data rate R8NC, channel 1, NON_COHERENT_PSK, etc.) and waits for a HAIL directive on that frequency.

#### Caller Communication Parameters

Communication parameters for the session (used after successful HAIL) are configured via the `setCommParameters` method (SET RECEIVER PARAMETERS / SET TRANSMITTER PARAMETERS directives):

```java
ground.setCommParameters(createType1CommParameters(dataRate));
```

> Note: Communication parameters must be consistent with the `spdu_version` property in the configuration. Use `com.leosatdata.model.Type1Directive` for `spdu_version=1` (Proximity-1), `Type4Directive` for `spdu_version=4` (first-generation lunar), and `Type5Directive` for `spdu_version=5` (second-generation lunar).

Example factory method:

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

#### Session Initiation (HAIL)

```java
ground.startSession();
```

The HAIL procedure is asynchronous. Successful completion is indicated by a `PERSISTENT_ACTIVITY` event with success status in the `NodeCallback`.

```java
@Override
public void handle(ControllerEvent event) {
	if (event.getType().equals(ControllerEventType.PERSISTENT_ACTIVITY) && event.getObj() != null) {
		PersistentActivity activity = (PersistentActivity) event.getObj();
		if (activity.getNotification().equals(PersistentActivityType.HAIL) && activity.isSuccess()) {
			// hail completed successfully
		}
	}
}
```

#### Data Transmission

```java
ground.send(packet, QualityOfService.EXPEDITED, 0);
```

The `send` operation is blocking. If internal queues are full, the calling thread blocks until space becomes available. Separate queues are maintained for EXPEDITED and SEQUENCE CONTROLLED Quality of Service.

Incoming data (if any) is delivered asynchronously through the `UserDataCallback`.

#### Graceful Session Termination

```java
ground.stopSession();
```

This action issues a LOCAL_NO_MORE_DATA (LNMD) directive and notifies the remote end. The session is considered terminated only when both ends have stopped it.

#### Shutdown

```java
ground.stop();
groundTransceiver.stop();
```

### Communication Parameter Change

Communication parameters may be modified after session establishment or during active data exchange by calling `setCommParameters` with updated values. This triggers a coordinated local and remote parameter change.

```java
ground.setCommParameters(newParameters);
```

During the change, outgoing packets are queued. The `send` method blocks if queues are full until the change completes.

### Protocol interopability

CCSDS 235.1 supports interoperability between different spacecraft and ground systems. It defines multiple transfer frame types and SPDU directive formats. This implementation supports all defined types.

Supported Transfer Frames:

 * AOS v2 (CCSDS 732.0)
 * Proximity-1 v3 (CCSDS 211.1)
 * USLP v4 (CCSDS 732.1)
 
Supported SPDU Directives (Communication Parameters):

 * Type1Directive - proximity-1
 * Type4Directive - first generation lunar
 * Type5Directive - second generation lunar
 
Message types are configured in the `.properties` file. Example for USLP v4 user frames and Type 5 SPDUs:

```properties
u_frame_version=4
spdu_version=5
frame_version=4
```

Per CCSDS 235.1, transfer frames are categorized as user frames (U-frames) and protocol frames (P-frames). AOS frames cannot be used as P-frames. Therefore, separate properties are provided: `u_frame_version` for user frames and `frame_version` for protocol frames.

### Frame Filtering

The implementation supports filtering based on spacecraft identifiers.

 * **Destination filtering**. Incoming frames must be addressed to the local `Node`.
 
```properties
local_spacecraft_id=1
source_destination_id=1
```

 * **Source filtering**: Accept frames only from a specific spacecraft/caller.
 
```properties
test_source=true
source_destination_id=0
remote_spacecraft_id=1
```

 * **Dynamic source**: Accept frames from the first connected caller.
 
```properties
test_source=true
source_destination_id=0
```

 * **No filtering**: Disable source testing locally.
 
```properties
test_source=false
```

### Timers

CCSDS 235.1 state machines make active use of various timers. These timers trigger different events. For example, `hail_wait_duration` is used when the caller does not receive a PLCW from the responder within the allotted time. Incorrect timer configuration can prevent some procedures from completing.

The equations below do not account for code latency or operating system scheduler delays.

#### Session Initiation (HAIL)

For HAIL to succeed, the following condition must be satisfied:

```
tail_idle_duration + hail_wait_duration > 2 * channel_latency + carrier_only_duration + acquisition_idle_duration
```

The left-hand side represents the time the caller waits for a PLCW before making another attempt. The right-hand side represents the time required for the responder to receive the communication parameters and switch the transceiver to the next channel. The parameter `channel_latency` is the latency between caller and responder.

#### Communication Parameter Change

A communication parameter change requires a round-trip of the message plus an additional `tail_idle_duration` at the responder.

```
comm_change_waiting_period > 2 * channel_latency + tail_idle_duration + carrier_only_duration + acquisition_idle_duration
```

### Time services

Although time services are not defined in the CCSDS 235.1 specification, the data structures in this implementation support them. The time service behavior follows the definition provided in the Proximity-1 Space Link Protocol specification.

The supported latency model is shown below:

![latencyModel](https://github.com/dernasherbrezon/ccsds235.1/blob/main/docs/latencyModel.svg)

This implementation fully supports the latency model. To enable time services, the `Transceiver` must support two additional interrupts:

This implementation fully support latency model. `Transceiver` implementation should support 2 extra interrupts to make time services work:

 * Interrupt when the ASM (Attached Synchronization Marker) has been transmitted
 * Interrupt when the ASM has been received

These interrupts do not need to be triggered exactly at the moment of ASM transmission or reception. The key requirement is that they fire independently of the transfer frame size.

> Note: These callbacks can introduce performance overhead and are not required for normal data transfer operations. They can be dynamically enabled or disabled if the following methods are implemented in your Transceiver:

```java
void enableAsmRxCallback(boolean enable);
void enableAsmTxCallback(boolean enable);
```

The following sections describe the typical workflow for distributing time to a remote spacecraft.

#### Time tagging

The time tagging process is used to measure the **One-Way Light Time (OWLT)** between two spacecraft. Each transfer frame is timestamped on both the transmitting and receiving sides. Because OWLT measurements are sensitive to radio interference, multipath propagation, and other channel effects, it is recommended to collect timestamps from a sufficient number of frames before performing correlation.

A session must be established before starting time tagging.

Starting time tagging:

```java
ground.startTimeTagging(3);
```

This command instructs the local node to tag 3 transmitted and 3 received messages. It also notifies the remote node to do the same. Once enough RX and TX messages tagged, the local `NodeCallback` receives a `TIMING_SERVICES_INSTANCE` event.

```java
@Override
public void handle(ControllerEvent event) {
	if (event.getType().equals(ControllerEventType.TIMING_SERVICES_INSTANCE) && event.getObj() != null) {
		List<TimeTag> tags = (List<TimeTag>) event.getObj();
		// save or process
	}
}
```

The TimeTag object contains:

 * UNIX timestamp (milliseconds)
 * Frame sequence number
 * Quality of Service (Expedited or Sequence Controlled)
 * Direction (RX or TX)

A basic correlation can be performed by matching local TX tags with corresponding remote RX tags (using the frame sequence number) and calculating the average time difference.

#### Time delay calculation 

Transfer frames may experience variable queuing delays, making them unsuitable for precise internal delay measurement. This implementation uses a PLCW (Protocol Command Word) to measure internal transmit-side delay, ensuring the measurement follows the same code path as the Time Distribution directive.

It is recommended to repeat the measurement several times for better statistical accuracy.

> Note: The transceiver may be busy transmitting a previous frame, which can add extra delay to the outgoing PLCW even though it has higher priority. The same applies to the Time Distribution directive.

Starting time delay calculation:

```java
long initialTime = System.currentTimeMillis();
ground.calculateTimeDelay(initialTime);
```

When the calculation completes, the `NodeCallback` receives a `TIME_DELAY_CALCULATION` event:

```java
@Override
public void handle(ControllerEvent event) {
	if (event.getType().equals(ControllerEventType.TIME_DELAY_CALCULATION) && event.getObj() != null) {
		TimeDelayCalculationResult result = (TimeDelayCalculationResult) event.getObj();
		// save or process
	}
}
```

#### Time distribution directive

Once the OWLT and internal delays are known, time can be distributed to the remote spacecraft.

> Important: The receiving side should complete its own time delay calculation before accepting a Time Distribution directive. It is assumed that such coordination is handled by higher-level protocols.

Distributing time:

```java
TimeDistribution directive = new TimeDistribution();
directive.setOneWayLightTime(300);
directive.setSendSideDelay(30);
directive.setTransceiverClock(System.currentTimeMillis());

ground.distributeTime(directive);
```

If the remote side initiates time distribution, the receiver will get a `TIME_DISTRIBUTION` notification:

```java
@Override
public void handle(ControllerEvent event) {
	if (event.getType().equals(ControllerEventType.TIME_DISTRIBUTION) && event.getObj() != null) {
		TimeDistribution result = (TimeDistribution) event.getObj();
		// save or process
	}
}
```