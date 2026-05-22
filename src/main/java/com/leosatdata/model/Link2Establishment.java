package com.leosatdata.model;

public class Link2Establishment {

	private LinkDirection direction;
	private NegotiationType2 demandQuery;
	private boolean rnmd; // Remote Node has No More Data to Send
	private boolean transmitToken;
	private Duplex duplex;
	private Polarization polarization;
	private Modulation coherency;
	private LinkModulation modulation;
	private ModulationIndex modulationIndex;
	private Coding coding;
	private int linkSnr;
	private TransceiverMode transceiverMode;
	private int aosFrameLength;
	private float symbolRate; // IEEE 754 standard for half-precision floating point numbers
	private float frequency;

	public Link2Establishment() {
		// do nothing
	}

	public Link2Establishment(Link2Establishment other) {
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
		this.modulation = other.modulation;
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

	public NegotiationType2 getDemandQuery() {
		return demandQuery;
	}

	public void setDemandQuery(NegotiationType2 demandQuery) {
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

	public Modulation getCoherency() {
		return coherency;
	}

	public void setCoherency(Modulation coherency) {
		this.coherency = coherency;
	}

	public LinkModulation getModulation() {
		return modulation;
	}

	public void setModulation(LinkModulation modulation) {
		this.modulation = modulation;
	}

	public ModulationIndex getModulationIndex() {
		return modulationIndex;
	}

	public void setModulationIndex(ModulationIndex modulationIndex) {
		this.modulationIndex = modulationIndex;
	}

	public Coding getCoding() {
		return coding;
	}

	public void setCoding(Coding coding) {
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
