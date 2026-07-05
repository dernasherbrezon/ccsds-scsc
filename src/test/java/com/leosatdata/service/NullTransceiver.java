package com.leosatdata.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.leosatdata.model.CarrierModulation;
import com.leosatdata.model.Coding;
import com.leosatdata.model.Coherency;
import com.leosatdata.model.LineCoding;
import com.leosatdata.model.ModulationIndex;
import com.leosatdata.model.Polarization;
import com.leosatdata.model.ScramblerType;
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

	private float rxSymbolRate;
	private float txSymbolRate;
	private float rxFrequency;
	private float txFrequency;

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
			executor.execute(new DelayedTxTask(this));
		} else {
			executor.schedule(new DelayedTxTask(this), txMillisToWait, TimeUnit.MILLISECONDS);
		}
		if (txFrameFilter != null && !txFrameFilter.accept(this, otherSide, data)) {
			return;
		}
		otherSide.executor.schedule(new DelayedRxTask(otherSide, data, otherSide.rxFrameFilter), channelDelay + txMillisToWait, TimeUnit.MILLISECONDS);
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
			if (callback != null) {
				callback.onSymbolUnlock();
			}
			if (callback != null) {
				callback.onCarrierLost();
			}
		}
		this.rxFrequency = frequency;
		if (notifyChange && !mismatchFrequencies && !skipCallbacks && rxEnabled) {
			if (callback != null) {
				callback.onCarrierAcquired();
			}
			if (callback != null) {
				callback.onSymbolLock();
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
			if (otherSide.callback != null && txSymbolEnabled) {
				otherSide.callback.onSymbolUnlock();
			}
			if (otherSide.callback != null && txCarrierEnabled) {
				otherSide.callback.onCarrierLost();
			}
		}
		this.txFrequency = frequency;
		if (notifyChange && !mismatchFrequencies && !skipCallbacks && rxOtherSideEnabled) {
			if (otherSide.callback != null && txCarrierEnabled) {
				otherSide.callback.onCarrierAcquired();
			}
			if (otherSide.callback != null && txSymbolEnabled) {
				otherSide.callback.onSymbolLock();
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
	public void setCallback(TransceiverCallback callback) {
		this.callback = callback;
	}

	@Override
	public TransceiverCallback getCallback() {
		return callback;
	}

	@Override
	public void setTxState(TransceiverTxState state) {
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
		// do nothing
	}

	@Override
	public void setTxCarrierModulation(CarrierModulation carrierModulation) {
		// do nothing
	}

	@Override
	public void setRxModulationIndex(ModulationIndex modulationIndex) {
		// do nothing
	}

	@Override
	public void setTxModulationIndex(ModulationIndex modulationIndex) {
		// do nothing
	}

	@Override
	public void setRxScrambler(ScramblerType scrambler) {
		// do nothing
	}

	@Override
	public void setTxScrambler(ScramblerType scrambler) {
		// do nothing
	}

	@Override
	public void setRxDifferentialEncoding(boolean differentialEncoding) {
		// do nothing
	}

	@Override
	public void setTxDifferentialEncoding(boolean differentialEncoding) {
		// do nothing
	}

	@Override
	public void enableAsmRxCallback(boolean enable) {
		this.asmRxCallback = enable;
	}

	@Override
	public void enableAsmTxCallback(boolean enable) {
		this.asmTxCallback = enable;
	}

	public boolean isAsmRxCallback() {
		return asmRxCallback;
	}

	public boolean isAsmTxCallback() {
		return asmTxCallback;
	}
}
