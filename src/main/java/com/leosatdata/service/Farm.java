package com.leosatdata.service;

public class Farm {

	private boolean rs = false;
	private boolean needPlcw = true;
	private boolean needStatusReport = true;
	private int vr = 0;
	private int expeditedFrameCounter = 0;

	// state RE0
	public void setInitializeMode() {
		rs = false;
		needPlcw = false;
		needStatusReport = false;
		vr = 0;
		expeditedFrameCounter = 0;
	}

}
