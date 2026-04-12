## Team Information

| Field | Details |
| ----- | ------- |
| Team Name | leosatdata |
| Members | Andrey Rodionov |
| Institution / Aﬀiliation | independent |
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

### Build & Run Instructions

#### Prerequisites

The build requires maven and jdk installed. Here is sample installation in Debian-based distributions:

```
sudo apt install maven openjdk-17-jre-headless
```

#### Build

```
mvn install
```

#### Run Tests

```
mvn test
```

#### Run Examples

TBD

### Gateway Status

| Gateway | Status | Notes |
| ----- | ------- | ----- |
| 0 — Design & Architecture | [ ] Complete / [x] Partial / [ ] Not Attempted | |
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
| Type F1 PLCW encodes/decodes correctly | (/) | SpduParserTest.testPlcw |
| Type F2 PLCW encodes/decodes correctly | | |
| Variable-length SPDUs (Types 1–5) encode/decode | | |
| PLCWs generated from FARM-P state | | |
| Directives decoded and processed | | |
| Big-endian byte order verified | | |
| Malformed SPDUs rejected gracefully Encoding/decoding < 1 ms per SPDU | | |

### Gateway 2: COP-P Layer

| Check | Pass/Fail | Evidence |
| ----- | ------- | ----- |
| FOP-P maintains V(S) correctly | | |
| FOP-P transmit window management works | | |
| FOP-P retransmits on retransmit flag | | |
| FOP-P processes PLCWs correctly | | |
| FARM-P maintains V(R) correctly | | |
| FARM-P detects sequence gaps | | |
| FARM-P sets retransmit flag on gap | | |
| Expedited service bypasses sequencing | | |
| Sequence Controlled service guarantees delivery | | |
| Resynchronization (SET V(R)) works | | |
| 8-bit sequence numbers (modulo-256) | | |
| 16-bit sequence numbers (modulo-65536) | | |
| Persistence mechanism implemented | | |

### Gateway 3: Frame Layer

| Check | Pass/Fail | Evidence |
| ----- | ------- | ----- |
| P-frames transmitted correctly | | |
| U-frames transmitted correctly | | |
| Frames received and parsed correctly | | |
| P-frames vs U-frames distinguished | | |
| SPDUs extracted from P-frames | | |
| QoS flags set correctly | | |
| Version-3 frames supported | | |
| Version-4 frames supported | | |
| CRC-16 validation works | | |
| Malformed frames rejected | | |
| Frame processing < 1 ms per frame | | |

### Gateway 4: Physical Layer Abstraction

| Check | Pass/Fail | Evidence |
| ----- | ------- | ----- |
| PhysicalChannel interface defined | | |
| Mock channel works for unit testing | | |
| TCP channel enables ground testing | | |
| UDP channel enables loss testing | | |
| PCID support works (channels 0 and 1) | | |
| Channel status reporting works | | |

### Gateway 5: State Machine

| Check | Pass/Fail | Evidence |
| ----- | ------- | ----- |
| All states implemented (Init, Hailing, Data, Reconnecting, Termination) | | |
| Full Duplex state tables (Tables 5-1, 5-2) | | |
| Half Duplex state tables (Tables 5-1, 5-3) | | |
| Simplex state tables (Tables 5-1, 5-4) | | |
| State transitions match CCSDS 235.1 tables exactly | | |
| Invalid transitions rejected | | |
| State history logging implemented | | |

### Gateway 6: Hailing & Session Control

| Check | Pass/Fail | Evidence |
| ----- | ------- | ----- |
| Hailing works (caller and responder) | | |
| Caller controller implemented | | |
| Responder controller implemented | | |
| Session lifecycle completes | | |
| Reconnect (rehailing) works | | |
| COMM_CHANGE support works | | |
| Session callbacks implemented | | |
| Thread safety verified | | |

### Gateway 7: Integration Testing

| Check | Pass/Fail | Evidence |
| ----- | ------- | ----- |
| Complete session lifecycle tests pass | | |
| Full Duplex integration tests pass | | |
| Half Duplex integration tests pass | | |
| Simplex integration tests pass | | |
| Reconnect integration tests pass | | |
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
