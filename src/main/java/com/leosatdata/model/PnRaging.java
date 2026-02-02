package com.leosatdata.model;

public class PnRaging {

	private RangingType rangingType;
	private RangingCode rangingCode;
	private int chipRateK;
	private int chipRateL;
	private int chipRateM;
	private RangingModulationIndex modulationIndex;
	private DaySegmented pnEpochTime;
	private RangingReportRequest reportRequest;

	public RangingType getRangingType() {
		return rangingType;
	}

	public void setRangingType(RangingType rangingType) {
		this.rangingType = rangingType;
	}

	public RangingCode getRangingCode() {
		return rangingCode;
	}

	public void setRangingCode(RangingCode rangingCode) {
		this.rangingCode = rangingCode;
	}

	public int getChipRateK() {
		return chipRateK;
	}

	public void setChipRateK(int chipRateK) {
		this.chipRateK = chipRateK;
	}

	public int getChipRateL() {
		return chipRateL;
	}

	public void setChipRateL(int chipRateL) {
		this.chipRateL = chipRateL;
	}

	public int getChipRateM() {
		return chipRateM;
	}

	public void setChipRateM(int chipRateM) {
		this.chipRateM = chipRateM;
	}

	public RangingModulationIndex getModulationIndex() {
		return modulationIndex;
	}

	public void setModulationIndex(RangingModulationIndex modulationIndex) {
		this.modulationIndex = modulationIndex;
	}

	public DaySegmented getPnEpochTime() {
		return pnEpochTime;
	}

	public void setPnEpochTime(DaySegmented pnEpochTime) {
		this.pnEpochTime = pnEpochTime;
	}

	public RangingReportRequest getReportRequest() {
		return reportRequest;
	}

	public void setReportRequest(RangingReportRequest reportRequest) {
		this.reportRequest = reportRequest;
	}

}
