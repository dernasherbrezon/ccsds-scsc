/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public class ControllerEvent {

	private final ControllerEventType type;
	private final Object obj;
	private final int statusCode;
	private final String failureMessage;

	public ControllerEvent(ControllerEventType type) {
		this(type, null);
	}

	public ControllerEvent(ControllerEventType type, Object obj) {
		this.type = type;
		this.obj = obj;
		this.statusCode = 0;
		this.failureMessage = null;
	}

	public ControllerEvent(ControllerEventType type, int statusCode, String failureMessage) {
		this(type, null, statusCode, failureMessage);
	}

	public ControllerEvent(ControllerEventType type, Object obj, int statusCode, String failureMessage) {
		this.type = type;
		this.statusCode = statusCode;
		this.failureMessage = failureMessage;
		this.obj = obj;
	}

	public Object getObj() {
		return obj;
	}

	public ControllerEventType getType() {
		return type;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public String getFailureMessage() {
		return failureMessage;
	}

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		result.append("ControllerEvent [type=" + type);
		if (obj != null) {
			result.append(", obj=" + obj);
		}
		if (statusCode != 0) {
			result.append(", statusCode=" + statusCode);
		}
		if (failureMessage != null) {
			result.append(", failureMessage=" + failureMessage);
		}
		result.append("]");
		return result.toString();
	}

}
