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
groundConfig = new Configuration(props);
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

TBD

### Basic usage

