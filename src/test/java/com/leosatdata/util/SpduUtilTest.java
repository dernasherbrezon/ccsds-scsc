package com.leosatdata.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.leosatdata.model.Spdu;
import com.leosatdata.model.Type4Directive;
import com.leosatdata.model.Type5Directive;
import com.leosatdata.service.NodeTest;

public class SpduUtilTest {

	@Test
	public void testType4CommChange() {
		Type4Directive type4 = NodeTest.createType4CommParameters();
		assertTrue(SpduUtil.isCommChange(type4));
		Spdu directive = SpduUtil.createRnmdDirective(type4, true);
		assertFalse(SpduUtil.isCommChange(directive));
	}
	
	@Test
	public void testType5CommChange() {
		Type5Directive type5 = NodeTest.createType5CommParameters();
		assertTrue(SpduUtil.isCommChange(type5));
		Spdu directive = SpduUtil.createRnmdDirective(type5, true);
		assertFalse(SpduUtil.isCommChange(directive));
	}
}
