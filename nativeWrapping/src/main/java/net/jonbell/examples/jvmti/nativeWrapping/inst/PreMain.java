package net.jonbell.examples.jvmti.nativeWrapping.inst;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

public class PreMain {
	public static void premain(String args, Instrumentation inst) {
		inst.addTransformer(new ClassFileTransformer() {
			@Override
			public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
				if(isIgnoredClass(className))
					return classfileBuffer;
				try {
					ClassReader cr = new ClassReader(classfileBuffer);

					ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
					cr.accept(new NativeWrappingCV(cw), 0);
					
//					Uncomment below if you want to dump all of the instrumented class files
//					File f = new File("debug/"+cr.getClassName().replace('/', '.')+".class");
//					if(!f.getParentFile().exists())
//						f.getParentFile().mkdir();
//					FileOutputStream fos = new FileOutputStream(f);
//					fos.write(cw.toByteArray());
//					fos.close();
					
					return cw.toByteArray();
				} catch (Throwable t) {
					//Make sure that an exception in instrumentation gets printed, rather than squelched
					t.printStackTrace();
					return null;
				}
			}
		});
	}
	
	public static boolean isIgnoredClass(String owner) {
		//For one reason or another, we can't muck with these classes with ease.
		//Instances of these classes that you want to be tagged will be tagged with JVMTI
		return owner.startsWith("java/lang/Object") || owner.startsWith("java/lang/Number") || owner.startsWith("java/lang/Comparable") || owner.startsWith("java/lang/ref/SoftReference")
				|| owner.startsWith("java/lang/ref/Reference") || owner.startsWith("java/lang/ref/FinalizerReference") || owner.startsWith("java/lang/Boolean")
				|| owner.startsWith("java/lang/Character") || owner.startsWith("java/lang/Float") || owner.startsWith("java/lang/Byte") || owner.startsWith("java/lang/Short")
				|| owner.startsWith("java/lang/Integer") || owner.startsWith("java/lang/StackTraceElement") || (owner.startsWith("edu/columbia/cs/psl/testdepends"))
				|| owner.startsWith("sun/awt/image/codec/")
												|| (owner.startsWith("sun/reflect/Reflection")) 
				|| owner.equals("java/lang/reflect/Proxy") 
				|| owner.startsWith("sun/reflection/annotation/AnnotationParser") 
				|| owner.startsWith("sun/reflect/MethodAccessor")
				|| owner.startsWith("sun/reflect/ConstructorAccessor")
				|| owner.startsWith("sun/reflect/SerializationConstructorAccessor")
				|| owner.startsWith("sun/reflect/GeneratedMethodAccessor") || owner.startsWith("sun/reflect/GeneratedConstructorAccessor")
				|| owner.startsWith("sun/reflect/GeneratedSerializationConstructor");
	}
}
