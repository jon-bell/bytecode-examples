package net.jonbell.examples.jvmti.tagging.runtime;

public class Tagger {
	public static int engaged = 0;

	private static native Object _getTag(Object obj);

	private static native void _setTag(Object obj, Object t);

	public static Object getTag(Object obj) {
		if (obj instanceof Tagged)
			return ((Tagged) obj).getMetadataTag();
		if (engaged == 0)
			throw new IllegalStateException("Attempting to use JVMTI features, but native agent not loaded");
		if (obj == null)
			return null;
		return _getTag(obj);
	}

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
