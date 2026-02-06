package com.leosatdata.service;

import com.leosatdata.util.Configuration;

public class Node {

	private final Configuration config;
	private final NodeCallback callback;

	public Node(Configuration config, NodeCallback callback) {
		this.config = config;
		this.callback = callback;
	}
}
