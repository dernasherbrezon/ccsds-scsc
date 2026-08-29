/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.util;

import java.util.concurrent.ThreadFactory;

public class NamingThreadFactory implements ThreadFactory {

	private final String prefix;

	public NamingThreadFactory(String prefix) {
		this.prefix = prefix;
	}

	@Override
	public Thread newThread(Runnable r) {
		return new Thread(r, prefix);
	}
	
	public String getPrefix() {
		return prefix;
	}

}
