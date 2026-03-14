package com.leosatdata.model;

public class PlExtensions {

	private RsCodeType rsCode;
	private boolean differentialEncoding;
	private ScramblerType scrambler;
	private ModeSelect modeSelect;
	private DataModulation dataModulation;
	private CarrierModulation carrierModulation;
	private boolean useExtendedDataRate;
	private boolean useExtendedFreqSet;
	private Direction direction;

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

	public ModeSelect getModeSelect() {
		return modeSelect;
	}

	public void setModeSelect(ModeSelect modeSelect) {
		this.modeSelect = modeSelect;
	}

	public DataModulation getDataModulation() {
		return dataModulation;
	}

	public void setDataModulation(DataModulation dataModulation) {
		this.dataModulation = dataModulation;
	}

	public CarrierModulation getCarrierModulation() {
		return carrierModulation;
	}

	public void setCarrierModulation(CarrierModulation carrierModulation) {
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

	public Direction getDirection() {
		return direction;
	}

	public void setDirection(Direction direction) {
		this.direction = direction;
	}

	@Override
	public String toString() {
		return "[rsCode=" + rsCode + ", differentialEncoding=" + differentialEncoding + ", scrambler=" + scrambler + ", modeSelect=" + modeSelect + ", dataModulation=" + dataModulation + ", carrierModulation=" + carrierModulation + ", useExtendedDataRate=" + useExtendedDataRate
				+ ", useExtendedFreqSet=" + useExtendedFreqSet + ", direction=" + direction + "]";
	}

}
