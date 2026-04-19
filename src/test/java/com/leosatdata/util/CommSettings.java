package com.leosatdata.util;

public class CommSettings {

	private int uFrameVersion;
	private int pFrameVersion;
	private int spduVersion;
	private int maxFrameSequenceLength;

	public CommSettings() {
		// do nothing
	}

	public CommSettings(int uFrameVersion, int pFrameVersion, int spduVersion, int maxFrameSequenceLength) {
		this.uFrameVersion = uFrameVersion;
		this.pFrameVersion = pFrameVersion;
		this.spduVersion = spduVersion;
		this.maxFrameSequenceLength = maxFrameSequenceLength;
	}

	public int getpFrameVersion() {
		return pFrameVersion;
	}

	public void setpFrameVersion(int pFrameVersion) {
		this.pFrameVersion = pFrameVersion;
	}

	public int getuFrameVersion() {
		return uFrameVersion;
	}

	public void setuFrameVersion(int uFrameVersion) {
		this.uFrameVersion = uFrameVersion;
	}

	public int getSpduVersion() {
		return spduVersion;
	}

	public void setSpduVersion(int spduVersion) {
		this.spduVersion = spduVersion;
	}

	public int getMaxFrameSequenceLength() {
		return maxFrameSequenceLength;
	}

	public void setMaxFrameSequenceLength(int maxFrameSequenceLength) {
		this.maxFrameSequenceLength = maxFrameSequenceLength;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + maxFrameSequenceLength;
		result = prime * result + pFrameVersion;
		result = prime * result + spduVersion;
		result = prime * result + uFrameVersion;
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
		CommSettings other = (CommSettings) obj;
		if (maxFrameSequenceLength != other.maxFrameSequenceLength)
			return false;
		if (pFrameVersion != other.pFrameVersion)
			return false;
		if (spduVersion != other.spduVersion)
			return false;
		if (uFrameVersion != other.uFrameVersion)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "CommSettings [uFrameVersion=" + uFrameVersion + ", pFrameVersion=" + pFrameVersion + ", spduVersion=" + spduVersion + ", maxFrameSequenceLength=" + maxFrameSequenceLength + "]";
	}

}
