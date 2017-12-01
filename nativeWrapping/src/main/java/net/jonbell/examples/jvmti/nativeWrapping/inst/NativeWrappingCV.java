package net.jonbell.examples.jvmti.nativeWrapping.inst;

import java.util.Arrays;
import java.util.HashSet;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.MethodNode;

import com.sun.org.apache.bcel.internal.generic.INVOKESPECIAL;
import com.sun.org.apache.bcel.internal.generic.INVOKESTATIC;

public class NativeWrappingCV extends ClassVisitor {

	public NativeWrappingCV(ClassVisitor cv) {
		super(Opcodes.ASM5, cv);
	}

	boolean addField = false;
	boolean isClass;
	String className;

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		className = name;
		super.visit(version, access, name, signature, superName, interfaces);
	}

	HashSet<MethodNode> nativeMethods = new HashSet<MethodNode>();

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		if ((access & Opcodes.ACC_NATIVE) != 0) {
			// Native method
			nativeMethods.add(new MethodNode(access, name, desc, signature, exceptions));
			name = "$$Enhanced$$By$$JVMTI$$" + name;
		}
		return super.visitMethod(access, name, desc, signature, exceptions);
	}

	@Override
	public void visitEnd() {
		for (MethodNode mn : nativeMethods) {
			int acc = mn.access & ~Opcodes.ACC_NATIVE;
			GeneratorAdapter ga = new GeneratorAdapter(super.visitMethod(acc, mn.name, mn.desc, mn.signature, new String[] {}), acc, mn.name, mn.desc);

			ga.visitCode();
			
			//Log the fact that this native method was called
			ga.visitLdcInsn(className+"."+mn.name+mn.desc);
			ga.visitMethodInsn(Opcodes.INVOKESTATIC, "net/jonbell/examples/jvmti/nativeWrapping/runtime/NativeLogger", "recordNative", "(Ljava/lang/String;)V", false);
			
			boolean isStatic = (mn.access & Opcodes.ACC_STATIC) != 0;
			if(!isStatic)
				ga.loadThis();
			ga.loadArgs();
			ga.visitMethodInsn((isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL), className, "$$Enhanced$$By$$JVMTI$$" + mn.name, mn.desc, false);
			ga.returnValue();
			ga.visitMaxs(0, 0);
			ga.visitEnd();
		}
		super.visitEnd();
	}
}
