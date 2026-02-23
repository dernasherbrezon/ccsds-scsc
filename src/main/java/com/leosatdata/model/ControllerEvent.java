package com.leosatdata.model;

public class ControllerEvent {

	private final ControllerEventType type;

	public ControllerEvent(ControllerEventType type) {
		this.type = type;
	}

	public ControllerEventType getType() {
		return type;
	}

}
