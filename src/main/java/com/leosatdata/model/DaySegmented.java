/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

// as defined in CCSDS 301.0-B-4
public class DaySegmented {

	private int day;
	private long millisecondsOfTheDay;

	public int getDay() {
		return day;
	}

	public void setDay(int day) {
		this.day = day;
	}

	public long getMillisecondsOfTheDay() {
		return millisecondsOfTheDay;
	}

	public void setMillisecondsOfTheDay(long millisecondsOfTheDay) {
		this.millisecondsOfTheDay = millisecondsOfTheDay;
	}

}
