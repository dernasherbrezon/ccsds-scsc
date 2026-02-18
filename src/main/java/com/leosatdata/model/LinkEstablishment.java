package com.leosatdata.model;

public class LinkEstablishment {

	private LinkDirection direction;
	private NegotiationType demandQuery;
	private QueryResponse queryResponse;
	private boolean rnmd; // Remote Node has No More Data to Send
	private boolean transmitToken;
	private Duplex duplex;
	private int freqChannel;
	private Polarization polarization;
	private ModulationIndex modulationIndex;
	private LinkModulation modulation;
	private Coding coding;
	private int linkSnr;
	private float symbolRate; // IEEE 754 standard for half-precision floating point numbers

	public LinkDirection getDirection() {
		return direction;
	}

	public void setDirection(LinkDirection direction) {
		this.direction = direction;
	}

	public NegotiationType getDemandQuery() {
		return demandQuery;
	}

	public void setDemandQuery(NegotiationType demandQuery) {
		this.demandQuery = demandQuery;
	}

	public QueryResponse getQueryResponse() {
		return queryResponse;
	}

	public void setQueryResponse(QueryResponse queryResponse) {
		this.queryResponse = queryResponse;
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

	public int getFreqChannel() {
		return freqChannel;
	}

	public void setFreqChannel(int freqChannel) {
		this.freqChannel = freqChannel;
	}

	public Polarization getPolarization() {
		return polarization;
	}

	public void setPolarization(Polarization polarization) {
		this.polarization = polarization;
	}

	public ModulationIndex getModulationIndex() {
		return modulationIndex;
	}

	public void setModulationIndex(ModulationIndex modulationIndex) {
		this.modulationIndex = modulationIndex;
	}

	public LinkModulation getModulation() {
		return modulation;
	}

	public void setModulation(LinkModulation modulation) {
		this.modulation = modulation;
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

	public float getSymbolRate() {
		return symbolRate;
	}

	public void setSymbolRate(float symbolRate) {
		this.symbolRate = symbolRate;
	}

}
