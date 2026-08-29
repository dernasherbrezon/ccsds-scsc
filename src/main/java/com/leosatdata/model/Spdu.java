/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public interface Spdu {

	SpduHeader getHeader();

	void setHeader(SpduHeader header);

}
