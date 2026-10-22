/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.leosatdata.model.CommParameters;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.NamingThreadFactory;

public class NullTransceiver implements Transceiver {

	private final Configuration config;
	private final long channelDelay;

	private TransceiverCallback callback;
	private NullTransceiver otherSide;
	private ScheduledExecutorService executor;
	private FrameFilter txFrameFilter;
	private FrameFilter rxFrameFilter;
	private TransceiverTxState txState = TransceiverTxState.OFF;
	private TransceiverRxState rxState = TransceiverRxState.OFF;
	private boolean skipCallbacks = false;

	private CommParameters rx;
	private CommParameters tx;

	private Long txDelay;

	private volatile boolean asmRxCallback;
	private volatile boolean asmTxCallback;

	public NullTransceiver(Configuration config) {
		this.config = config;
		this.txFrameFilter = new DefaultFrameFilter();
		this.channelDelay = config.getDuration("channel_delay_duration");
	}

	public void setOtherSide(NullTransceiver otherSide) {
		this.otherSide = otherSide;
	}

	public void start() {
		executor = Executors.newScheduledThreadPool(1, new NamingThreadFactory("transceiver-" + config.getInteger("local_spacecraft_id", 0)));
	}

	public void stop() {
		if (executor == null) {
			return;
		}
		executor.shutdown();
	}

	@Override
	public int send(byte[] data) {
		assert (tx.getSymbolRate() != 0);
		long txMillisToWait;
		if (txDelay == null) {
			txMillisToWait = (long) ((data.length * 8 * 1000) / tx.getSymbolRate());
		} else {
			txMillisToWait = txDelay;
		}
		// tx should be called by interrupt, i.e. async
		if (txMillisToWait == 0) {
			executor.execute(new DelayedTxTask(this));
		} else {
			executor.schedule(new DelayedTxTask(this), txMillisToWait, TimeUnit.MILLISECONDS);
		}
		if (txFrameFilter != null && !txFrameFilter.accept(this, otherSide, data)) {
			return 0;
		}
		otherSide.executor.schedule(new DelayedRxTask(otherSide, data, otherSide.rxFrameFilter), channelDelay + txMillisToWait, TimeUnit.MILLISECONDS);
		return 0;
	}

	@Override
	public int setRxCommParameters(CommParameters params) {
		if (this.rx == null) {
			this.rx = params;
			return 0;
		}
		// assume change frequency when symbol lock/unlock is required
		boolean notifyChange = this.rx.getFrequency() != params.getFrequency();
		boolean mismatchFrequencies = otherSide.tx == null || otherSide.tx.getFrequency() != params.getFrequency();
		boolean rxEnabled = rxState.equals(TransceiverRxState.RECEIVING);
		if (notifyChange && mismatchFrequencies && !skipCallbacks && rxEnabled) {
			if (callback != null) {
				callback.onSymbolUnlock();
			}
			if (callback != null) {
				callback.onCarrierLost();
			}
		}
		this.rx = params;
		if (notifyChange && !mismatchFrequencies && !skipCallbacks && rxEnabled) {
			if (callback != null) {
				callback.onCarrierAcquired();
			}
			if (callback != null) {
				callback.onSymbolLock();
			}
		}
		return 0;
	}

	@Override
	public int setTxCommParameters(CommParameters params) {
		if (this.tx == null) {
			this.tx = params;
			return 0;
		}
		// assume change frequency when symbol lock/unlock is required
		boolean notifyChange = this.tx.getFrequency() != params.getFrequency();
		boolean mismatchFrequencies = otherSide.rx == null || otherSide.rx.getFrequency() != params.getFrequency();
		boolean rxOtherSideEnabled = otherSide.rxState.equals(TransceiverRxState.RECEIVING);
		boolean txCarrierEnabled = txState.equals(TransceiverTxState.CARRIER_ONLY) || txState.equals(TransceiverTxState.MODULATION);
		boolean txSymbolEnabled = txState.equals(TransceiverTxState.MODULATION);
		if (notifyChange && mismatchFrequencies && !skipCallbacks && rxOtherSideEnabled) {
			if (otherSide.callback != null && txSymbolEnabled) {
				otherSide.callback.onSymbolUnlock();
			}
			if (otherSide.callback != null && txCarrierEnabled) {
				otherSide.callback.onCarrierLost();
			}
		}
		this.tx = params;
		if (notifyChange && !mismatchFrequencies && !skipCallbacks && rxOtherSideEnabled) {
			if (otherSide.callback != null && txCarrierEnabled) {
				otherSide.callback.onCarrierAcquired();
			}
			if (otherSide.callback != null && txSymbolEnabled) {
				otherSide.callback.onSymbolLock();
			}
		}
		return 0;
	}

