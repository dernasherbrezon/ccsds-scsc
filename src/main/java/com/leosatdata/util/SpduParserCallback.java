package com.leosatdata.util;

import com.leosatdata.model.Plcw;
import com.leosatdata.model.StatusReport;
import com.leosatdata.model.TimeDistribution;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type4Directive;
import com.leosatdata.model.Type5Directive;

public interface SpduParserCallback {

	void onPlsw(Plcw message);

	void onTimeDistribution(TimeDistribution timeDistribution);

	void onType1Directive(Type1Directive decode);

	void onType4Directive(Type4Directive decode);

	void onType5Directive(Type5Directive decode);

	void onStatusReport(StatusReport decode);
}
