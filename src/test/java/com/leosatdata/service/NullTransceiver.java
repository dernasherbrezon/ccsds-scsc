package com.leosatdata.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.leosatdata.model.CarrierModulation;
import com.leosatdata.model.Coding;
import com.leosatdata.model.LineCoding;
import com.leosatdata.model.Coherency;
import com.leosatdata.model.Polarization;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.NamingThreadFactory;

public class NullTransceiver implements Transceiver {

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

	private float rxSymbolRate;
	private float txSymbolRate;
	private float rxFrequency;
	private float txFrequency;

	private Long txDelay;

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
		executor.shutdown();
	}

	@Override
	public void send(byte[] data) {
		assert (txSymbolRate != 0);
		long txMillisToWait;
		if (txDelay == null) {
			txMillisToWait = (long) ((data.length * 8 * 1000) / txSymbolRate);
		} else {
			txMillisToWait = txDelay;
		}
		// tx should be called by interrupt, i.e. async
		if (txMillisToWait == 0) {
			executor.execute(new DelayedTxTask(getTxCallback()));
		} else {
			executor.schedule(new DelayedTxTask(getTxCallback()), txMillisToWait, TimeUnit.MILLISECONDS);
		}
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

	@Override
	public void setRxSymbolRate(float baud) {
		this.rxSymbolRate = baud;
	}

	@Override
	public void setRxLineCoding(LineCoding dataModulation) {
		// do nothing
	}

	@Override
	public void setRxFrequency(float frequency) {
		// assume change frequency when symbol lock/unlock is required
		boolean notifyChange = this.rxFrequency != frequency;
		boolean mismatchFrequencies = otherSide.txFrequency != frequency;
		boolean rxEnabled = rxState.equals(TransceiverRxState.RECEIVING);
		if (notifyChange && mismatchFrequencies && !skipCallbacks && rxEnabled) {
			if (symbolLockCallback != null) {
				symbolLockCallback.onSymbolUnlock();
			}
			if (carrierCallback != null) {
				carrierCallback.onCarrierLost();
			}
		}
		this.rxFrequency = frequency;
		if (notifyChange && !mismatchFrequencies && !skipCallbacks && rxEnabled) {
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
	public void setRxCoding(Coding dataEncoding) {
		// do nothing
	}

	@Override
	public void setRxPolarization(Polarization polarization) {
		// do nothing
	}

	@Override
	public void setRxCoherency(Coherency modulation) {
		// do nothing
	}

	@Override
	public void setTxSymbolRate(float baud) {
		this.txSymbolRate = baud;
	}

	@Override
	public void setTxLineCoding(LineCoding modulation) {
		// do nothing
	}

	@Override
	public void setTxFrequency(float frequency) {
		// assume change frequency when symbol lock/unlock is required
		boolean notifyChange = this.txFrequency != frequency;
		boolean mismatchFrequencies = otherSide.rxFrequency != frequency;
		boolean rxOtherSideEnabled = otherSide.rxState.equals(TransceiverRxState.RECEIVING);
		boolean txCarrierEnabled = txState.equals(TransceiverTxState.CARRIER_ONLY) || txState.equals(TransceiverTxState.MODULATION);
		boolean txSymbolEnabled = txState.equals(TransceiverTxState.MODULATION);
		if (notifyChange && mismatchFrequencies && !skipCallbacks && rxOtherSideEnabled) {
			if (otherSide.symbolLockCallback != null && txSymbolEnabled) {
				otherSide.symbolLockCallback.onSymbolUnlock();
			}
			if (otherSide.carrierCallback != null && txCarrierEnabled) {
				otherSide.carrierCallback.onCarrierLost();
			}
		}
		this.txFrequency = frequency;
		if (notifyChange && !mismatchFrequencies && !skipCallbacks && rxOtherSideEnabled) {
			if (otherSide.carrierCallback != null && txCarrierEnabled) {
				otherSide.carrierCallback.onCarrierAcquired();
			}
			if (otherSide.symbolLockCallback != null && txSymbolEnabled) {
				otherSide.symbolLockCallback.onSymbolLock();
			}
		}
	}

	public float getTxFrequency() {
		return txFrequency;
	}
	
	public float getRxSymbolRate() {
		return rxSymbolRate;
	}
	
	public float getTxSymbolRate() {
		return txSymbolRate;
	}

	@Override
	public void setTxCoding(Coding encoding) {
		// do nothing
	}

	@Override
	public void setTxPolarization(Polarization polarization) {
		// do nothing
	}

	@Override
	public void setTxCoherency(Coherency modulation) {
		// do nothing
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
		if (this.rxState != null && !skipCallbacks && rxFrequency == otherSide.txFrequency) {
			if (this.rxState.equals(TransceiverRxState.RECEIVING)) {
				if (state.equals(TransceiverRxState.OFF)) {
					symbolLockCallback.onSymbolUnlock();
					carrierCallback.onCarrierLost();
				}
			}
			if (this.rxState.equals(TransceiverRxState.OFF)) {
				if (state.equals(TransceiverRxState.RECEIVING)) {
					if (otherSide.txState.equals(TransceiverTxState.CARRIER_ONLY) || otherSide.txState.equals(TransceiverTxState.MODULATION)) {
						carrierCallback.onCarrierAcquired();
					}
					if (otherSide.txState.equals(TransceiverTxState.MODULATION)) {
						symbolLockCallback.onSymbolLock();
					}
				}
			}
		}
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

	public void setTxDelay(Long txDelay) {
		this.txDelay = txDelay;
	}
	
	@Override
	public void setRxCarrierModulation(CarrierModulation carrierModulation) {
		//do nothing
	}
	
	@Override
	public void setTxCarrierModulation(CarrierModulation carrierModulation) {
		//do nothing
	}
}
