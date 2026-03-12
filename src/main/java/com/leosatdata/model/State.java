package com.leosatdata.model;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.leosatdata.util.Configuration;

public class State {

	private boolean persistence;
	private final LinkedBlockingQueue<Event> queue = new LinkedBlockingQueue<>();
	private final LinkedBlockingQueue<Spdu> macQueue = new LinkedBlockingQueue<>(); // FIXME should it be blocking queue?
	private final BlockingQueue<byte[]> seqQueue;
	private final BlockingQueue<byte[]> expQueue;

	private Spdu commParameters;
	private MacMode mode = MacMode.INACTIVE;
	private MacState state = MacState.S1_INACTIVE;
	// FIXME module-8. Most likely won't work for some trasnfer frames
	private int ves = 0; // sequence number of the next Expedited frame to be sent
	private int seqCtrlFsn = 0;
	private boolean needPlcw = false;
	private boolean uFrameLastSent = true;
	
	// FIXME Separate Report Values shall be reported for each physical channel	independent of the I/O port.
	private int vr = 0;

	public State(Configuration config) {
		this.seqQueue = new ArrayBlockingQueue<>(config.getInteger("transmission_window", 10));
		this.expQueue = new ArrayBlockingQueue<>(config.getInteger("transmission_window", 10)); // use the same parameter for expedited and sequenced
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
	
	public BlockingQueue<byte[]> getSeqQueue() {
		return seqQueue;
	}
	
	public BlockingQueue<byte[]> getExpQueue() {
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

	public LinkedBlockingQueue<Spdu> getMacQueue() {
		return macQueue;
	}

	public MacMode getMode() {
		return mode;
	}

	public void setMode(MacMode mode) {
		this.mode = mode;
	}

	public Spdu getCommParameters() {
		return commParameters;
	}
	
	public void setCommParameters(Spdu commParameters) {
		this.commParameters = commParameters;
	}

	public LinkedBlockingQueue<Event> getQueue() {
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
