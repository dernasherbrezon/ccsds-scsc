package com.leosatdata.model;

// normalized across Type1, Type4 and Type5 directives
public class CommParameters {

	private float symbolRate;
	private LineCoding lineCoding;
	private float frequency;
	private Coding encoding;
	private Polarization polarization;
	private Coherency coherency;
	private CarrierModulation carrierModulation;
	private ModulationIndex modulationIndex;
	private ScramblerType scrambler;
	private boolean differentialEncoding;

	public float getSymbolRate() {
		return symbolRate;
	}

	public void setSymbolRate(float symbolRate) {
		this.symbolRate = symbolRate;
	}

	public LineCoding getLineCoding() {
		return lineCoding;
	}

	public void setLineCoding(LineCoding lineCoding) {
		this.lineCoding = lineCoding;
	}

	public float getFrequency() {
		return frequency;
	}

	public void setFrequency(float frequency) {
		this.frequency = frequency;
	}

	public Coding getEncoding() {
		return encoding;
	}

	public void setEncoding(Coding encoding) {
		this.encoding = encoding;
	}

	public Polarization getPolarization() {
		return polarization;
	}

	public void setPolarization(Polarization polarization) {
		this.polarization = polarization;
	}

	public Coherency getCoherency() {
		return coherency;
	}
	
	public void setCoherency(Coherency coherency) {
		this.coherency = coherency;
	}

	public CarrierModulation getCarrierModulation() {
		return carrierModulation;
	}

	public void setCarrierModulation(CarrierModulation carrierModulation) {
		this.carrierModulation = carrierModulation;
	}

	public ModulationIndex getModulationIndex() {
		return modulationIndex;
	}

	public void setModulationIndex(ModulationIndex modulationIndex) {
		this.modulationIndex = modulationIndex;
	}

	public ScramblerType getScrambler() {
		return scrambler;
	}

	public void setScrambler(ScramblerType scrambler) {
		this.scrambler = scrambler;
	}

	public boolean isDifferentialEncoding() {
		return differentialEncoding;
	}

	public void setDifferentialEncoding(boolean differentialEncoding) {
		this.differentialEncoding = differentialEncoding;
	}

}
