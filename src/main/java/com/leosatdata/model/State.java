package com.leosatdata.model;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.leosatdata.util.Configuration;

public class State {

	private boolean persistence;
	private final BlockingQueue<Event> queue;
	private final BlockingQueue<Spdu> macQueue;
	private final BlockingQueue<Object> seqQueue;
	private final BlockingQueue<Object> expQueue;
	private final BlockingQueue<Object> sentQueue;

	private Spdu commParameters;
	private MacState state;
	private int ves; // sequence number of the next Expedited frame to be sent
	private int vvs; // sequence number to be assigned to the next Sequence Controlled frame to be
						// sent
	private int vs; // sequence number of the next new Sequence Controlled frame to be sent
	private int nnr; // copy of the Report Value from the previous valid PLCW
	private boolean needPlcw;
	private boolean uFrameLastSent;
	private boolean rrr; // copy of the Retransmit Flag from the previous valid PLCW
	private boolean resync;
	private boolean rs; // indicating whether or not Sequence Controlled frames need to be
						// retransmitted;

	private int expeditedFrameCounter; // a modulo-8 counter indicating that Expedited frames have been received.
	// FIXME Separate Report Values shall be reported for each physical channel
	// independent of the I/O port.
	private int vr; // sequence number plus one (modulo 256) of the last Sequence Controlled frame
					// acknowledged by the receiver

	// wait tx complete before sending next frame
	// tx is async operation
	private boolean txReady;
	private Duplex duplex;
	private TransceiverOperationMode transceiverMode;
	private boolean carrierAcquired;

	// termination step, used in half-duplex
	private int x;
	// comm change step, used in half-duplex
	private int y;

	public State(Configuration config) {
		int capacity = config.getInteger("transmission_window", 10);
		this.seqQueue = new ArrayBlockingQueue<>(capacity);
		this.expQueue = new ArrayBlockingQueue<>(capacity); // use the same parameter for expedited and sequenced
		this.macQueue = new ArrayBlockingQueue<>(capacity);
		this.sentQueue = new ArrayBlockingQueue<>(capacity);
		// if all message queues filled up, have +2 for event queue to process
		this.queue = new ArrayBlockingQueue<>(capacity + capacity + capacity + 2);
		reset();
	}

	public void reset() {
		state = MacState.S1_INACTIVE;
		ves = 0;
		vvs = 0;
		vs = 0;
		nnr = 0;
		needPlcw = false;
		uFrameLastSent = true;
		rrr = false;
		resync = false;
		rs = false;
		expeditedFrameCounter = 0;
		vr = 0;
		txReady = true;
		transceiverMode = TransceiverOperationMode.RESPONDER; // by default passive
		carrierAcquired = false;
		y = 0;
		x = 0;
	}

	public int getVr() {
		return vr;
	}

	public void setVr(int vr) {
		this.vr = vr;
	}

	public boolean isuFrameLastSent() {
		return uFrameLastSent;
	}

	public void setuFrameLastSent(boolean uFrameLastSent) {
		this.uFrameLastSent = uFrameLastSent;
	}

	public boolean isNeedPlcw() {
		return needPlcw;
	}

	public void setNeedPlcw(boolean needPlcw) {
		this.needPlcw = needPlcw;
	}

	public BlockingQueue<Object> getSeqQueue() {
		return seqQueue;
	}

	public BlockingQueue<Object> getExpQueue() {
		return expQueue;
	}

	public int getVvs() {
		return vvs;
	}

	public void setVvs(int vvs) {
		this.vvs = vvs;
	}

	public int getVes() {
		return ves;
	}

	public void setVes(int ves) {
		this.ves = ves;
	}

	public BlockingQueue<Spdu> getMacQueue() {
		return macQueue;
	}

	public Spdu getCommParameters() {
		return commParameters;
	}

	public void setCommParameters(Spdu commParameters) {
		this.commParameters = commParameters;
	}

	public BlockingQueue<Event> getQueue() {
		return queue;
	}

	public boolean isPersistence() {
		return persistence;
	}

	public void setPersistence(boolean persistence) {
		this.persistence = persistence;
	}

	public MacState getState() {
		return state;
	}

	public void setState(MacState state) {
		this.state = state;
	}

	public BlockingQueue<Object> getSentQueue() {
		return sentQueue;
	}

	public int getVs() {
		return vs;
	}

	public void setVs(int vs) {
		this.vs = vs;
	}

	public int getNnr() {
		return nnr;
	}

	public void setNnr(int nnr) {
		this.nnr = nnr;
	}

	public boolean isRrr() {
		return rrr;
	}

	public void setRrr(boolean rrr) {
		this.rrr = rrr;
	}

	public boolean isResync() {
		return resync;
	}

	public void setResync(boolean resync) {
		this.resync = resync;
	}

	public boolean isRs() {
		return rs;
	}

	public void setRs(boolean rs) {
		this.rs = rs;
	}

	public void setExpeditedFrameCounter(int expeditedFrameCounter) {
		this.expeditedFrameCounter = expeditedFrameCounter;
	}

	public int getExpeditedFrameCounter() {
		return expeditedFrameCounter;
	}

	public void setTxReady(boolean txReady) {
		this.txReady = txReady;
	}

	public boolean isTxReady() {
		return txReady;
	}

	public void setDuplex(Duplex duplex) {
		this.duplex = duplex;
	}

	public Duplex getDuplex() {
		return duplex;
	}

	public void setTransceiverMode(TransceiverOperationMode transceiverMode) {
		this.transceiverMode = transceiverMode;
	}

	public TransceiverOperationMode getTransceiverMode() {
		return transceiverMode;
	}

	public boolean noSpduPending() {
		return !needPlcw && macQueue.isEmpty();
	}

	public boolean noFramesPending() {
		return !needPlcw && macQueue.isEmpty() && seqQueue.isEmpty() && expQueue.isEmpty();
	}

	public void setY(int y) {
		this.y = y;
	}

	public int getY() {
		return y;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getX() {
		return x;
	}

	public void setCarrierAcquired(boolean carrierAcquired) {
		this.carrierAcquired = carrierAcquired;
	}

	public boolean isCarrierAcquired() {
		return carrierAcquired;
	}
}
