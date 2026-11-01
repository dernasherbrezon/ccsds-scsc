/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public class SetFixedLengthFrame {

	private boolean slicing; // false - no slicing
	private LinkDirection direction;
	private int frameLength;

	public boolean isSlicing() {
		return slicing;
	}

	public void setSlicing(boolean slicing) {
		this.slicing = slicing;
	}

	public LinkDirection getDirection() {
		return direction;
	}

	public void setDirection(LinkDirection direction) {
		this.direction = direction;
	}

	public int getFrameLength() {
		return frameLength;
	}

	public void setFrameLength(int frameLength) {
		this.frameLength = frameLength;
	}

}
