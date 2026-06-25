package com.leosatdata.model;

public class PersistentActivity {

	private PersistentActivityType notification;
	private int waitingPeriod;
	private int lifetime;
	private int response;
	private boolean success;

	public int getLifetime() {
		return lifetime;
	}

	public void setLifetime(int lifetime) {
		this.lifetime = lifetime;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public PersistentActivityType getNotification() {
		return notification;
	}

	public void setNotification(PersistentActivityType notification) {
		this.notification = notification;
	}

	public int getWaitingPeriod() {
		return waitingPeriod;
	}

	public void setWaitingPeriod(int waitingPeriod) {
		this.waitingPeriod = waitingPeriod;
	}

	public int getResponse() {
		return response;
	}

	public void setResponse(int response) {
		this.response = response;
	}

	@Override
	public String toString() {
		return "PersistentActivity [notification=" + notification + ", waitingPeriod=" + waitingPeriod + ", lifetime=" + lifetime + ", response=" + response + ", success=" + success + "]";
	}

}
