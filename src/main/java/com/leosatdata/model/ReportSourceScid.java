/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public class ReportSourceScid {

	private StatusType type;
	private Integer sourceSpacecraftId;
	private SurfaceGroupElevationAngle elevationAngle;
	private AlertMessage alert;

	public AlertMessage getAlert() {
		return alert;
	}

	public void setAlert(AlertMessage alert) {
		this.alert = alert;
	}

	public SurfaceGroupElevationAngle getElevationAngle() {
		return elevationAngle;
	}

	public void setElevationAngle(SurfaceGroupElevationAngle elevationAngle) {
		this.elevationAngle = elevationAngle;
	}

	public StatusType getType() {
		return type;
	}

	public void setType(StatusType type) {
		this.type = type;
	}

	public Integer getSourceSpacecraftId() {
		return sourceSpacecraftId;
	}

	public void setSourceSpacecraftId(Integer sourceSpacecraftId) {
		this.sourceSpacecraftId = sourceSpacecraftId;
	}
}
