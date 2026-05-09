package com.leosatdata.service;

class DelayedRxTask implements Runnable {

	private final byte[] data;
	private final RxCallback callback;
	private final FrameFilter filter;

	public DelayedRxTask(RxCallback callback, byte[] data, FrameFilter filter) {
		this.data = data;
		this.callback = callback;
		this.filter = filter;
	}

	@Override
	public void run() {
		if (filter == null || filter.accept(null, null, data)) {
			callback.onRx(data);
		}
	}

}