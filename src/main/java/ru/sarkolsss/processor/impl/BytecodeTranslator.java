package ru.sarkolsss.processor.impl;

import org.objectweb.asm.*;
import ru.sarkolsss.util.MethodContext;
import ru.sarkolsss.util.Util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class BytecodeTranslator extends ClassVisitor {
    private final MethodContext ctx;
    private final Method targetMethod;

    public BytecodeTranslator(MethodContext ctx, Method targetMethod) {
        super(Opcodes.ASM9);
        this.ctx = ctx;
        this.targetMethod = targetMethod;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                     String signature, String[] exceptions) {
        if (name.equals(targetMethod.getName()) && descriptor.equals(Type.getMethodDescriptor(targetMethod))) {
            return new MethodTranslator();
        }
        return null;
    }

    private class MethodTranslator extends MethodVisitor {
        private final Stack<String> stack = new Stack<>();
        private final Map<Integer, String> locals = new HashMap<>();
        private int tempCounter = 0;
        private int labelCounter = 0;
        private final Set<String> declaredVars = new HashSet<>();
        private final Map<Label, String> labelNames = new HashMap<>();
        private final Map<String, Integer> classCounters = new HashMap<>();

        public MethodTranslator() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitCode() {
            Class<?>[] paramTypes = targetMethod.getParameterTypes();
            boolean isStatic = Modifier.isStatic(targetMethod.getModifiers());

            int paramIndex = 0;
            for (int i = 0; i < paramTypes.length; i++) {
                Type paramType = Type.getType(paramTypes[i]);
                String cppType = Util.convertType(paramType);
                String paramName = "param_" + paramIndex;
                String argName = "arg" + i;

                ctx.content.append("    ").append(cppType).append(" ").append(paramName)
                        .append(" = ").append(argName).append(";\n");

                locals.put(paramIndex, paramName);
                declaredVars.add(paramName);
                paramIndex++;
            }

            if (!(paramTypes.length == 0)) {
                ctx.content.append("\n");
            }
        }

        private String getUniqueClassName(String className) {
            int count = classCounters.getOrDefault(className, 0);
            classCounters.put(className, count + 1);
            if (count == 0) {
                return className;
            }
            return className + "_" + count;
        }

        private String getLabelName(Label label) {
            return labelNames.computeIfAbsent(label, l -> "L" + (labelCounter++));
        }

        private String declareVariable(String prefix) {
            String varName = prefix + (tempCounter++);
            declaredVars.add(varName);
            return varName;
        }

        private boolean hasReturnBeforeLabel = false;
        @Override
        public void visitInsn(int opcode) {
            try {
                switch (opcode) {
                    case Opcodes.ACONST_NULL:
                        stack.push("nullptr");
                        break;
                    case Opcodes.ICONST_M1:
                        stack.push("-1");
                        break;
                    case Opcodes.ICONST_0:
                    case Opcodes.LCONST_0:
                    case Opcodes.FCONST_0:
                    case Opcodes.DCONST_0:
                        stack.push("0");
                        break;
                    case Opcodes.ICONST_1:
                    case Opcodes.LCONST_1:
                    case Opcodes.FCONST_1:
                    case Opcodes.DCONST_1:
                        stack.push("1");
                        break;
                    case Opcodes.ICONST_2:
                        stack.push("2");
                        break;
                    case Opcodes.ICONST_3:
                        stack.push("3");
                        break;
                    case Opcodes.ICONST_4:
                        stack.push("4");
                        break;
                    case Opcodes.ICONST_5:
                        stack.push("5");
                        break;
                    case Opcodes.IADD:
                    case Opcodes.LADD:
                    case Opcodes.FADD:
                    case Opcodes.DADD:
                        if (stack.size() >= 2) {
                            String op2 = stack.pop();
                            String op1 = stack.pop();
                            String result = declareVariable("add_");
                            ctx.content.append("    auto ").append(result)
                                    .append(" = ").append(op1).append(" + ").append(op2).append(";\n");
                            stack.push(result);
                        }
                        break;
                    case Opcodes.ISUB:
                    case Opcodes.LSUB:
                    case Opcodes.FSUB:
                    case Opcodes.DSUB:
                        if (stack.size() >= 2) {
                            String op2 = stack.pop();
                            String op1 = stack.pop();
                            String result = declareVariable("sub_");
                            ctx.content.append("    auto ").append(result)
                                    .append(" = ").append(op1).append(" - ").append(op2).append(";\n");
                            stack.push(result);
                        }
                        break;
                    case Opcodes.IMUL:
                    case Opcodes.LMUL:
                    case Opcodes.FMUL:
                    case Opcodes.DMUL:
                        if (stack.size() >= 2) {
                            String op2 = stack.pop();
                            String op1 = stack.pop();
                            String result = declareVariable("mul_");
                            ctx.content.append("    auto ").append(result)
                                    .append(" = ").append(op1).append(" * ").append(op2).append(";\n");
                            stack.push(result);
                        }
                        break;
                    case Opcodes.DUP:
                        if (!stack.isEmpty()) {
                            String top = stack.peek();
                            stack.push(top);
                        }
                        break;
                    case Opcodes.POP:
                        if (!stack.isEmpty()) {
                            stack.pop();
                        }
                        break;
                    case Opcodes.IRETURN:
                    case Opcodes.LRETURN:
                    case Opcodes.FRETURN:
                    case Opcodes.DRETURN:
                    case Opcodes.ARETURN:
                        if (!stack.isEmpty()) {
                            ctx.content.append("    return ").append(stack.pop()).append(";\n");
                        }
                        hasReturnBeforeLabel = true; // <-- ДОБАВИТЬ
                        break;
                    case Opcodes.RETURN:
                        ctx.content.append("    return;\n");
                        hasReturnBeforeLabel = true; // <-- ДОБАВИТЬ
                        break;
                }
            } catch (Exception e) {
            }
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            switch (opcode) {
                case Opcodes.ILOAD:
                case Opcodes.LLOAD:
                case Opcodes.FLOAD:
                case Opcodes.DLOAD:
                case Opcodes.ALOAD:
                    String varName = locals.get(var);
                    if (varName == null) {
                        varName = "local_" + var;
                        if (!declaredVars.contains(varName)) {
                            ctx.content.append("    auto ").append(varName).append(";\n");
                            declaredVars.add(varName);
                        }
                        locals.put(var, varName);
                    }
                    stack.push(varName);
                    break;
                case Opcodes.ISTORE:
                case Opcodes.LSTORE:
                case Opcodes.FSTORE:
                case Opcodes.DSTORE:
                case Opcodes.ASTORE:
                    if (!stack.isEmpty()) {
                        String value = stack.pop();
                        String localVar = locals.get(var);
                        if (localVar == null) {
                            localVar = "local_" + var;
                            ctx.content.append("    auto ").append(localVar).append(" = ").append(value).append(";\n");
                            declaredVars.add(localVar);
                            locals.put(var, localVar);
                        } else {
                            ctx.content.append("    ").append(localVar).append(" = ").append(value).append(";\n");
                        }
                    }
                    break;
            }
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                stack.push(String.valueOf(operand));
            }
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof String) {
                String constName = declareVariable("str_");
                ctx.content.append("    jstring ").append(constName)
                        .append(" = env->NewStringUTF(\"")
                        .append(escapeString(value.toString())).append("\");\n");
                stack.push(constName);
            } else if (value instanceof Integer) {
                stack.push(value.toString());
            } else if (value instanceof Float) {
                stack.push(value.toString() + "f");
            } else if (value instanceof Long) {
                stack.push(value.toString() + "L");
            } else if (value instanceof Double) {
                stack.push(value.toString());
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            String classVar = getUniqueClassName(owner.replace('/', '_') + "_cls");

            if (opcode == Opcodes.GETSTATIC) {
                String fieldVar = declareVariable("fld_");
                ctx.content.append("    jclass ").append(classVar).append(" = env->FindClass(\"")
                        .append(owner).append("\");\n");
                ctx.content.append("    jfieldID ").append(fieldVar).append("_id = env->GetStaticFieldID(")
                        .append(classVar).append(", \"").append(name).append("\", \"")
                        .append(descriptor).append("\");\n");

                String jniGetter = getJniFieldGetter(descriptor, true);
                ctx.content.append("    auto ").append(fieldVar).append(" = env->").append(jniGetter)
                        .append("(").append(classVar).append(", ").append(fieldVar).append("_id);\n");
                stack.push(fieldVar);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            Type methodType = Type.getMethodType(descriptor);
            Type[] argTypes = methodType.getArgumentTypes();

            List<String> args = new ArrayList<>();
            for (int i = argTypes.length - 1; i >= 0; i--) {
                if (!stack.isEmpty()) {
                    args.add(0, stack.pop());
                }
            }

            String objectRef = null;

            // ИСПРАВЛЕНИЕ: правильная обработка конструкторов и вызовов методов
            if (opcode == Opcodes.INVOKESPECIAL && name.equals("<init>")) {
                // Это конструктор - объект на вершине стека
                if (!stack.isEmpty()) {
                    objectRef = stack.pop();
                }
            } else if (opcode != Opcodes.INVOKESTATIC) {
                // Обычный метод экземпляра - объект на вершине стека
                if (!stack.isEmpty()) {
                    objectRef = stack.pop();
                }
            }

            String classVar = getUniqueClassName(owner.replace('/', '_') + "_cls");
            String methodVar = declareVariable("mid_");

            ctx.content.append("    jclass ").append(classVar).append(" = env->FindClass(\"")
                    .append(owner).append("\");\n");

            String methodIdType = (opcode == Opcodes.INVOKESTATIC) ? "GetStaticMethodID" : "GetMethodID";
            ctx.content.append("    jmethodID ").append(methodVar).append(" = env->")
                    .append(methodIdType).append("(").append(classVar).append(", \"")
                    .append(name).append("\", \"").append(descriptor).append("\");\n");

            Type returnType = methodType.getReturnType();
            String resultVar = null;

            // ИСПРАВЛЕНИЕ: для конструкторов не создаём результат
            if (name.equals("<init>")) {
                ctx.content.append("    env->CallNonvirtualVoidMethod(");
                if (objectRef != null) {
                    ctx.content.append(objectRef);
                }
                ctx.content.append(", ").append(classVar).append(", ").append(methodVar);

                for (String arg : args) {
                    ctx.content.append(", ").append(arg);
                }

                ctx.content.append(");\n");

                // Объект остаётся на стеке для дальнейшего использования
                if (objectRef != null) {
                    stack.push(objectRef);
                }
            } else {
                // Обычный метод
                String jniCall = getJniMethodCall(returnType, opcode == Opcodes.INVOKESTATIC);

                if (!returnType.getDescriptor().equals("V")) {
                    resultVar = declareVariable("res_");
                    ctx.content.append("    auto ").append(resultVar).append(" = ");
                } else {
                    ctx.content.append("    ");
                }

                ctx.content.append("env->").append(jniCall).append("(");

                if (opcode == Opcodes.INVOKESTATIC) {
                    ctx.content.append(classVar);
                } else if (objectRef != null) {
                    ctx.content.append(objectRef);
                } else {
                    ctx.content.append("obj"); // fallback
                }

                ctx.content.append(", ").append(methodVar);

                for (String arg : args) {
                    ctx.content.append(", ").append(arg);
                }

                ctx.content.append(");\n");

                if (resultVar != null) {
                    stack.push(resultVar);
                }
            }
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.NEW) {
                String classVar = getUniqueClassName(type.replace('/', '_') + "_cls");
                String objVar = declareVariable("obj_");

                ctx.content.append("    jclass ").append(classVar).append(" = env->FindClass(\"")
                        .append(type).append("\");\n");

                ctx.content.append("    jobject ").append(objVar)
                        .append(" = env->AllocObject(").append(classVar).append(");\n");

                stack.push(objVar);
            }
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            // Сбрасываем флаг при использовании goto
            if (opcode == Opcodes.GOTO) {
                hasReturnBeforeLabel = false;
            }

            String labelName = getLabelName(label);

            switch (opcode) {
                case Opcodes.IFEQ:
                    if (!stack.isEmpty()) {
                        String value = stack.pop();
                        ctx.content.append("    if (").append(value).append(" == 0) goto ").append(labelName).append(";\n");
                        hasReturnBeforeLabel = false; // <-- ДОБАВИТЬ
                    }
                    break;
                case Opcodes.IFNE:
                    if (!stack.isEmpty()) {
                        String value = stack.pop();
                        ctx.content.append("    if (").append(value).append(" != 0) goto ").append(labelName).append(";\n");
                        hasReturnBeforeLabel = false; // <-- ДОБАВИТЬ
                    }
                    break;
                case Opcodes.GOTO:
                    ctx.content.append("    goto ").append(labelName).append(";\n");
                    hasReturnBeforeLabel = false; // <-- ДОБАВИТЬ
                    break;
            }
        }

        @Override
        public void visitLabel(Label label) {
            // Не генерируем метки после return
            if (!hasReturnBeforeLabel) {
                String labelName = getLabelName(label);
                ctx.content.append(labelName).append(":\n");
            }
        }

        private String getJniFieldGetter(String descriptor, boolean isStatic) {
            String prefix = isStatic ? "GetStatic" : "Get";
            switch (descriptor.charAt(0)) {
                case 'Z': return prefix + "BooleanField";
                case 'B': return prefix + "ByteField";
                case 'C': return prefix + "CharField";
                case 'S': return prefix + "ShortField";
                case 'I': return prefix + "IntField";
                case 'J': return prefix + "LongField";
                case 'F': return prefix + "FloatField";
                case 'D': return prefix + "DoubleField";
                default: return prefix + "ObjectField";
            }
        }

        private String getJniMethodCall(Type returnType, boolean isStatic) {
            String prefix = isStatic ? "CallStatic" : "Call";
            switch (returnType.getDescriptor()) {
                case "V": return prefix + "VoidMethod";
                case "Z": return prefix + "BooleanMethod";
                case "B": return prefix + "ByteMethod";
                case "C": return prefix + "CharField";
                case "S": return prefix + "ShortMethod";
                case "I": return prefix + "IntMethod";
                case "J": return prefix + "LongMethod";
                case "F": return prefix + "FloatMethod";
                case "D": return prefix + "DoubleMethod";
                default: return prefix + "ObjectMethod";
            }
        }

        private String escapeString(String str) {
            return str.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }
}