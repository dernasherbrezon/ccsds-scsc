/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

import com.leosatdata.model.ControllerEvent;

public interface NodeCallback {

	void handle(ControllerEvent event);
	
}
