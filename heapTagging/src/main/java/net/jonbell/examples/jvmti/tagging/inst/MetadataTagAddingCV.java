package net.jonbell.examples.jvmti.tagging.inst;

import java.util.Arrays;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MetadataTagAddingCV extends ClassVisitor {

	static String METADATA_FIELD_NAME = "jonbellmetadatafield";

	public MetadataTagAddingCV(ClassVisitor cv) {
		super(Opcodes.ASM5, cv);
	}

	boolean addField = false;
	boolean isClass;
	String className;

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		isClass = (access & Opcodes.ACC_ENUM) == 0 && (access & Opcodes.ACC_INTERFACE) == 0;
		addField = isClass && PreMain.isIgnoredClass(superName);
		className = name;

		if (isClass) {
			//Add the interface declaration
			String[] newInterfaces = new String[interfaces.length + 1];
			System.arraycopy(interfaces, 0, newInterfaces, 0, interfaces.length);
			newInterfaces[interfaces.length] = "net/jonbell/examples/jvmti/tagging/runtime/Tagged";
			interfaces = newInterfaces;
		}
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public void visitEnd() {
		if (isClass) {
			//Add method to retrieve and set tag
			MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "getMetadataTag", "()Ljava/lang/Object;", null, null);
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitFieldInsn(Opcodes.GETFIELD, className, METADATA_FIELD_NAME, "Ljava/lang/Object;");
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();

			mv = super.visitMethod(Opcodes.ACC_PUBLIC, "setMetadataTag", "(Ljava/lang/Object;)V", null, null);
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitFieldInsn(Opcodes.PUTFIELD, className, METADATA_FIELD_NAME, "Ljava/lang/Object;");
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();

			if (addField) {
				//Add the field itself
				super.visitField(Opcodes.ACC_PUBLIC, METADATA_FIELD_NAME, "Ljava/lang/Object;", null, null);
			}
		}
		super.visitEnd();
	}
}
