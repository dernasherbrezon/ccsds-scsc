package com.leosatdata.util;

import com.leosatdata.model.ControlParameters;
import com.leosatdata.model.Link2Establishment;
import com.leosatdata.model.LinkEstablishment;
import com.leosatdata.model.Spdu;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type4Directive;
import com.leosatdata.model.Type5Directive;

public class SpduUtil {

	public static Spdu createRnmdDirective(Spdu obj, boolean rnmd) {
		if (obj instanceof Type1Directive) {
			Type1Directive directive = (Type1Directive) obj;
			if (directive.getControlParams() == null) {
				return null;
			}
			ControlParameters params = directive.getControlParams();
			params.setRnmd(rnmd);

			Type1Directive result = new Type1Directive();
			result.setControlParams(params);
			return result;
		}
		if (obj instanceof Type4Directive) {
			Type4Directive directive = (Type4Directive) obj;
			LinkEstablishment rx = null;
			LinkEstablishment tx = null;
			if (directive.getRxLink() != null) {
				rx = directive.getRxLink();
				rx.setRnmd(rnmd);
			}
			if (directive.getTxLink() != null) {
				tx = directive.getTxLink();
				tx.setRnmd(rnmd);
			}
			if (tx == null && rx == null) {
				return null;
			}
			Type4Directive result = new Type4Directive();
			result.setRxLink(rx);
			result.setTxLink(tx);
			return result;
		}
		if (obj instanceof Type5Directive) {
			Type5Directive directive = (Type5Directive) obj;
			Link2Establishment rx = null;
			Link2Establishment tx = null;
			if (directive.getRxLink() != null) {
				rx = directive.getRxLink();
				rx.setRnmd(rnmd);
			}
			if (directive.getTxLink() != null) {
				tx = directive.getTxLink();
				tx.setRnmd(rnmd);
			}
			if (tx == null && rx == null) {
				return null;
			}
			Type5Directive result = new Type5Directive();
			result.setTxLink(tx);
			result.setRxLink(rx);
			return result;
		}
		return null;
	}

	// consider rnmd flag irrespective of direction (rx or tx)
	public static boolean isRnmd(Spdu obj) {
		if (obj instanceof Type1Directive) {
			Type1Directive directive = (Type1Directive) obj;
			if (directive.getControlParams() == null) {
				return false;
			}
			return directive.getControlParams().isRnmd();
		}
		if (obj instanceof Type4Directive) {
			Type4Directive directive = (Type4Directive) obj;
			boolean rxRnmd = directive.getRxLink() != null && directive.getRxLink().isRnmd();
			boolean txRnmd = directive.getTxLink() != null && directive.getTxLink().isRnmd();
			return rxRnmd || txRnmd;
		}
		if (obj instanceof Type5Directive) {
			Type5Directive directive = (Type5Directive) obj;
			boolean rxRnmd = directive.getRxLink() != null && directive.getRxLink().isRnmd();
			boolean txRnmd = directive.getTxLink() != null && directive.getTxLink().isRnmd();
			return rxRnmd || txRnmd;
		}
		return false;
	}
}
