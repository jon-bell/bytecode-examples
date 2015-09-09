package net.jonbell.examples.jvmti.tagging.runtime;

public interface Tagged {
	public Object getMetadataTag();
	public void setMetadataTag(Object tag);
}