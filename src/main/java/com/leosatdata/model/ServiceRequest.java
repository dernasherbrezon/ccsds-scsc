/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public class ServiceRequest {

	private int missionSpecificEvent;
	private boolean emergency; // if false - then nominal

	public int getMissionSpecificEvent() {
		return missionSpecificEvent;
	}

	public void setMissionSpecificEvent(int missionSpecificEvent) {
		this.missionSpecificEvent = missionSpecificEvent;
	}

	public boolean isEmergency() {
		return emergency;
	}

	public void setEmergency(boolean emergency) {
		this.emergency = emergency;
	}

}
