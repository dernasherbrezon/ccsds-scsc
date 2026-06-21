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
