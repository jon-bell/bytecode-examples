package net.jonbell.examples.bytecode.instrumenting;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

public class ClassCoverageClassFileTransformer implements ClassFileTransformer {
	boolean shouldIgnore;

	boolean shouldIgnore(ClassReader cr) {
		shouldIgnore = false;
		cr.accept(new ClassVisitor(Opcodes.ASM5) {
			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				super.visit(version, access, name, signature, superName, interfaces);
				if (!((access & Opcodes.ACC_INTERFACE) == 0 && (access & Opcodes.ACC_ENUM) == 0))
					shouldIgnore = true; //only bother with classes
			}

			@Override
			public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
				if (name.equals(ClassCoverageCV.CLASS_COVERAGE_FIELD))
					shouldIgnore = true; //if the field is already there, then we instrumented it statically
				return super.visitField(access, name, desc, signature, value);
			}
		}, ClassReader.SKIP_CODE);
		return shouldIgnore;
	}

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		ClassReader cr = new ClassReader(classfileBuffer);
		if (shouldIgnore(cr))
			return classfileBuffer;
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		try {
			ClassVisitor cv = new ClassCoverageCV(cw);
			cr.accept(cv, 0);
			return cw.toByteArray();
		} catch (Throwable t) {
			t.printStackTrace();
			return null;
		}
	}
}
