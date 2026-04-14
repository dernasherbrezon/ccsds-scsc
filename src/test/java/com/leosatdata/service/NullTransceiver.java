package com.leosatdata.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.Coding;
import com.leosatdata.model.DataModulation;
import com.leosatdata.model.Modulation;
import com.leosatdata.model.Polarization;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.NamingThreadFactory;

public class NullTransceiver implements Transceiver {

	private final static Logger LOG = LoggerFactory.getLogger(NullTransceiver.class);

	private final Configuration config;
	private TxCallback sendCallback;
	private RxCallback receiveCallback;
	private CarrierCallback carrierCallback;
	private SymbolLockCallback symbolLockCallback;
	private NullTransceiver otherSide;
	private ScheduledExecutorService executor;
	private FrameFilter txFrameFilter;
	private FrameFilter rxFrameFilter;
	private TransceiverTxState txState = TransceiverTxState.OFF;
	private TransceiverRxState rxState = TransceiverRxState.OFF;
	private boolean skipCallbacks = false;

	private float symbolRate;
	private float rxFrequency;
	private float txFrequency;

	public NullTransceiver(Configuration config) {
		this.config = config;
		this.txFrameFilter = new DefaultFrameFilter();
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
		// FIXME gracefull shutdown with timeouts and retry
		executor.shutdown();
	}

	@Override
	public void send(byte[] data) {
		assert (symbolRate != 0);
		long txMillisToWait = (long) ((data.length * 8 * 1000) / symbolRate);
		// tx should be called by interrupt, i.e. async
		executor.schedule(new DelayedTxTask(getTxCallback(), data), txMillisToWait, TimeUnit.MILLISECONDS);
		if (txFrameFilter != null && !txFrameFilter.accept(this, otherSide, data)) {
			return;
		}
		otherSide.executor.schedule(new DelayedRxTask(otherSide.getRxCallback(), data, otherSide.rxFrameFilter), txMillisToWait + config.getLong("channel.delayMillis", 0), TimeUnit.MILLISECONDS);
	}

	@Override
	public void setTxCallback(TxCallback callback) {
		this.sendCallback = callback;
	}

	@Override
	public TxCallback getTxCallback() {
		return sendCallback;
	}

	@Override
	public void setRxCallback(RxCallback callback) {
		this.receiveCallback = callback;
	}

	@Override
	public RxCallback getRxCallback() {
		return receiveCallback;
	}

	private static class DelayedRxTask implements Runnable {

		private final byte[] data;
		private final RxCallback callback;
		private final FrameFilter filter;

		public DelayedRxTask(RxCallback callback, byte[] data, FrameFilter filter) {
			this.data = data;
			this.callback = callback;
			this.filter = filter;
		}

		@Override
		public void run() {
			if (filter == null || filter.accept(null, null, data)) {
				callback.onRx(data);
			}
		}

	}

	private static class DelayedTxTask implements Runnable {

		private final TxCallback callback;
		private final byte[] data;

		public DelayedTxTask(TxCallback callback, byte[] data) {
			this.callback = callback;
			this.data = data;
		}

		@Override
		public void run() {
			callback.onTx();
		}

	}

	@Override
	public void setRxSymbolRate(float baud) {
		this.symbolRate = baud;
	}

	@Override
	public void setRxDataModulation(DataModulation dataModulation) {
		// TODO
	}

	@Override
	public void setRxFrequency(float frequency) {
		boolean notifyChange = this.rxFrequency != frequency;
		if (notifyChange && !skipCallbacks) {
			if (symbolLockCallback != null) {
				symbolLockCallback.onSymbolUnlock();
			}
			if (carrierCallback != null) {
				carrierCallback.onCarrierLost();
			}
		}
		this.rxFrequency = frequency;
		if (notifyChange && !skipCallbacks) {
			if (carrierCallback != null) {
				carrierCallback.onCarrierAcquired();
			}
			if (symbolLockCallback != null) {
				symbolLockCallback.onSymbolLock();
			}
		}
	}

	public float getRxFrequency() {
		return rxFrequency;
	}

	@Override
	public void setRxDataEncoding(Coding dataEncoding) {
		// TODO
	}

	@Override
	public void setRxPolarization(Polarization polarization) {
		// TODO
	}

	@Override
	public void setRxModulation(Modulation modulation) {
		// TODO
	}

	@Override
	public void setTxSymbolRate(float baud) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setTxDataModulation(DataModulation modulation) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setTxFrequency(float frequency) {
		// assume change frequency when symbol lock/unlock is required
		boolean notifyChange = this.rxFrequency != frequency;
		if (notifyChange && !skipCallbacks) {
			if (otherSide.symbolLockCallback != null) {
				otherSide.symbolLockCallback.onSymbolUnlock();
			}
			if (otherSide.carrierCallback != null) {
				otherSide.carrierCallback.onCarrierLost();
			}
		}
		this.txFrequency = frequency;
		if (notifyChange && !skipCallbacks) {
			if (otherSide.carrierCallback != null) {
				otherSide.carrierCallback.onCarrierAcquired();
			}
			if (otherSide.symbolLockCallback != null) {
				otherSide.symbolLockCallback.onSymbolLock();
			}
		}
	}

	public float getTxFrequency() {
		return txFrequency;
	}

	@Override
	public void setTxDataEncoding(Coding encoding) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setTxPolarization(Polarization polarization) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setTxModulation(Modulation modulation) {
		// TODO Auto-generated method stub

	}

	public void setTxFrameFilter(FrameFilter frameFilter) {
		this.txFrameFilter = frameFilter;
	}

	@Override
	public void setCarrierCallback(CarrierCallback callback) {
		this.carrierCallback = callback;
	}

	@Override
	public CarrierCallback getCarrierCallback() {
		return carrierCallback;
	}

	@Override
	public void setSymbolLockCallback(SymbolLockCallback callback) {
		this.symbolLockCallback = callback;
	}

	@Override
	public SymbolLockCallback getSymbolLockCallback() {
		return symbolLockCallback;
	}

	@Override
	public void setTxState(TransceiverTxState state) {
		if (this.txState != null && !skipCallbacks) {
			if (this.txState.equals(TransceiverTxState.MODULATION)) {
				if (state.equals(TransceiverTxState.CARRIER_ONLY) || state.equals(TransceiverTxState.OFF)) {
					otherSide.symbolLockCallback.onSymbolUnlock();
				}
				if (state.equals(TransceiverTxState.OFF)) {
					otherSide.carrierCallback.onCarrierLost();
				}
			}
			if (this.txState.equals(TransceiverTxState.CARRIER_ONLY)) {
				if (state.equals(TransceiverTxState.MODULATION)) {
					otherSide.symbolLockCallback.onSymbolLock();
				}
				if (state.equals(TransceiverTxState.OFF)) {
					otherSide.carrierCallback.onCarrierLost();
				}
			}
			if (this.txState.equals(TransceiverTxState.OFF)) {
				if (state.equals(TransceiverTxState.MODULATION) || state.equals(TransceiverTxState.CARRIER_ONLY)) {
					otherSide.carrierCallback.onCarrierAcquired();
				}
				if (state.equals(TransceiverTxState.MODULATION)) {
					otherSide.symbolLockCallback.onSymbolLock();
				}
			}
		}
		this.txState = state;
	}

	@Override
	public TransceiverTxState getTxState() {
		return txState;
	}

	@Override
	public void setRxState(TransceiverRxState state) {
		this.rxState = state;
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
}
