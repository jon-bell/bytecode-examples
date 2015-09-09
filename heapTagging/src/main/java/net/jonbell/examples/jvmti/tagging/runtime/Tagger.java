package net.jonbell.examples.jvmti.tagging.runtime;

public class Tagger {
	/**
	 * Flag set by the JVMTI agent to indicate that it was successfully loaded
	 */
	public static int engaged = 0;

	private static native Object _getTag(Object obj);

	private static native void _setTag(Object obj, Object t);

	/**
	 * Get the tag currently assigned to an object. If the reference is to an
	 * object, and its class has been instrumented, then we do this entirely
	 * within the JVM. If the reference is to an array, or an instance of a
	 * class that was NOT instrumented, then we use JNI/JVMTI and make a native
	 * call.
	 * 
	 * @param obj
	 * @return
	 */
	public static Object getTag(Object obj) {
		if (obj instanceof Tagged)
			return ((Tagged) obj).getMetadataTag();
		if (engaged == 0)
			throw new IllegalStateException("Attempting to use JVMTI features, but native agent not loaded");
		if (obj == null)
			return null;
		return _getTag(obj);
	}

	/**
	 * Set the tag on an object. If the reference is to an object, and its class
	 * has been instrumented, then we do this entirely within the JVM. If the
	 * reference is to an array, or an instance of a class that was NOT
	 * instrumented, then we use JNI/JVMTI and make a native call.
	 * 
	 * @param obj
	 * @param t
	 */
	public static void setTag(Object obj, Object t) {
		if (obj instanceof Tagged) {
			((Tagged) obj).setMetadataTag(t);
			return;
		}
		if (engaged == 0)
			throw new IllegalStateException("Attempting to use JVMTI features, but native agent not loaded");
		if (obj == null)
			return;
		_setTag(obj, t);
	}

}
