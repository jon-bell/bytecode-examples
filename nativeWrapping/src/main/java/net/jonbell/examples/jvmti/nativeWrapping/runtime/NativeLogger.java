package net.jonbell.examples.jvmti.nativeWrapping.runtime;

import java.util.HashSet;

public class NativeLogger {

	public static HashSet<String> nativeCalls = new HashSet<String>();
	public static void recordNative(String meth)
	{
		nativeCalls.add(meth);
	}
	static
	{
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			
			@Override
			public void run() {
				System.out.println("Native methods invoked:");
				HashSet<String> toPrint = nativeCalls;
				nativeCalls = new HashSet<String>();
				for(String s : toPrint)
					System.out.println(s);
			}
		}));
	}
}
