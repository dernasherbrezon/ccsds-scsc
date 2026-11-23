/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.leosatdata.model.Event;
import com.leosatdata.model.EventType;
import com.leosatdata.model.MacState;
import com.leosatdata.model.State;
import com.leosatdata.util.Configuration;

public class HalfDuplexMacTest {

	private State state;
	private HalfDuplexMac mac;
	private boolean waitCancelled;

	@Before
	public void start() {
		Properties props = new Properties();
		props.setProperty("maximum_failed_token_passes", "3");
		Configuration config = new Configuration(props);
		state = new State(config);
		Transceiver transceiver = (Transceiver) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { Transceiver.class }, (proxy, method, args) -> {
			Class<?> type = method.getReturnType();
			if (type.equals(int.class)) {
				return 0;
			}
			if (type.equals(boolean.class)) {
				return true;
			}
			return null;
		});
		TimerService timer = new TimerService(state, config) {
			@Override
			public void scheduleWait(EventType type, long duration) {
			}

			@Override
			public void cancelWait() {
				waitCancelled = true;
			}
		};
		mac = new HalfDuplexMac(state, config, transceiver, event -> {
		}, timer);
	}

	@Test
	public void testTokenPassExceededWhenSendTimeoutCancelled() throws Exception {
		setTokenPassAttempt(4);
		state.setState(MacState.S50_DATA_SERVICES_SEND);
		state.setPersistence(false);
		state.setNeedPlcw(false);

		// nothing to send: TX_COMPLETE cancels E38 and tries to pass the token
		mac.handle(new Event(EventType.TX_COMPLETE));

		assertEquals(true, waitCancelled);
		Event reconnect = state.getQueue().poll(1, TimeUnit.SECONDS);
		assertNotNull(reconnect);
		assertEquals(EventType.E83_TOKEN_PASS_EXCEEDED, reconnect.getType());
		assertEquals(MacState.S50_DATA_SERVICES_SEND, state.getState());
		assertNull(state.getMacQueue().peek());
	}

	@Test
	public void testReconnectOnTokenPassExceeded() throws Exception {
		state.setState(MacState.S50_DATA_SERVICES_SEND);
		mac.handle(new Event(EventType.E83_TOKEN_PASS_EXCEEDED));
		assertEquals(MacState.S80_RECONNECT, state.getState());
	}

	private void setTokenPassAttempt(int value) throws Exception {
		Field f = HalfDuplexMac.class.getDeclaredField("currentTokenPassAttempt");
		f.setAccessible(true);
		f.setInt(mac, value);
	}
}
