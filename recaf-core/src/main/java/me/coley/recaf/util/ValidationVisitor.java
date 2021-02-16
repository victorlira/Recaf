package me.coley.recaf.util;

import me.coley.recaf.RecafConstants;
import org.objectweb.asm.*;

/**
 * An empty visitor that provides empty visitor instances,
 * This will allow ASM to attempt to interpret all portions of some input bytecode, allowing detection of
 * any code that causes ASM to fail to parse a class.
 *
 * @author Matt Coley
 */
public class ValidationVisitor extends ClassVisitor {
	private static final int API = RecafConstants.ASM_VERSION;

	/**
	 * Create visitor.
	 */
	public ValidationVisitor() {
		super(API);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		return anno();
	}

	@Override
	public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
		return anno();
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		return field();
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		return method();
	}

	private static AnnotationVisitor anno() {
		return new AnnotationVisitor(API) {
			@Override
			public AnnotationVisitor visitAnnotation(String name, String descriptor) {
				return anno();
			}

			@Override
			public AnnotationVisitor visitArray(String name) {
				return anno();
			}
		};
	}

	private static FieldVisitor field() {
		return new FieldVisitor(API) {
			@Override
			public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
				return anno();
			}

			@Override
			public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
				return anno();
			}
		};
	}

	private static MethodVisitor method() {
		return new MethodVisitor(API) {
			@Override
			public AnnotationVisitor visitAnnotationDefault() {
				return anno();
			}

			@Override
			public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
				return anno();
			}

			@Override
			public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
				return anno();
			}

			@Override
			public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
				return anno();
			}

			@Override
			public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
				return anno();
			}

			@Override
			public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
				return anno();
			}

			@Override
			public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String descriptor, boolean visible) {
				return anno();
			}
		};
	}
}
