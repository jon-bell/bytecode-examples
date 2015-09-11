package net.jonbell.examples.jvmti.tagging;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.Arrays;

import net.jonbell.examples.jvmti.walking.runtime.HeapWalker;

import org.junit.Test;

public class WalkingITCase {
	static class Foo {
		Object[] FOOFIELD;
	}

	static Object bar;
	static Object baz;

	@Test
	public void testCleanupBetweenCrawls() throws Exception {
		Object foo = "foo";
		baz = foo;
		for (int i = 0; i < 10; i++)
			HeapWalker.crawl();

		Field[] ret = HeapWalker.getRoots(foo);
		System.out.println(Arrays.toString(ret));

		assertNotNull(ret);
		assertEquals(1, ret.length);
		assertEquals("net.jonbell.examples.jvmti.tagging.WalkingITCase", ret[0].getDeclaringClass().getName());
		assertEquals("baz", ret[0].getName());
		bar = null;
		baz = null;
	}

	@Test
	public void testDirectlyReachable() throws Exception {
		Object foo = "foo";
		baz = foo;
		HeapWalker.crawl();
		Field[] ret = HeapWalker.getRoots(foo);
		System.out.println(Arrays.toString(ret));

		assertNotNull(ret);
		assertEquals(1, ret.length);
		assertEquals("net.jonbell.examples.jvmti.tagging.WalkingITCase", ret[0].getDeclaringClass().getName());
		assertEquals("baz", ret[0].getName());
		bar = null;
		baz = null;
	}

	@Test
	public void testIndiriectlyReachable() throws Exception {
		Object foo = "foo";
		baz = new Foo();
		((Foo) baz).FOOFIELD = new Object[100];
		((Foo) baz).FOOFIELD[0] = foo;
		HeapWalker.crawl();
		Field[] ret = HeapWalker.getRoots(foo);
		System.out.println(Arrays.toString(ret));

		assertNotNull(ret);
		assertEquals(1, ret.length);
		assertEquals("net.jonbell.examples.jvmti.tagging.WalkingITCase", ret[0].getDeclaringClass().getName());
		assertEquals("baz", ret[0].getName());
		bar = null;
		baz = null;
	}

	@Test
	public void testMultipleReachable() throws Exception {
		Object foo = "foo";
		baz = new Foo();
		((Foo) baz).FOOFIELD = new Object[100];
		((Foo) baz).FOOFIELD[0] = foo;
		bar = baz;
		HeapWalker.crawl();
		Field[] ret = HeapWalker.getRoots(foo);
		System.out.println(Arrays.toString(ret));

		assertNotNull(ret);
		assertEquals(2, ret.length);
		assertEquals("net.jonbell.examples.jvmti.tagging.WalkingITCase", ret[0].getDeclaringClass().getName());
		assertTrue(("baz".equals(ret[0].getName()) && "bar".equals(ret[1].getName())) || ("bar".equals(ret[0].getName()) && "baz".equals(ret[1].getName())));
		bar = null;
		baz = null;
	}
}
