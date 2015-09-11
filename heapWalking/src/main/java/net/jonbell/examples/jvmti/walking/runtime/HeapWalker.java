package net.jonbell.examples.jvmti.walking.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;

import sun.misc.Launcher;

public class HeapWalker {
	/**
	 * Flag set by the JVMTI agent to indicate that it was successfully loaded
	 */
	public static int engaged = 0;

	private static native Field[] _getRoots(Object obj);

	private static native void _crawl();

	/**
	 * Get the static field roots that point to obj.
	 * The data is accurate as of the last time you call the "crawl" function, which captures the pointers.
	 * @param obj
	 * @return
	 */
	public static Field[] getRoots(Object obj) {
		if (engaged == 0)
			throw new IllegalStateException("Attempting to use JVMTI features, but native agent not loaded");
		if (obj == null)
			return null;
		return filter(_getRoots(obj));
	}

	/**
	 * Crawl the heap and generate a reverse points-to graph, which we need to suppor the "getRoots" functionality.
	 */
	public static void crawl() {
		if (engaged == 0)
			throw new IllegalStateException("Attempting to use JVMTI features, but native agent not loaded");
		_crawl();
	}

	/**
	 * Filter some internal static fields out of the returned list
	 * @param in
	 * @return
	 */
	private static Field[] filter(Field[] in)
	{
		ArrayList<Field> ret = new ArrayList<Field>();
		for(Field sf : in)
		{
			if(sf.getDeclaringClass() == ClassLoader.class || sf.getDeclaringClass() == Launcher.class || sf.getDeclaringClass() == Proxy.class)
				continue;
			ret.add(sf);
		}
		return ret.toArray(new Field[ret.size()]);
	}

}
