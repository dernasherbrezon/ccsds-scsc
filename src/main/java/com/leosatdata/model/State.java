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
	private MacState state = MacState.S1_INACTIVE;
	private int ves = 0; // sequence number of the next Expedited frame to be sent
	private int vvs = 0; // sequence number to be assigned to the next Sequence Controlled frame to be
							// sent
	private int vs = 0; // sequence number of the next new Sequence Controlled frame to be sent
	private int nnr = 0; // copy of the Report Value from the previous valid PLCW
	private boolean needPlcw = false;
	private boolean uFrameLastSent = true;
	private boolean rrr = false; // copy of the Retransmit Flag from the previous valid PLCW
	private boolean resync = false;
	private boolean rs = false; // indicating whether or not Sequence Controlled frames need to be
								// retransmitted;

	private int expeditedFrameCounter; // a modulo-8 counter indicating that Expedited frames have been received.
	// FIXME Separate Report Values shall be reported for each physical channel
	// independent of the I/O port.
	private int vr = 0;

	public State(Configuration config) {
		this.seqQueue = new ArrayBlockingQueue<>(config.getInteger("transmission_window", 10));
		this.expQueue = new ArrayBlockingQueue<>(config.getInteger("transmission_window", 10)); // use the same parameter for expedited and sequenced
		this.macQueue = new ArrayBlockingQueue<>(config.getInteger("transmission_window", 10));
		this.sentQueue = new ArrayBlockingQueue<>(config.getInteger("transmission_window", 10));
		// if all message queues filled up, have +1 for event queue to process
		this.queue = new ArrayBlockingQueue<>(seqQueue.size() + expQueue.size() + macQueue.size() + 1);
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
}
