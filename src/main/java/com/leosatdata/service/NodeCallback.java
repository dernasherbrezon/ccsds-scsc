package com.leosatdata.service;

import com.leosatdata.model.ControllerEvent;

public interface NodeCallback {

	void handle(ControllerEvent event);
	
}
