package com.leosatdata.model;

import java.util.concurrent.LinkedBlockingQueue;

public class State {

	private boolean persistence;
	private final LinkedBlockingQueue<Event> queue = new LinkedBlockingQueue<>();
	private final LinkedBlockingQueue<Spdu> macQueue = new LinkedBlockingQueue<>(); // FIXME should it be blocking queue?
	private Spdu currentLink;
	private MacMode mode = MacMode.INACTIVE;
	private int expFsn = 0; // VE(S)sequence number of the next Expedited frame to be sent
	private int seqCtrlFsn = 0;

	public int getSeqCtrlFsn() {
		return seqCtrlFsn;
	}

	public void setSeqCtrlFsn(int seqCtrlFsn) {
		this.seqCtrlFsn = seqCtrlFsn;
	}

	public int getExpFsn() {
		return expFsn;
	}

	public void setExpFsn(int expFsn) {
		this.expFsn = expFsn;
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

	public Spdu getCurrentLink() {
		return currentLink;
	}

	public void setCurrentLink(Spdu currentLink) {
		this.currentLink = currentLink;
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

}
