package com.leosatdata.model;

public class ControllerEvent {

	private final ControllerEventType type;
	private final Object obj;

	public ControllerEvent(ControllerEventType type) {
		this(type, null);
	}

	public ControllerEvent(ControllerEventType type, Object obj) {
		this.type = type;
		this.obj = obj;
	}

	public Object getObj() {
		return obj;
	}

	public ControllerEventType getType() {
		return type;
	}

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		result.append("ControllerEvent [type=" + type);
		if (obj != null) {
			result.append(", obj=" + obj);
		}
		result.append("]");
		return result.toString();
	}

}
