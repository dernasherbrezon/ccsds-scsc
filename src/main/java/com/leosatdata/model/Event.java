package com.leosatdata.model;

public class Event {

	private final EventType type;
	private final Object arg;

	public Event(EventType type) {
		this(type, null);
	}

	public Event(EventType type, Object arg) {
		this.type = type;
		this.arg = arg;
	}

	public EventType getType() {
		return type;
	}

	public Object getArg() {
		return arg;
	}

}
