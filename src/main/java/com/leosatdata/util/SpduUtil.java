package com.leosatdata.util;

import com.leosatdata.model.ControlParameters;
import com.leosatdata.model.Duplex;
import com.leosatdata.model.Spdu;
import com.leosatdata.model.Type1Directive;

public class SpduUtil {

	public static Spdu createRnmdDirective(Spdu obj, boolean rnmd) {
		if (obj instanceof Type1Directive) {
			Type1Directive directive = (Type1Directive) obj;
			ControlParameters params;
			if (directive.getControlParams() != null) {
				params = directive.getControlParams();
			} else {
				params = new ControlParameters();
				// FIXME populate params from state?
				params.setDuplex(Duplex.FULL_DUPLEX);
				params.setTimeSampleField(0);
				params.setTransmitToken(false);
			}
			params.setRnmd(rnmd);

			Type1Directive result = new Type1Directive();
			result.setControlParams(params);
			return result;
		}
		// FIXME other directives
		return null;
	}

	public static boolean isRnmd(Spdu obj) {
		if (obj instanceof Type1Directive) {
			Type1Directive directive = (Type1Directive) obj;
			if (directive.getControlParams() == null) {
				return false;
			}
			return directive.getControlParams().isRnmd();
		}
		// FIXME other directives
		return false;
	}
}
