## Open questions

Here is the list of questions and suggestions to the specification with no particular order.

### General

1. No overview of supported features and their sample usage. For example, some distinct features found in the spec:

  * Hail/Session
  * Expediated/Sequence controlled
  * Reconnect/Re-sync
  * Carrier recovery
  
2. No clear session lifecycle. I.e. hail/session esteblished, termination, reconnect, resync.

3. Using 2 boolean variables to define transceiver state (transmit on-off / modulation on-off) is not a good idea. Better have a model and enum. For example: off, carrier, modulation

4. E2.2.5 Directive Function (Query Request/Response, Demand &etc). Not clear why it is needed and should be used. It seems endpoints can negotiate link, but there is no sequence diagram for that or description of the procedure. No use cases provided 

5. E2.2.17 Transceiver Mode. Not clear what this mode is and how should be used. Each of the transceiver parameter is controlled by the data link layer separately via "Link Establishment" directive (i.e. baud rate, frequency &etc). What if mode specified as "Proximity-1", but coding is LDPC24576_16384 (i.e. supported only by the recent Type5 Lunar directive)?

6. Is it possible to change duplex mode durnig COMM_CHANGE or once link established?

7. How to recover from physical layer failures? Or internal failures? Should it be notification to OBC?

8. Not clear the use case of "REPORT SOURCE SPACECRAFT ID" directive. According to section E2.5 this directive can be used to query spacecraft id from remote (i.e source).

 * However section 5.1.3.2 b) 2) (Test_Source) is telling that spacecraft id (SCID) from the first transfer frame can be used.
 * Not clear at what stage of communication transceiver should send that directive. The "query" keyword assume this directive should be sent once session established (data services). In that case the very first transfer frame containing HAIL parameters will by-pass validation, which contradicts to the requirement that validation should discard invalid transfer frames.
 * What if "spacecraft id" defined in that directive is different to scid of the incoming transfer frame (assuming source_destination_id=source at the caller)? This opens up too much room for interpretation making different implementations less compatible 

9. Unclear description of: PN Ranging, Time distribution and Time Tagging service.

  * All comm directives (Type1, Type4 and Type5) support time tagging service, but it is not mentioned anywhere in the specification. At least a reference to proximity-1 is required
  * Not clear when and how to use "PN Ranging" directive. Why extra directive is needed? "The PN RANGING directive is the mechanism by which the PN ranging for a Proximity-1 node is initiated and configured". What is Proximity-1 node in this context? Is it possible to agree on the ranging parameters using Application layer? Should it be in a separate phase in the communication?
  * "TIME DISTRIBUTION" directive briefly mentioned under "E2.6 PN RANGING DIRECTIVE" section. Should it be described under own section?
  * Unclear purpose of "NULL" value of TIME DISTRIBUTION Type.

### MIB properties

1. Not clear how and what to send to OBC in <type>_notification and <type>_response properties. 

2. E81: Reconnect Wait Duration Timeout. There is no such mib property

3. persistence_wait_time. This name is generic, but used only during remote comm_change. 

4. comm_change_waiting_period. Should it be called "comm_change_wait_duration" to be consistent with naming of other persistence activities.

### State machines

1. Half duplex. E40. "Set MODULATION = on" during Acquisition_Idle. This should trigger outgoing frames straight away.

2. Half duplex. E39. Can't get into this state when "NEED_PLCW is true" and "PERSISTENCE = true" and no frames in the mac queue. NEED_PLCW will never be false, because persistence won't allow it. Steps to reproduce: 

  * switch to tx
  * NEED_PLCW=true
  * Send big message. It will take more time than send_timeout
  * send_timeout fired. persistence = true
  * message sent
  * need_plcw still true, but won't be sent
  
3. 6.2.3.2.2. "setting the PCID field in the SET V(R) directive to the value associated with this FOP-P". There is no such field in the V(R) directive

4. Set V(R) directive supports only 8bit sequence numbers. However 32-bit plcw supports 16bit( (2 bytes) sequences.
