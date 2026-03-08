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

public class NullTransceiver implements Transceiver {

	private final static Logger LOG = LoggerFactory.getLogger(NullTransceiver.class);

	private final Configuration config;
	private TxCallback sendCallback;
	private RxCallback receiveCallback;
	private NullTransceiver otherSide;
	private ScheduledExecutorService executor;
	private boolean rx = false;
	private boolean tx = false;

	private float symbolRate;
	private DataModulation dataModulation;
	private float frequency;
	private Coding dataEncoding;
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
		assert (symbolRate != 0);
		try {
			long millisToWait = (long) ((data.length * 8 * 1000) / symbolRate);
			Thread.sleep(millisToWait);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}
		getTxCallback().onTx();
		// FIXME expected channel timeout
		if (otherSide.rx) {
			executor.schedule(new DelayedTask(otherSide.getRxCallback(), data), 10000, TimeUnit.MILLISECONDS);
		}
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
		LOG.info("start rx");
		rx = true;
	}

	@Override
	public void stopRx() {
		LOG.info("stop rx");
		rx = false;
	}

	@Override
	public void startTx() {
		LOG.info("start carrier");
		tx = true;
	}

	@Override
	public void startIdle() {
		LOG.info("start idle");
	}

	@Override
	public void stopIdle() {
		LOG.info("stop idle");
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
	public void setRxSymbolRate(float baud) {
		this.symbolRate = baud;
	}

	@Override
	public void setRxDataModulation(DataModulation dataModulation) {
		this.dataModulation = dataModulation;
	}

	@Override
	public void setRxFrequency(float frequency) {
		this.frequency = frequency;
	}

	@Override
	public void setRxDataEncoding(Coding dataEncoding) {
		this.dataEncoding = dataEncoding;
	}

	@Override
	public void setRxPolarization(Polarization polarization) {
		this.polarization = polarization;
	}

	@Override
	public void setRxModulation(Modulation modulation) {
		this.modulation = modulation;
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
		// TODO Auto-generated method stub

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

}
