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

	private Spdu commParameters;
	private MacState state = MacState.S1_INACTIVE;
	private int ves = 0; // sequence number of the next Expedited frame to be sent
	private int seqCtrlFsn = 0;
	private boolean needPlcw = false;
	private boolean uFrameLastSent = true;

	// FIXME Separate Report Values shall be reported for each physical channel
	// independent of the I/O port.
	private int vr = 0;

	public State(Configuration config) {
		this.seqQueue = new ArrayBlockingQueue<>(config.getInteger("transmission_window", 10));
		this.expQueue = new ArrayBlockingQueue<>(config.getInteger("transmission_window", 10)); // use the same parameter for expedited and sequenced
		this.macQueue = new ArrayBlockingQueue<>(config.getInteger("transmission_window", 10));
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

	public int getSeqCtrlFsn() {
		return seqCtrlFsn;
	}

	public void setSeqCtrlFsn(int seqCtrlFsn) {
		this.seqCtrlFsn = seqCtrlFsn;
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
}
