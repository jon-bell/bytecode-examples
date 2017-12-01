package net.jonbell.examples.jvmti.nativeWrapping;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;

import org.junit.Test;

public class NativeWrappingITCase {
	@Test
	public void testObject() throws Exception {
		Object o = new Object();
		Object tag = "foo";
		FileInputStream fis = new FileInputStream(new File(""));
	}
}
