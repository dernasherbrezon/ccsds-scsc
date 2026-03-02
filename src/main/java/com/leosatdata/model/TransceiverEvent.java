package com.leosatdata.model;

public class TransceiverEvent {

	private final TransceiverEventType type;
	private final Object obj;

	public TransceiverEvent(TransceiverEventType type) {
		this(type, null);
	}

	public TransceiverEvent(TransceiverEventType type, Object obj) {
		this.type = type;
		this.obj = obj;
	}

	public Object getObj() {
		return obj;
	}

	public TransceiverEventType getType() {
		return type;
	}

}
