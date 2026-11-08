package com.leosatdata.model;

public class TurnaroundRatio {

	private int numerator;
	private int denominator;

	public TurnaroundRatio() {
		// do nothing
	}

	public TurnaroundRatio(TurnaroundRatio other) {
		this.numerator = other.numerator;
		this.denominator = other.denominator;
	}

	public int getNumerator() {
		return numerator;
	}

	public void setNumerator(int numerator) {
		this.numerator = numerator;
	}

	public int getDenominator() {
		return denominator;
	}

	public void setDenominator(int denominator) {
		this.denominator = denominator;
	}

}
