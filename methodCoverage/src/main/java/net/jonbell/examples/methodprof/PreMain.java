package net.jonbell.examples.methodprof;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import net.jonbell.examples.methodprof.inst.MethodProfilingCV;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

public class PreMain {
	public static void premain(String args, Instrumentation inst) {
		inst.addTransformer(new ClassFileTransformer() {

			@Override
			public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
				//For our purposes, skip java* and sun* internal methods
				if (className.startsWith("java") || className.startsWith("sun"))
					return null;
				ClassReader cr = new ClassReader(classfileBuffer);
				ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
				try {
					ClassVisitor cv = new MethodProfilingCV(cw);
					cr.accept(cv, 0);
					return cw.toByteArray();
				} catch (Throwable t) {
					t.printStackTrace();
					return null;
				}
			}
		});

	}
}
