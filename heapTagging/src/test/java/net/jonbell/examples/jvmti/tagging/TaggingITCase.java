package net.jonbell.examples.jvmti.tagging;

import static org.junit.Assert.*;
import net.jonbell.examples.jvmti.tagging.runtime.Tagged;
import net.jonbell.examples.jvmti.tagging.runtime.Tagger;

import org.junit.Test;

public class TaggingITCase {
	@Test
	public void testObject() throws Exception {
		Object o = new Object();
		Object tag = "foo";
		Tagger.setTag(o, tag);
		assertEquals(tag, Tagger.getTag(o));
	}

	@Test
	public void testInstrumented() throws Exception {
		InnerClass ic = new InnerClass();
		assertTrue(ic instanceof Tagged);
		Object tag = "foobar";
		Tagger.setTag(ic, tag);
		assertEquals(tag, Tagger.getTag(ic));
	}

	static class InnerClass {

	}
}
