package com.leosatdata.util;

import com.leosatdata.model.Plcw;
import com.leosatdata.model.TransmitterParameters;

public interface SpduCallback {

	void onPlsw(Plcw message);
	
	void onTransmitterParameters(TransmitterParameters message);
}
