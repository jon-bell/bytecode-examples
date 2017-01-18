package net.jonbell.examples.bytecode.instrument.demo;

import static org.junit.Assert.*;

import org.junit.Test;

public class ClassCoverageIT {

	@Test
	public void testClassCoverage() throws Exception {
		new FooClass().magic();
	}
}
