## Team Information

| Field | Details |
| ----- | ------- |
| Team Name | leosatdata |
| Members | Andrey Rodionov |
| Institution / Affiliation | independent |
| Contact Email | dernasherbrezon@gmail.com |
| Repository URL  | https://github.com/dernasherbrezon/ccsds235.1 |

## Implementation Overview

### Language & Technology Stack

| Component | Choice |
| ----- | ------- |
| Primary Language  | Java |
| Build System | Maven |
| Key Libraries | None |
| Test Framework | JUnit |
| Documentation Tool  | JavaDoc |
| Platform(s) Tested  | macOS ARM64 |

### Architecture Summary

![diagram](https://github.com/dernasherbrezon/ccsds235.1/blob/main/docs/diagram.svg)

Key features:

 * Different queues used for passing data between different threads
 * Duplex state machine selected by SET_DUPLEX directive
 * All state machines process every event from the event queue
 * Frame sublayer is used as IO sublayer (not shown on the diagram) for simplicity of implementation
 * Transceiver implementation might implement physical interrupt signals for different events. These events passed to the event queue for processing

#### Threading model

![threads](https://github.com/dernasherbrezon/ccsds235.1/blob/main/docs/threads.svg)

Key features:

 * Single TX thread to execute all state machines, frame selection and tx
 * Transceiver can generate interrupts for different physical signals, they are handled on RX thread
 * Sending data is asynchronous operation: transceiver will generate TX_COMPLETE event when data physically fully modulated and sent over radio
 * All notifications to the vehicle controller executed on the TX thread. 

#### Implementation details

 * pcid not supported. Not clear if transceiver is shared between several OBC or each OBC has own transceiver
 * virtual channel mux/demux is not supported for AOS
 * only packet mode is supported (bit stream (B_PDU) is not)

### Build & Run Instructions

#### Prerequisites

The build requires maven and jdk installed. Here is sample installation in Debian-based distributions:

```
sudo apt install maven openjdk-20-jre-headless
```

Java version 20 is the minimum requirement.

#### Build

```
mvn install
```

#### Run Tests

```
mvn test
```

#### Run Examples

Run server

```
mvn exec:java@server
```

Then run ```listen``` command. This will put node into CONNECTING_L state.

Run client

```
mvn exec:java@client
```

Then run ```connect``` command. This will put node into CONNECTING_T state.

After session established, it is possible to type in the console to send message to the other node.


### Gateway Status

| Gateway | Status | Notes |
| ----- | ------- | ----- |
| 0 — Design & Architecture | [x] Complete / [ ] Partial / [ ] Not Attempted | |
| 1 — SPDU Layer  | [x] Complete / [ ] Partial / [ ] Not Attempted | |
| 2 — COP-P Layer (FOP-P, FARM-P) | [x] Complete / [ ] Partial / [ ] Not Attempted | |
| 3 — Frame Layer  | [x] Complete / [ ] Partial / [ ] Not Attempted | |
| 4 — Physical Layer Abstraction  | [x] Complete / [ ] Partial / [ ] Not Attempted | |
| 5 — State Machine  | [x] Complete / [ ] Partial / [ ] Not Attempted | |
| 6 — Hailing & Session Control  | [x] Complete / [ ] Partial / [ ] Not Attempted | |
| 7 — Integration Testing | [ ] Complete / [x] Partial / [ ] Not Attempted | |
| 8 — Conformance Testing  | [ ] Complete / [ ] Partial / [x] Not Attempted | |
| 9 — Interoperability Testing  | [ ] Complete / [ ] Partial / [x] Not Attempted | |
| 10 — Documentation & Examples  | [ ] Complete / [x] Partial / [ ] Not Attempted | |

## Validation Results

### Gateway 1: SPDU Layer

| Check | Pass/Fail | Evidence |
| ----- | ------- | ----- |
| Type F1 PLCW encodes/decodes correctly | :heavy_check_mark: | SpduParserTest.testPlcw |
| Type F2 PLCW encodes/decodes correctly | :heavy_check_mark: | SpduParserTest.testPlcw |
| Variable-length SPDUs (Types 1–5) encode/decode | :heavy_check_mark: | SpduParserTest.testType5/testType4/testType3/testType2/testType1 |
| PLCWs generated from FARM-P state | :heavy_check_mark: | NodeTest.testStartSendStop |
| Directives decoded and processed | :heavy_check_mark: | NodeTest.testStartSendStop |
| Big-endian byte order verified | :heavy_check_mark: | SpduParserTest.testPlcw |
| Malformed SPDUs rejected gracefully Encoding/decoding < 1 ms per SPDU | :heavy_check_mark: | SpduParserTest.testUnsupportedSpdu |

### Gateway 2: COP-P Layer

| Check | Pass/Fail | Evidence |
| ----- | ------- | ----- |
| FOP-P maintains V(S) correctly | | NodeTest.testStartSendStop |
| FOP-P transmit window management works | | NodeTest.testStartSendStop |
| FOP-P retransmits on retransmit flag | | NodeTest.testStartSendStop |
| FOP-P processes PLCWs correctly | | NodeTest.testStartSendStop |
| FARM-P maintains V(R) correctly | | NodeTest.testStartSendStop |
| FARM-P detects sequence gaps | | NodeTest.testStartSendStop |
| FARM-P sets retransmit flag on gap | | NodeTest.testStartSendStop |
| Expedited service bypasses sequencing | | NodeTest.testPrioritization |
| Sequence Controlled service guarantees delivery | | NodeTest.testStartSendStop |
| Resynchronization (SET V(R)) works | | NodeTest.testResync1/testResync2/testResync3 |
| 8-bit sequence numbers (modulo-256) | | NodeTest.testSequenceOverflow1 |
| 16-bit sequence numbers (modulo-65536) | | NodeTest.testSequenceOverflow2 |
| Persistence mechanism implemented | | NodeTest.testStartSendStop |

### Gateway 3: Frame Layer

| Check | Pass/Fail | Evidence |
| ----- | ------- | ----- |
| P-frames transmitted correctly | | NodeTest.testStartSendStop |
| U-frames transmitted correctly | | NodeTest.testStartSendStop |
| Frames received and parsed correctly | | NodeTest.testStartSendStop |
| P-frames vs U-frames distinguished | | NodeTest.testStartSendStop |
| SPDUs extracted from P-frames | | NodeTest.testStartSendStop |
| QoS flags set correctly | | NodeTest.testStartSendStop |
| Version-3 frames supported | | NodeTest.testStartSendStop |
| Version-4 frames supported | | NodeTest.testStartSendStop |
| CRC-16 validation works | | CsSublayerCrcTest.testInvalidData |
| Malformed frames rejected | | |
| Frame processing < 1 ms per frame | | PerfTest |

### Gateway 4: Physical Layer Abstraction

| Check | Pass/Fail | Evidence |
| ----- | ------- | ----- |
| PhysicalChannel interface defined | | Transceiver |
| Mock channel works for unit testing | | NullTransceiver |
| TCP channel enables ground testing | | TcpTransceiver |
| UDP channel enables loss testing | | |
| PCID support works (channels 0 and 1) | | |
| Channel status reporting works | | |

### Gateway 5: State Machine

| Check | Pass/Fail | Evidence |
| ----- | ------- | ----- |
| All states implemented (Init, Hailing, Data, Reconnecting, Termination) | | FullDuplexMac/HalfDuplexMac |
| Full Duplex state tables (Tables 5-1, 5-2) | | FullDuplexMac |
| Half Duplex state tables (Tables 5-1, 5-3) | | HalfDuplexMac |
| Simplex state tables (Tables 5-1, 5-4) | | SimplexMac |
| State transitions match CCSDS 235.1 tables exactly | | |
| Invalid transitions rejected | | |
| State history logging implemented | | log4j.logger.state |

### Gateway 6: Hailing & Session Control

| Check | Pass/Fail | Evidence |
| ----- | ------- | ----- |
| Hailing works (caller and responder) | | NodeTest.testStartSendStop |
| Caller controller implemented | | NodeTest.testStartSendStop |
| Responder controller implemented | | NodeTest.testStartSendStop |
| Session lifecycle completes | | NodeTest.testStartSendStop |
| Reconnect (rehailing) works | | NodeTest.testReconnectAfterCarrierLoss |
| COMM_CHANGE support works | | NodeTest.testCommChange |
| Session callbacks implemented | | NodeTest.testStartSendStop |
| Thread safety verified | | |

### Gateway 7: Integration Testing

| Check | Pass/Fail | Evidence |
| ----- | ------- | ----- |
| Complete session lifecycle tests pass | | |
| Full Duplex integration tests pass | | NodeTest.parameters |
| Half Duplex integration tests pass | | |
| Simplex integration tests pass | | |
| Reconnect integration tests pass | | NodeTest.testStartSendStop |
| Resynchronization integration tests pass | | |
| Multi-channel tests pass | | |
| Performance benchmarks complete | | |
| Stress tests pass (no leaks) | | |

### Gateway 8: Conformance Testing

| Check | Pass/Fail | Evidence |
| ----- | ------- | ----- |
| All mandatory features implemented | | |
| All conformance tests pass | | |
| PICS document complete | | |
| Test vectors generated (binary + JSON) | | |
| Compliance report shows 100% conformance | | |

### Gateway 9: Interoperability Testing

| Check | Pass/Fail | Evidence |
| ----- | ------- | ----- |
| Session established with other implementation(s) | | |
| Data exchanged successfully | | |
| Test vectors compatible across implementations | | |
| Error recovery works across implementations | | |
| Interoperability report complete | | |

### Gateway 10: Documentation & Examples

| Check | Pass/Fail | Evidence |
| ----- | ------- | ----- |
| API documentation complete | | |
| User guide complete | | |
| Architecture documentation complete | | |
| Caller example works | | |
| Responder example works | | |
| Wire format specification documented | | |
