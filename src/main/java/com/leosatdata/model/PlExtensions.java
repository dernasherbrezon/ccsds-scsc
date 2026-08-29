/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public class PlExtensions {

	private RsCodeType rsCode;
	private boolean differentialEncoding;
	private ScramblerType scrambler;
	private CarrierSuppression carrierSuppression;
	private LineCoding lineCoding; // or "Data Modulation" as defined in the spec
	private Type1CarrierModulation carrierModulation;
	private boolean useExtendedDataRate;
	private boolean useExtendedFreqSet;

	public RsCodeType getRsCode() {
		return rsCode;
	}

	public void setRsCode(RsCodeType rsCode) {
		this.rsCode = rsCode;
	}

	public boolean isDifferentialEncoding() {
		return differentialEncoding;
	}

	public void setDifferentialEncoding(boolean differentialEncoding) {
		this.differentialEncoding = differentialEncoding;
	}

	public ScramblerType getScrambler() {
		return scrambler;
	}

	public void setScrambler(ScramblerType scrambler) {
		this.scrambler = scrambler;
	}

	public CarrierSuppression getCarrierSuppression() {
		return carrierSuppression;
	}

	public void setCarrierSuppression(CarrierSuppression carrierSuppression) {
		this.carrierSuppression = carrierSuppression;
	}

	public LineCoding getLineCoding() {
		return lineCoding;
	}

	public void setLineCoding(LineCoding lineCoding) {
		this.lineCoding = lineCoding;
	}

	public Type1CarrierModulation getCarrierModulation() {
		return carrierModulation;
	}

	public void setCarrierModulation(Type1CarrierModulation carrierModulation) {
		this.carrierModulation = carrierModulation;
	}

	public boolean isUseExtendedDataRate() {
		return useExtendedDataRate;
	}

	public void setUseExtendedDataRate(boolean useExtendedDataRate) {
		this.useExtendedDataRate = useExtendedDataRate;
	}

	public boolean isUseExtendedFreqSet() {
		return useExtendedFreqSet;
	}

	public void setUseExtendedFreqSet(boolean useExtendedFreqSet) {
		this.useExtendedFreqSet = useExtendedFreqSet;
	}

	@Override
	public String toString() {
		return "[rsCode=" + rsCode + ", differentialEncoding=" + differentialEncoding + ", scrambler=" + scrambler + ", carrierSuppression=" + carrierSuppression + ", dataModulation=" + lineCoding + ", carrierModulation=" + carrierModulation + ", useExtendedDataRate=" + useExtendedDataRate
				+ ", useExtendedFreqSet=" + useExtendedFreqSet + "]";
	}

}
