package net.jonbell.examples.bytecode.instrumenting;

import java.util.HashSet;

public class CoverageLogger {

	private static HashSet<Class<?>> covered = new HashSet<Class<?>>();
	public static HashSet<Class<?>> getCoveredClasses()
	{
		return covered;
	}
	public static void resetCoverage()
	{
		for(Class<?> c : covered)
		{
			try {
				c.getField(ClassCoverageCV.CLASS_COVERAGE_FIELD).setBoolean(null, false);
			} catch (IllegalArgumentException e) {
			} catch (IllegalAccessException e) {
			} catch (NoSuchFieldException e) {
			} catch (SecurityException e) {
			}
		}
		covered = new HashSet<Class<?>>();
	}
	public static void classHit(Class<?> c)
	{
		covered.add(c);
	}
}