	public void setTxFrameFilter(FrameFilter frameFilter) {
		this.txFrameFilter = frameFilter;
	}

	@Override
	public void setCallback(TransceiverCallback callback) {
		this.callback = callback;
	}

	@Override
	public TransceiverCallback getCallback() {
		return callback;
	}

	@Override
	public int setTxState(TransceiverTxState state) {
		if (this.txState != null && !skipCallbacks) {
			if (this.txState.equals(TransceiverTxState.MODULATION)) {
				if (state.equals(TransceiverTxState.CARRIER_ONLY) || state.equals(TransceiverTxState.OFF)) {
					otherSide.callback.onSymbolUnlock();
				}
				if (state.equals(TransceiverTxState.OFF)) {
					otherSide.callback.onCarrierLost();
				}
			}
			if (this.txState.equals(TransceiverTxState.CARRIER_ONLY)) {
				if (state.equals(TransceiverTxState.MODULATION)) {
					otherSide.callback.onSymbolLock();
				}
				if (state.equals(TransceiverTxState.OFF)) {
					otherSide.callback.onCarrierLost();
				}
			}
			if (this.txState.equals(TransceiverTxState.OFF)) {
				if (state.equals(TransceiverTxState.MODULATION) || state.equals(TransceiverTxState.CARRIER_ONLY)) {
					otherSide.callback.onCarrierAcquired();
				}
				if (state.equals(TransceiverTxState.MODULATION)) {
					otherSide.callback.onSymbolLock();
				}
			}
		}
		this.txState = state;
		return 0;
	}

	@Override
	public TransceiverTxState getTxState() {
		return txState;
	}

	@Override
	public int setRxState(TransceiverRxState state) {
		if (this.rxState != null && !skipCallbacks && rx != null && otherSide.tx != null && rx.getFrequency() == otherSide.tx.getFrequency()) {
			if (this.rxState.equals(TransceiverRxState.RECEIVING)) {
				if (state.equals(TransceiverRxState.OFF)) {
					callback.onSymbolUnlock();
					callback.onCarrierLost();
				}
			}
			if (this.rxState.equals(TransceiverRxState.OFF)) {
				if (state.equals(TransceiverRxState.RECEIVING)) {
					if (otherSide.txState.equals(TransceiverTxState.CARRIER_ONLY) || otherSide.txState.equals(TransceiverTxState.MODULATION)) {
						callback.onCarrierAcquired();
					}
					if (otherSide.txState.equals(TransceiverTxState.MODULATION)) {
						callback.onSymbolLock();
					}
				}
			}
		}
		this.rxState = state;
		return 0;
	}

	@Override
	public TransceiverRxState getRxState() {
		return rxState;
	}

	public void setSkipCallbacks(boolean skipCallbacks) {
		this.skipCallbacks = skipCallbacks;
	}

	public boolean isSkipCallbacks() {
		return skipCallbacks;
	}

	public void setRxFrameFilter(FrameFilter rxFrameFilter) {
		this.rxFrameFilter = rxFrameFilter;
	}

	public void setTxDelay(Long txDelay) {
		this.txDelay = txDelay;
	}

	@Override
	public int enableAsmRxCallback(boolean enable) {
		this.asmRxCallback = enable;
		return 0;
	}

	@Override
	public int enableAsmTxCallback(boolean enable) {
		this.asmTxCallback = enable;
		return 0;
	}

	public boolean isAsmRxCallback() {
		return asmRxCallback;
	}

	public boolean isAsmTxCallback() {
		return asmTxCallback;
	}

	public CommParameters getTx() {
		return tx;
	}

	public CommParameters getRx() {
		return rx;
	}
}
