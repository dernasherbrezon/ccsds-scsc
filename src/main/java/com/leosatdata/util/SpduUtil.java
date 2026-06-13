package com.leosatdata.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.ControlParameters;
import com.leosatdata.model.Duplex;
import com.leosatdata.model.Type5LinkEstablishment;
import com.leosatdata.model.Type4LinkEstablishment;
import com.leosatdata.model.Spdu;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type4Directive;
import com.leosatdata.model.Type5Directive;
import com.leosatdata.model.Vr;

public class SpduUtil {

	private final static Logger LOG = LoggerFactory.getLogger(SpduUtil.class);

	public static boolean isValidDirective(int spduVersion, Spdu obj) {
		if (obj == null) {
			return false;
		}
		if (obj instanceof Type1Directive) {
			return spduVersion == Type1Directive.SPDU_VERSION;
		} else if (obj instanceof Type4Directive) {
			return spduVersion == Type4Directive.SPDU_VERSION;
		} else if (obj instanceof Type5Directive) {
			return spduVersion == Type5Directive.SPDU_VERSION;
		} else {
			return false;
		}
	}

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
			Type4LinkEstablishment rx = null;
			Type4LinkEstablishment tx = null;
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
			Type5LinkEstablishment rx = null;
			Type5LinkEstablishment tx = null;
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
		LOG.error("unknown spdu for createRnmdDirective: {}", obj);
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

	public static boolean isCommChange(Spdu obj) {
		if (obj == null) {
			return false;
		}
		if (obj instanceof Type1Directive) {
			Type1Directive directive = (Type1Directive) obj;
			return directive.getRxParams() != null || directive.getTxParams() != null;
		}
		if (obj instanceof Type4Directive) {
			Type4Directive directive = (Type4Directive) obj;
			if (directive.getRxLink() == null && directive.getTxLink() == null) {
				return false;
			}
			if (directive.getRxLink() != null && (directive.getRxLink().isRnmd() || directive.getRxLink().isTransmitToken())) {
				return false;
			}
			if (directive.getTxLink() != null && (directive.getTxLink().isRnmd() || directive.getTxLink().isTransmitToken())) {
				return false;
			}
			return true;
		}
		if (obj instanceof Type5Directive) {
			Type5Directive directive = (Type5Directive) obj;
			if (directive.getRxLink() == null && directive.getTxLink() == null) {
				return false;
			}
			if (directive.getRxLink() != null && (directive.getRxLink().isRnmd() || directive.getRxLink().isTransmitToken())) {
				return false;
			}
			if (directive.getTxLink() != null && (directive.getTxLink().isRnmd() || directive.getTxLink().isTransmitToken())) {
				return false;
			}
			return true;
		}
		return false;
	}

	public static Vr getVr(Spdu obj) {
		if (obj instanceof Type1Directive) {
			Type1Directive directive = (Type1Directive) obj;
			return directive.getVr();
		}
		if (obj instanceof Type4Directive) {
			Type4Directive directive = (Type4Directive) obj;
			return directive.getVr();
		}
		if (obj instanceof Type5Directive) {
			Type5Directive directive = (Type5Directive) obj;
			return directive.getVr();
		}
		// PLCW won't have vr
		return null;
	}

	public static Spdu mergeCommParameters(Spdu oldParams, Spdu newParams) {
		if (newParams instanceof Type1Directive) {
			ControlParameters controlParameters = new ControlParameters();
			controlParameters.setRnmd(false);
			controlParameters.setTimeSampleField(0);
			controlParameters.setTransmitToken(false);
			Type1Directive obj = (Type1Directive) newParams;
			obj.setControlParams(controlParameters);
			if (oldParams == null) {
				return obj;
			}
			((Type1Directive) oldParams).merge(obj);
			return oldParams;
		}
		if (newParams instanceof Type4Directive) {
			Type4Directive obj = (Type4Directive) newParams;
			if (oldParams == null) {
				return obj;
			}
			((Type4Directive) oldParams).merge(obj);
			return oldParams;
		}

		if (newParams instanceof Type5Directive) {
			Type5Directive obj = (Type5Directive) newParams;
			if (oldParams == null) {
				return obj;
			}
			((Type5Directive) oldParams).merge(obj);
			return oldParams;
		}
		LOG.error("unknown spdu for mergeCommParameters: {}", newParams);
		return null;
	}

	public static void setDuplex(Spdu params, Duplex duplex) {
		if (params instanceof Type1Directive) {
			Type1Directive type1 = (Type1Directive) params;
			if (type1.getControlParams() != null) {
				type1.getControlParams().setDuplex(duplex);
			}
			return;
		}
		if (params instanceof Type4Directive) {
			Type4Directive type4 = (Type4Directive) params;
			if (type4.getRxLink() != null) {
				type4.getRxLink().setDuplex(duplex);
			}
			if (type4.getTxLink() != null) {
				type4.getTxLink().setDuplex(duplex);
			}
			return;
		}
		if (params instanceof Type5Directive) {
			Type5Directive type5 = (Type5Directive) params;
			if (type5.getRxLink() != null) {
				type5.getRxLink().setDuplex(duplex);
			}
			if (type5.getTxLink() != null) {
				type5.getTxLink().setDuplex(duplex);
			}
			return;
		}
		LOG.error("unknown spdu for setDuplex: {}", params);
	}

