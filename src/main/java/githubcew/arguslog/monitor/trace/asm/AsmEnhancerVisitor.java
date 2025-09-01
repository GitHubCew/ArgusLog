
package githubcew.arguslog.monitor.trace.asm;

import org.objectweb.asm.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

/**
 * 使用 ASM 增强类，在指定方法前后插入：
 * - ArgusTraceRequestContext.startMethod(className, methodName, parameterTypes): 插入方法调用开始处理逻辑
 *
 * - ArgusTraceRequestContext.endMethod() : 插入方法调用处理结束逻辑
 */
public class AsmEnhancerVisitor {

    /**
     * 增强指定方法，插入 trace 上下文调用
     *
     * @param originalBytes     原始类字节码
     * @param internalClassName 类名（内部形式，如 com/example/Test）
     * @param methodNames        方法名列表
     * @return 增强后的字节码
     */
    public static byte[] modify(
            byte[] originalBytes,
            String internalClassName,
            List<String> methodNames) {

        Set<String> targetMethods = new HashSet<>(methodNames);

        ClassReader cr = new ClassReader(originalBytes);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

                // 如果是目标方法，就包装
                if (targetMethods.contains(name)) {
                    return new TraceMethodVisitor(mv, internalClassName, name, desc);
                }
                return mv;
            }
        }, 0);

        return cw.toByteArray();
    }

    /**
     * 在方法前后插入 ArgusTraceContext 调用
     */
    private static class TraceMethodVisitor extends MethodVisitor {
        private final String owner;
        private final String methodName;
        private final String methodDesc;

        private final Label startLabel = new Label();
        private final Label endLabel = new Label();
        private final Label returnLabel = new Label();

        public TraceMethodVisitor(MethodVisitor mv, String owner, String methodName, String methodDesc) {
            super(Opcodes.ASM9, mv);
            this.owner = owner;
            this.methodName = methodName;
            this.methodDesc = methodDesc;
        }

        @Override
        public void visitCode() {
            // 标记 try 块开始
            mv.visitLabel(startLabel);

            // --- 1. 压入 className ---
            String className = Type.getObjectType(owner).getClassName(); // 转为 com.xxx.Xxx
            mv.visitLdcInsn(className);

            // --- 2. 压入 methodName ---
            mv.visitLdcInsn(methodName);

            // --- 3. 生成 parameterTypes: Class<?>[] ---
            Type methodType = Type.getMethodType(methodDesc);
            Type[] argTypes = methodType.getArgumentTypes();
            int argCount = argTypes.length;

            // 创建数组：new Class[argCount]
            mv.visitIntInsn(BIPUSH, argCount);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");

            // 填充每个 Class<?> 元素
            for (int i = 0; i < argCount; i++) {
                mv.visitInsn(DUP);                    // 复制数组引用
                mv.visitIntInsn(BIPUSH, i);           // 压入索引 i
                pushClassConstant(argTypes[i]);       // 压入 Class 对象（如 String.class）
                mv.visitInsn(AASTORE);                // array[i] = class
            }

            // --- 4. 调用 ArgusTraceContext.startMethod(...) ---
            mv.visitMethodInsn(INVOKESTATIC,
                    "githubcew/arguslog/monitor/trace/ArgusTraceRequestContext",
                    "startMethod",
                    "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Class;)V", false);

            // ✅ 正确位置：原始方法体开始（由 ASM 框架驱动）
            // 不要再手动调 mv.visitCode()，它会由 ClassReader 自动触发
            super.visitCode();
        }

        @Override
        public void visitInsn(int opcode) {
            // 在 return 或 异常抛出前插入 endMethod
            if (opcode == IRETURN || opcode == LRETURN || opcode == FRETURN ||
                    opcode == DRETURN || opcode == ARETURN || opcode == RETURN ||
                    opcode == ATHROW) {

                mv.visitMethodInsn(INVOKESTATIC,
                        "githubcew/arguslog/monitor/trace/ArgusTraceRequestContext",
                        "endMethod",
                        "()V", false);
            }
            mv.visitInsn(opcode);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            // 定义 try-finally 块
            mv.visitTryCatchBlock(startLabel, endLabel, endLabel, null);
            mv.visitLabel(endLabel); // catch any

            // 插入：ArgusTraceContext.endMethod()
            mv.visitMethodInsn(INVOKESTATIC,
                    "githubcew/arguslog/monitor/trace/ArgusTraceContext",
                    "endMethod",
                    "()V", false);

            // 重新抛出异常
            mv.visitInsn(ATHROW);

            // 正常返回
            mv.visitLabel(returnLabel);

            // 增加栈空间余量
            mv.visitMaxs(maxStack + 6, maxLocals);
        }

        /**
         * 将指定类型的 Class 对象压栈（如 String.class）
         */
        private void pushClassConstant(Type type) {
            switch (type.getSort()) {
                case Type.BOOLEAN:
                    mv.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "TYPE", "Ljava/lang/Class;");
                    break;
                case Type.CHAR:
                    mv.visitFieldInsn(GETSTATIC, "java/lang/Character", "TYPE", "Ljava/lang/Class;");
                    break;
                case Type.BYTE:
                    mv.visitFieldInsn(GETSTATIC, "java/lang/Byte", "TYPE", "Ljava/lang/Class;");
                    break;
                case Type.SHORT:
                    mv.visitFieldInsn(GETSTATIC, "java/lang/Short", "TYPE", "Ljava/lang/Class;");
                    break;
                case Type.INT:
                    mv.visitFieldInsn(GETSTATIC, "java/lang/Integer", "TYPE", "Ljava/lang/Class;");
                    break;
                case Type.FLOAT:
                    mv.visitFieldInsn(GETSTATIC, "java/lang/Float", "TYPE", "Ljava/lang/Class;");
                    break;
                case Type.LONG:
                    mv.visitFieldInsn(GETSTATIC, "java/lang/Long", "TYPE", "Ljava/lang/Class;");
                    break;
                case Type.DOUBLE:
                    mv.visitFieldInsn(GETSTATIC, "java/lang/Double", "TYPE", "Ljava/lang/Class;");
                    break;
                case Type.OBJECT:
                case Type.ARRAY:
                    mv.visitLdcInsn(type); // LDC Type → 对应的 Class 对象
                    break;
                default:
                    throw new IllegalArgumentException("不支持的类型: " + type);
            }
        }
    }
}