package com.leosatdata.model;

public class Type5LinkEstablishment {

	private LinkDirection direction;
	private Type5NegotiationType demandQuery;
	private boolean rnmd; // Remote Node has No More Data to Send
	private boolean transmitToken;
	private Duplex duplex;
	private Polarization polarization;
	private Coherency coherency;
	private Type4CarrierModulation carrierModulation;
	private ModulationIndex modulationIndex;
	private Type4Coding coding;
	private int linkSnr;
	private TransceiverMode transceiverMode;
	private int aosFrameLength;
	private float symbolRate;
	private float frequency;

	public Type5LinkEstablishment() {
		// do nothing
	}

	public Type5LinkEstablishment(Type5LinkEstablishment other) {
		if (other == null) {
			return;
		}
		this.direction = other.direction;
		this.demandQuery = other.demandQuery;
		this.rnmd = other.rnmd;
		this.transmitToken = other.transmitToken;
		this.duplex = other.duplex;
		this.polarization = other.polarization;
		this.coherency = other.coherency;
		this.carrierModulation = other.carrierModulation;
		this.modulationIndex = other.modulationIndex;
		this.coding = other.coding;
		this.linkSnr = other.linkSnr;
		this.transceiverMode = other.transceiverMode;
		this.aosFrameLength = other.aosFrameLength;
		this.symbolRate = other.symbolRate;
		this.frequency = other.frequency;
	}

	public LinkDirection getDirection() {
		return direction;
	}

	public void setDirection(LinkDirection direction) {
		this.direction = direction;
	}

	public Type5NegotiationType getDemandQuery() {
		return demandQuery;
	}

	public void setDemandQuery(Type5NegotiationType demandQuery) {
		this.demandQuery = demandQuery;
	}

	public boolean isRnmd() {
		return rnmd;
	}

	public void setRnmd(boolean rnmd) {
		this.rnmd = rnmd;
	}

	public boolean isTransmitToken() {
		return transmitToken;
	}

	public void setTransmitToken(boolean transmitToken) {
		this.transmitToken = transmitToken;
	}

	public Duplex getDuplex() {
		return duplex;
	}

	public void setDuplex(Duplex duplex) {
		this.duplex = duplex;
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

	public Type4CarrierModulation getCarrierModulation() {
		return carrierModulation;
	}

	public void setCarrierModulation(Type4CarrierModulation carrierModulation) {
		this.carrierModulation = carrierModulation;
	}

	public ModulationIndex getModulationIndex() {
		return modulationIndex;
	}

	public void setModulationIndex(ModulationIndex modulationIndex) {
		this.modulationIndex = modulationIndex;
	}

	public Type4Coding getCoding() {
		return coding;
	}

	public void setCoding(Type4Coding coding) {
		this.coding = coding;
	}

	public int getLinkSnr() {
		return linkSnr;
	}

	public void setLinkSnr(int linkSnr) {
		this.linkSnr = linkSnr;
	}

	public TransceiverMode getTransceiverMode() {
		return transceiverMode;
	}

	public void setTransceiverMode(TransceiverMode transceiverMode) {
		this.transceiverMode = transceiverMode;
	}

	public int getAosFrameLength() {
		return aosFrameLength;
	}

	public void setAosFrameLength(int aosFrameLength) {
		this.aosFrameLength = aosFrameLength;
	}

	public float getSymbolRate() {
		return symbolRate;
	}

	public void setSymbolRate(float symbolRate) {
		this.symbolRate = symbolRate;
	}

	public float getFrequency() {
		return frequency;
	}

	public void setFrequency(float frequency) {
		this.frequency = frequency;
	}

}
