package net.jonbell.examples.methodprof;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;

public class ProfileLogger {
	static HashSet<Class<?>> classesHit = new HashSet<Class<?>>();

	/**
	 * Callback called when a classfile is covered the first time.
	 * State is reset upon calling dump().
	 * @param c
	 */
	public static void classHit(Class<?> c) {
		classesHit.add(c);
	}

	static HashSet<String> methodsHit;

	/**
	 * Callback called when a method is covered the first time.
	 * The callback occurs only when dump() is called - it is not called in real time.
	 * State is reset upon calling dump.
	 * @param method
	 */
	public static void methodHit(String method) {
		methodsHit.add(method);
	}

	/**
	 * Return the list of methods covered since the last invocation of dump()
	 * @return
	 */
	public static HashSet<String> dump() {
		HashSet<Class<?>> classes = classesHit;
		classesHit = new HashSet<Class<?>>();
		methodsHit = new HashSet<String>();
		for (Class<?> c : classes) {
			try {
				Method m = c.getDeclaredMethod("__dumpMethodsHit");
				m.setAccessible(true);
				m.invoke(null);
			} catch (IllegalAccessException e) {
			} catch (IllegalArgumentException e) {
			} catch (InvocationTargetException e) {
			} catch (NoSuchMethodException e) {
			} catch (SecurityException e) {
			}
		}
		return methodsHit;
	}
}