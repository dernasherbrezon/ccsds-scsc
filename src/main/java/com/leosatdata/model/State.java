package com.leosatdata.model;

import java.util.concurrent.LinkedBlockingQueue;

public class State {

	private boolean persistence;
	private final LinkedBlockingQueue<Event> queue = new LinkedBlockingQueue<>();
	private final LinkedBlockingQueue<Spdu> macQueue = new LinkedBlockingQueue<>(); // FIXME should it be blocking queue?
	private Spdu currentLink;
	private MacMode mode = MacMode.INACTIVE;

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
