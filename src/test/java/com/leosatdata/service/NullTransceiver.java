package com.leosatdata.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.leosatdata.model.DataEncoding;
import com.leosatdata.model.DataModulation;
import com.leosatdata.model.Modulation;
import com.leosatdata.model.Polarization;
import com.leosatdata.util.Configuration;

public class NullTransceiver implements Transceiver {

	private final Configuration config;
	private TxCallback sendCallback;
	private RxCallback receiveCallback;
	private NullTransceiver otherSide;
	private ScheduledExecutorService executor;
	private boolean rx = false;
	private boolean tx = false;
	private boolean modulationEnabled = false;

	private int symbolRate;
	private DataModulation dataModulation;
	private long frequency;
	private DataEncoding dataEncoding;
	private Polarization polarization;
	private Modulation modulation;

	public NullTransceiver(Configuration config) {
		this.config = config;
	}

	public void setOtherSide(NullTransceiver otherSide) {
		this.otherSide = otherSide;
	}

	public void start() {
		executor = Executors.newScheduledThreadPool(1);
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
		assert (tx == true);
		try {
			// FIXME wait expected baud rate timeout
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}
		getTxCallback().onTx();
		// FIXME expected channel timeout
		assert (otherSide.rx == true);
		executor.schedule(new DelayedTask(otherSide.getRxCallback(), data), 10000, TimeUnit.MILLISECONDS);
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

	private static class DelayedTask implements Runnable {

		private final byte[] data;
		private final RxCallback callback;

		public DelayedTask(RxCallback callback, byte[] data) {
			this.data = data;
			this.callback = callback;
		}

		@Override
		public void run() {
			callback.onRx(data);
		}

	}

	@Override
	public void startRx() {
		rx = true;
	}

	@Override
	public void stopRx() {
		rx = false;
	}

	@Override
	public void startCarrier() {
		// do nothing
	}

	@Override
	public void startIdle() {
		// do nothing
	}

//	@Override
//	public void startTx() {
//		try {
//			// radiate carrier frequency only
//			Thread.sleep(config.getLong("carrier_only_duration", 1000));
//			// radiate idle sequence 0101010
//			Thread.sleep(config.getLong("acquisition_idle_duration", 1000));
//		} catch (InterruptedException e) {
//			Thread.currentThread().interrupt();
//			return;
//		}
//		tx = true;
//	}

	@Override
	public void stopTx() {
		tx = false;
	}

	@Override
	public void setModulation(boolean modulation) {
		this.modulationEnabled = modulation;
	}

	@Override
	public boolean getModulation() {
		return modulationEnabled;
	}

	@Override
	public void setSymbolRate(int baud) {
		this.symbolRate = baud;
	}

	@Override
	public void setDataModulation(DataModulation dataModulation) {
		this.dataModulation = dataModulation;
	}

	@Override
	public void setFrequency(long frequency) {
		this.frequency = frequency;
	}

	@Override
	public void setDataEncoding(DataEncoding dataEncoding) {
		this.dataEncoding = dataEncoding;
	}

	@Override
	public void setPolarization(Polarization polarization) {
		this.polarization = polarization;
	}

	@Override
	public void setModulation(Modulation modulation) {
		this.modulation = modulation;
	}

}
