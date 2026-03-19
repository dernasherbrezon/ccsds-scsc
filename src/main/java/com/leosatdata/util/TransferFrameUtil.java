package com.leosatdata.util;

import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.SourceDestination;

public class TransferFrameUtil {

	public static PduTypeId getPduTypeId(Object obj) {
		if (obj instanceof com.leosatdata.ccsds7320b4.model.TransferFrame) {
			return PduTypeId.USER_DATA;
		}
		if (obj instanceof com.leosatdata.ccsds2110b6.model.TransferFrame) {
			com.leosatdata.ccsds2110b6.model.TransferFrame frame = (com.leosatdata.ccsds2110b6.model.TransferFrame) obj;
			return frame.getPduTypeId();
		}
		if (obj instanceof com.leosatdata.ccsds7321b3.model.TransferFrame) {
			com.leosatdata.ccsds7321b3.model.TransferFrame frame = (com.leosatdata.ccsds7321b3.model.TransferFrame) obj;
			return frame.getProtocolControl();
		}
		return null;
	}

	public static SourceDestination getSourceDestination(Object obj) {
		if (obj instanceof com.leosatdata.ccsds7320b4.model.TransferFrame) {
			return SourceDestination.UNKNOWN;
		}
		if (obj instanceof com.leosatdata.ccsds2110b6.model.TransferFrame) {
			com.leosatdata.ccsds2110b6.model.TransferFrame frame = (com.leosatdata.ccsds2110b6.model.TransferFrame) obj;
			return frame.getSourceOrDestination();
		}
		if (obj instanceof com.leosatdata.ccsds7321b3.model.TransferFrame) {
			com.leosatdata.ccsds7321b3.model.TransferFrame frame = (com.leosatdata.ccsds7321b3.model.TransferFrame) obj;
			return frame.getSourceOrDestination();
		}
		return null;
	}

	public static int getScid(Object obj) {
		if (obj instanceof com.leosatdata.ccsds7320b4.model.TransferFrame) {
			com.leosatdata.ccsds7320b4.model.TransferFrame frame = (com.leosatdata.ccsds7320b4.model.TransferFrame) obj;
			return frame.getScid();
		}
		if (obj instanceof com.leosatdata.ccsds2110b6.model.TransferFrame) {
			com.leosatdata.ccsds2110b6.model.TransferFrame frame = (com.leosatdata.ccsds2110b6.model.TransferFrame) obj;
			return frame.getScid();
		}
		if (obj instanceof com.leosatdata.ccsds7321b3.model.TransferFrame) {
			com.leosatdata.ccsds7321b3.model.TransferFrame frame = (com.leosatdata.ccsds7321b3.model.TransferFrame) obj;
			return frame.getScid();
		}
		return -1;
	}

	public static byte[] getPayload(Object obj) {
		if (obj instanceof com.leosatdata.ccsds7320b4.model.TransferFrame) {
			com.leosatdata.ccsds7320b4.model.TransferFrame frame = (com.leosatdata.ccsds7320b4.model.TransferFrame) obj;
			return frame.getDataField();
		}
		if (obj instanceof com.leosatdata.ccsds2110b6.model.TransferFrame) {
			com.leosatdata.ccsds2110b6.model.TransferFrame frame = (com.leosatdata.ccsds2110b6.model.TransferFrame) obj;
			return frame.getDataField();
		}
		if (obj instanceof com.leosatdata.ccsds7321b3.model.TransferFrame) {
			com.leosatdata.ccsds7321b3.model.TransferFrame frame = (com.leosatdata.ccsds7321b3.model.TransferFrame) obj;
			return frame.getDataField();
		}
		return null;
	}

}
