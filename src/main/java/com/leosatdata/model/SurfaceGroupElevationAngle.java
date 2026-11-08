/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public class SurfaceGroupElevationAngle {

	private boolean departing; // false for arriving
	private int minimumElevationAngle; // in degrees

	public boolean isDeparting() {
		return departing;
	}

	public void setDeparting(boolean departing) {
		this.departing = departing;
	}

	public int getMinimumElevationAngle() {
		return minimumElevationAngle;
	}

	public void setMinimumElevationAngle(int minimumElevationAngle) {
		this.minimumElevationAngle = minimumElevationAngle;
	}

}
