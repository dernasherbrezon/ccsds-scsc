/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public enum ControllerEventType {

	// @formatter:off
	PERSISTENT_ACTIVITY,
	CARRIER_ONLY_RECEIVED,
	SENDER_TX_TIMEOUT, // SENDER EXCEEDED PRESCRIBED TRANSMISSION PERIOD—HALF DUPLEX
	COP_P_LOSS_SYNCHRONIZATION,
	TIMING_SERVICES_INSTANCE,
	END_OF_SESSION,
	CARRIER_LOSS,
	NO_CARRIER_RECEIVED, // half-duplex
	NO_DATA_RECEIVED, // half-duplex
	INVALID_FRAME_SOURCE,
	
	// custom
	TRANSCEIVER_ERROR,		// notification sent when transceiver command returns error
	TIME_DISTRIBUTION,		// TimeDistribution directive received from remote side. The result is com.leosatdata.model.TimeDistribution
	TIME_DELAY_CALCULATION	// results of tx/rx delays calculation. The result is com.leosatdata.model.TimeDelayCalculationResult
	// @formatter:on
	
}