	public static Duplex getDuplex(Spdu params) {
		if (params instanceof Type1Directive) {
			Type1Directive type1 = (Type1Directive) params;
			if (type1.getControlParams() != null) {
				return type1.getControlParams().getDuplex();
			}
			return null;
		}
		if (params instanceof Type4Directive) {
			Type4Directive type4 = (Type4Directive) params;
			if (type4.getRxLink() != null) {
				return type4.getRxLink().getDuplex();
			}
			if (type4.getTxLink() != null) {
				return type4.getTxLink().getDuplex();
			}
			return null;
		}
		if (params instanceof Type5Directive) {
			Type5Directive type5 = (Type5Directive) params;
			if (type5.getRxLink() != null) {
				return type5.getRxLink().getDuplex();
			}
			if (type5.getTxLink() != null) {
				return type5.getTxLink().getDuplex();
			}
			return null;
		}
		LOG.error("unknown spdu for getDuplex: {}", params);
		return null;
	}

	public static Spdu createTransmit(Spdu params, boolean transmitToken) {
		if (params instanceof Type1Directive) {
			Type1Directive type1 = (Type1Directive) params;
			Type1Directive result = new Type1Directive();
			ControlParameters control = new ControlParameters(type1.getControlParams());
			control.setTransmitToken(transmitToken);
			result.setControlParams(control);
			return result;
		}
		if (params instanceof Type4Directive) {
			Type4Directive type4 = (Type4Directive) params;
			Type4Directive result = new Type4Directive();
			if (type4.getRxLink() != null) {
				Type4LinkEstablishment link = new Type4LinkEstablishment(type4.getRxLink());
				link.setTransmitToken(transmitToken);
				result.setRxLink(link);
			}
			if (type4.getTxLink() != null) {
				Type4LinkEstablishment link = new Type4LinkEstablishment(type4.getTxLink());
				link.setTransmitToken(transmitToken);
				result.setTxLink(link);
			}
			return result;
		}
		if (params instanceof Type5Directive) {
			Type5Directive type5 = (Type5Directive) params;
			Type5Directive result = new Type5Directive();
			if (type5.getRxLink() != null) {
				Type5LinkEstablishment link = new Type5LinkEstablishment(type5.getRxLink());
				link.setTransmitToken(transmitToken);
				result.setRxLink(link);
			}
			if (type5.getTxLink() != null) {
				Type5LinkEstablishment link = new Type5LinkEstablishment(type5.getTxLink());
				link.setTransmitToken(transmitToken);
				result.setTxLink(link);
			}
			return result;
		}
		LOG.error("unknown spdu for createTransmit: {}", params);
		return null;
	}

	public static void setTransmit(Spdu params, boolean token) {
		if (params instanceof Type1Directive) {
			Type1Directive type1 = (Type1Directive) params;
			type1.getControlParams().setTransmitToken(token);
			return;
		}
		if (params instanceof Type4Directive) {
			Type4Directive type4 = (Type4Directive) params;
			if (type4.getRxLink() != null) {
				type4.getRxLink().setTransmitToken(token);
			}
			if (type4.getTxLink() != null) {
				type4.getTxLink().setTransmitToken(token);
			}
			return;
		}
		if (params instanceof Type5Directive) {
			Type5Directive type5 = (Type5Directive) params;
			if (type5.getRxLink() != null) {
				type5.getRxLink().setTransmitToken(token);
			}
			if (type5.getTxLink() != null) {
				type5.getTxLink().setTransmitToken(token);
			}
			return;
		}
		LOG.error("unknown spdu for setTransmit: {}", params);
	}

	public static boolean isTransmit(Spdu params) {
		if (params instanceof Type1Directive) {
			Type1Directive type1 = (Type1Directive) params;
			if (type1.getControlParams() != null) {
				return type1.getControlParams().isTransmitToken();
			}
			return false;
		}
		if (params instanceof Type4Directive) {
			Type4Directive type4 = (Type4Directive) params;
			if (type4.getRxLink() != null) {
				return type4.getRxLink().isTransmitToken();
			}
			if (type4.getTxLink() != null) {
				return type4.getTxLink().isTransmitToken();
			}
			return false;
		}
		if (params instanceof Type5Directive) {
			Type5Directive type5 = (Type5Directive) params;
			if (type5.getRxLink() != null) {
				return type5.getRxLink().isTransmitToken();
			}
			if (type5.getTxLink() != null) {
				return type5.getTxLink().isTransmitToken();
			}
			return false;
		}
		return false;
	}
}
