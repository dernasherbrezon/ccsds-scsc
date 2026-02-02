package com.leosatdata.util;

import com.leosatdata.model.ControlParameters;
import com.leosatdata.model.Link2Establishment;
import com.leosatdata.model.LinkEstablishment;
import com.leosatdata.model.PlExtensions;
import com.leosatdata.model.Plcw;
import com.leosatdata.model.PnRaging;
import com.leosatdata.model.ReportRequest;
import com.leosatdata.model.TimeDistribution;
import com.leosatdata.model.TxRxParameters;
import com.leosatdata.model.Vr;

public interface SpduCallback {

	void onPlsw(Plcw message);

	void onTransmitterParameters(TxRxParameters message);

	void onReceiverParameters(TxRxParameters message);

	void onControlParameters(ControlParameters message);

	void onVr(Vr vr);

	void onReportRequest(ReportRequest reportReq);

	void onPlExtensions(PlExtensions plExtensions);

	void onSourceSpacecraftId(int sourceSpacecraftId);

	void onTimeDistribution(TimeDistribution timeDistribution);

	void onStatusReport(byte[] statusReport);

	void onLinkEstablishment(LinkEstablishment linkEstablishment);

	void onLink2Establishment(Link2Establishment link2);

	void onPnRanging(PnRaging pnRanging);
}
