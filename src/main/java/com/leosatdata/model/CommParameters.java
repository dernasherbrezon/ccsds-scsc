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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((carrierModulation == null) ? 0 : carrierModulation.hashCode());
		result = prime * result + ((coherency == null) ? 0 : coherency.hashCode());
		result = prime * result + (differentialEncoding ? 1231 : 1237);
		result = prime * result + ((encoding == null) ? 0 : encoding.hashCode());
		result = prime * result + Float.floatToIntBits(frequency);
		result = prime * result + ((lineCoding == null) ? 0 : lineCoding.hashCode());
		result = prime * result + ((modulationIndex == null) ? 0 : modulationIndex.hashCode());
		result = prime * result + ((polarization == null) ? 0 : polarization.hashCode());
		result = prime * result + ((scrambler == null) ? 0 : scrambler.hashCode());
		result = prime * result + Float.floatToIntBits(symbolRate);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CommParameters other = (CommParameters) obj;
		if (carrierModulation != other.carrierModulation)
			return false;
		if (coherency != other.coherency)
			return false;
		if (differentialEncoding != other.differentialEncoding)
			return false;
		if (encoding != other.encoding)
			return false;
		if (Float.floatToIntBits(frequency) != Float.floatToIntBits(other.frequency))
			return false;
		if (lineCoding != other.lineCoding)
			return false;
		if (modulationIndex != other.modulationIndex)
			return false;
		if (polarization != other.polarization)
			return false;
		if (scrambler != other.scrambler)
			return false;
		if (Float.floatToIntBits(symbolRate) != Float.floatToIntBits(other.symbolRate))
			return false;
		return true;
	}

}
