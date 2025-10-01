package ru.sarkolsss.codegen;

import org.objectweb.asm.Opcodes;
import ru.sarkolsss.bytecode.InstructionInfo;
import ru.sarkolsss.bytecode.MethodBodyInfo;
import ru.sarkolsss.bytecode.NativeMethodInfo;
import ru.sarkolsss.utils.Logger;
import java.util.*;

public class BytecodeTranslator {
    private final TypeMapper typeMapper;
    private final JniHelper jniHelper;
    private int tempVarCounter;
    private int labelCounter;

    public BytecodeTranslator(TypeMapper typeMapper) {
        this.typeMapper = typeMapper;
        this.jniHelper = new JniHelper();
    }

    public String translateMethodBody(MethodBodyInfo bodyInfo, NativeMethodInfo method) {
        StringBuilder code = new StringBuilder();
        tempVarCounter = 0;
        labelCounter = 0;

        Stack<String> stack = new Stack<>();
        Map<Integer, String> locals = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        Set<String> emittedLabels = new HashSet<>();

        int paramIndex = 0;
        if ((method.getAccess() & Opcodes.ACC_STATIC) == 0) {
            locals.put(paramIndex++, "thisObj");
        }

        String[] paramTypes = typeMapper.getParameterTypes(method.getDescriptor());
        for (int i = 0; i < paramTypes.length; i++) {
            locals.put(paramIndex++, "param" + i);
        }

        try {
            boolean needsSemicolon = false;

            for (int i = 0; i < bodyInfo.instructions.size(); i++) {
                InstructionInfo insn = bodyInfo.instructions.get(i);
                boolean isLabelOnly = (insn.opcode == -1 && "LABEL".equals(insn.type));

                if (isLabelOnly && insn.label != null) {
                    String labelName = getOrCreateLabel(insn.label, labels);

                    if (!emittedLabels.contains(labelName)) {
                        if (needsSemicolon) {
                            code.append("    ;\n");
                            needsSemicolon = false;
                        }

                        code.append(labelName).append(":\n");
                        emittedLabels.add(labelName);
                        needsSemicolon = true;
                    }
                    continue;
                }

                if (needsSemicolon && insn.opcode != -1) {
                    needsSemicolon = false;
                }

                String translated = translateInstruction(insn, stack, locals, code, labels);

                if (translated != null && !translated.isEmpty()) {
                    code.append("    ").append(translated).append("\n");
                    needsSemicolon = false;
                }
            }

            if (needsSemicolon) {
                code.append("    ;\n");
            }

        } catch (Exception e) {
            Logger.error("Bytecode translation error: " + e.getMessage());
            e.printStackTrace();
            code.append("    // Translation error: ").append(e.getMessage()).append("\n");
        }

        String returnType = typeMapper.getReturnType(method.getDescriptor());
        if (!returnType.equals("void") && !stack.isEmpty()) {
            String retVal = stack.pop();
            String jniRet = jniHelper.convertToJniReturn(retVal, returnType, code);
            code.append("    return ").append(jniRet).append(";\n");
        } else if (!returnType.equals("void")) {
            code.append("    return ").append(typeMapper.getDefaultValue(returnType)).append(";\n");
        }

        return code.toString();
    }


    private String translateInstruction(InstructionInfo insn, Stack<String> stack,
                                        Map<Integer, String> locals, StringBuilder code,
                                        Map<String, String> labels) {
        if (insn.opcode == -1) {
            if ("LABEL".equals(insn.type)) {
                return null;
            }
            return null;
        }

        switch (insn.opcode) {
            case Opcodes.NOP: return null;
            case Opcodes.ACONST_NULL: stack.push("nullptr"); return null;

            case Opcodes.ICONST_M1: stack.push("-1"); return null;
            case Opcodes.ICONST_0: stack.push("0"); return null;
            case Opcodes.ICONST_1: stack.push("1"); return null;
            case Opcodes.ICONST_2: stack.push("2"); return null;
            case Opcodes.ICONST_3: stack.push("3"); return null;
            case Opcodes.ICONST_4: stack.push("4"); return null;
            case Opcodes.ICONST_5: stack.push("5"); return null;

            case Opcodes.TABLESWITCH: return handleTableSwitch(insn, stack, code, labels);
            case Opcodes.LOOKUPSWITCH: return handleLookupSwitch(insn, stack, code, labels);

            case Opcodes.LCONST_0: stack.push("0LL"); return null;
            case Opcodes.LCONST_1: stack.push("1LL"); return null;

            case Opcodes.FCONST_0: stack.push("0.0f"); return null;
            case Opcodes.FCONST_1: stack.push("1.0f"); return null;
            case Opcodes.FCONST_2: stack.push("2.0f"); return null;

            case Opcodes.DCONST_0: stack.push("0.0"); return null;
            case Opcodes.DCONST_1: stack.push("1.0"); return null;

            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
                stack.push(String.valueOf(insn.operand));
                return null;

            case Opcodes.LDC:
                return handleLdc(insn, stack, code);

            case Opcodes.ILOAD: case Opcodes.LLOAD: case Opcodes.FLOAD:
            case Opcodes.DLOAD: case Opcodes.ALOAD:
                String localVar = locals.getOrDefault(insn.var, "local_" + insn.var);
                stack.push(localVar);
                return null;

            case Opcodes.ISTORE: case Opcodes.LSTORE: case Opcodes.FSTORE:
            case Opcodes.DSTORE: case Opcodes.ASTORE:
                if (stack.isEmpty()) return "// Stack underflow at STORE";
                String value = stack.pop();
                String varName = "local_" + insn.var;
                locals.put(insn.var, varName);
                return "auto " + varName + " = " + value + ";";

            case Opcodes.IALOAD: case Opcodes.LALOAD: case Opcodes.FALOAD:
            case Opcodes.DALOAD: case Opcodes.AALOAD: case Opcodes.BALOAD:
            case Opcodes.CALOAD: case Opcodes.SALOAD:
                return handleArrayLoad(insn.opcode, stack, code);

            case Opcodes.IASTORE: case Opcodes.LASTORE: case Opcodes.FASTORE:
            case Opcodes.DASTORE: case Opcodes.AASTORE: case Opcodes.BASTORE:
            case Opcodes.CASTORE: case Opcodes.SASTORE:
                return handleArrayStore(insn.opcode, stack, code);

            case Opcodes.IADD: case Opcodes.LADD: case Opcodes.FADD: case Opcodes.DADD:
                return handleBinaryOp(stack, code, "+");
            case Opcodes.ISUB: case Opcodes.LSUB: case Opcodes.FSUB: case Opcodes.DSUB:
                return handleBinaryOp(stack, code, "-");
            case Opcodes.IMUL: case Opcodes.LMUL: case Opcodes.FMUL: case Opcodes.DMUL:
                return handleBinaryOp(stack, code, "*");
            case Opcodes.IDIV: case Opcodes.LDIV: case Opcodes.FDIV: case Opcodes.DDIV:
                return handleBinaryOp(stack, code, "/");
            case Opcodes.IREM: case Opcodes.LREM: case Opcodes.FREM: case Opcodes.DREM:
                return handleBinaryOp(stack, code, "%");

            case Opcodes.ISHL: case Opcodes.LSHL:
                return handleBinaryOp(stack, code, "<<");
            case Opcodes.ISHR: case Opcodes.LSHR:
                return handleBinaryOp(stack, code, ">>");
            case Opcodes.IUSHR: case Opcodes.LUSHR:
                return handleBinaryOp(stack, code, ">>>");

            case Opcodes.IAND: case Opcodes.LAND:
                return handleBinaryOp(stack, code, "&");
            case Opcodes.IOR: case Opcodes.LOR:
                return handleBinaryOp(stack, code, "|");
            case Opcodes.IXOR: case Opcodes.LXOR:
                return handleBinaryOp(stack, code, "^");

            case Opcodes.INEG: case Opcodes.LNEG: case Opcodes.FNEG: case Opcodes.DNEG:
                return handleUnaryOp(stack, code, "-");

            case Opcodes.IINC:
                String incVar = locals.getOrDefault(insn.var, "local_" + insn.var);
                return incVar + " += " + insn.increment + ";";

            case Opcodes.I2L: return handleConversion(stack, code, "(jlong)");
            case Opcodes.I2F: return handleConversion(stack, code, "(jfloat)");
            case Opcodes.I2D: return handleConversion(stack, code, "(jdouble)");
            case Opcodes.L2I: return handleConversion(stack, code, "(jint)");
            case Opcodes.L2F: return handleConversion(stack, code, "(jfloat)");
            case Opcodes.L2D: return handleConversion(stack, code, "(jdouble)");
            case Opcodes.F2I: return handleConversion(stack, code, "(jint)");
            case Opcodes.F2L: return handleConversion(stack, code, "(jlong)");
            case Opcodes.F2D: return handleConversion(stack, code, "(jdouble)");
            case Opcodes.D2I: return handleConversion(stack, code, "(jint)");
            case Opcodes.D2L: return handleConversion(stack, code, "(jlong)");
            case Opcodes.D2F: return handleConversion(stack, code, "(jfloat)");
            case Opcodes.I2B: return handleConversion(stack, code, "(jbyte)");
            case Opcodes.I2C: return handleConversion(stack, code, "(jchar)");
            case Opcodes.I2S: return handleConversion(stack, code, "(jshort)");

            case Opcodes.LCMP:
                return handleCompare(stack, code, "lcmp");
            case Opcodes.FCMPL: case Opcodes.FCMPG:
                return handleCompare(stack, code, "fcmp");
            case Opcodes.DCMPL: case Opcodes.DCMPG:
                return handleCompare(stack, code, "dcmp");

            case Opcodes.IFEQ: case Opcodes.IFNE: case Opcodes.IFLT:
            case Opcodes.IFGE: case Opcodes.IFGT: case Opcodes.IFLE:
                return handleIfCondition(insn, stack, code, labels);

            case Opcodes.IF_ICMPEQ: case Opcodes.IF_ICMPNE: case Opcodes.IF_ICMPLT:
            case Opcodes.IF_ICMPGE: case Opcodes.IF_ICMPGT: case Opcodes.IF_ICMPLE:
            case Opcodes.IF_ACMPEQ: case Opcodes.IF_ACMPNE:
                return handleIfCompare(insn, stack, code, labels);

            case Opcodes.GOTO:
                String gotoLabel = getOrCreateLabel(insn.label, labels);
                return "goto " + gotoLabel + ";";

            case Opcodes.GETSTATIC:
                return handleGetStatic(insn, stack, code);

            case Opcodes.PUTSTATIC:
                return handlePutStatic(insn, stack, code);

            case Opcodes.GETFIELD:
                return handleGetField(insn, stack, code);

            case Opcodes.PUTFIELD:
                return handlePutField(insn, stack, code);

            case Opcodes.INVOKEVIRTUAL:
                return handleInvokeVirtual(insn, stack, code);

            case Opcodes.INVOKESPECIAL:
                return handleInvokeSpecial(insn, stack, code);

            case Opcodes.INVOKESTATIC:
                return handleInvokeStatic(insn, stack, code);

            case Opcodes.INVOKEINTERFACE:
                return handleInvokeInterface(insn, stack, code);

            case Opcodes.NEW:
                return handleNew(insn, stack, code);

            case Opcodes.NEWARRAY:
                return handleNewArray(insn, stack, code);

            case Opcodes.ANEWARRAY:
                return handleANewArray(insn, stack, code);

            case Opcodes.ARRAYLENGTH:
                return handleArrayLength(stack, code);

            case Opcodes.ATHROW:
                return handleThrow(stack, code);

            case Opcodes.CHECKCAST:
                return handleCheckCast(insn, stack, code);

            case Opcodes.INSTANCEOF:
                return handleInstanceOf(insn, stack, code);

            case Opcodes.MONITORENTER:
                if (!stack.isEmpty()) stack.pop();
                return "// MONITORENTER";

            case Opcodes.MONITOREXIT:
                if (!stack.isEmpty()) stack.pop();
                return "// MONITOREXIT";

            case Opcodes.RETURN:
                return null;

            case Opcodes.IRETURN:
            case Opcodes.LRETURN:
            case Opcodes.FRETURN:
            case Opcodes.DRETURN:
            case Opcodes.ARETURN:
                return null;

            case Opcodes.POP:
                if (!stack.isEmpty()) stack.pop();
                return null;

            case Opcodes.POP2:
                if (!stack.isEmpty()) stack.pop();
                if (!stack.isEmpty()) stack.pop();
                return null;

            case Opcodes.DUP:
                if (!stack.isEmpty()) stack.push(stack.peek());
                return null;

            case Opcodes.DUP_X1:
                if (stack.size() >= 2) {
                    String v1 = stack.pop();
                    String v2 = stack.pop();
                    stack.push(v1);
                    stack.push(v2);
                    stack.push(v1);
                }
                return null;

            case Opcodes.DUP_X2:
                if (stack.size() >= 3) {
                    String v1 = stack.pop();
                    String v2 = stack.pop();
                    String v3 = stack.pop();
                    stack.push(v1);
                    stack.push(v3);
                    stack.push(v2);
                    stack.push(v1);
                }
                return null;

            case Opcodes.DUP2:
                if (stack.size() >= 2) {
                    String v1 = stack.pop();
                    String v2 = stack.pop();
                    stack.push(v2);
                    stack.push(v1);
                    stack.push(v2);
                    stack.push(v1);
                }
                return null;

            case Opcodes.SWAP:
                if (stack.size() >= 2) {
                    String v1 = stack.pop();
                    String v2 = stack.pop();
                    stack.push(v1);
                    stack.push(v2);
                }
                return null;

            default:
                return "// Unsupported opcode: " + insn.opcode;
        }
    }

    private String handleLdc(InstructionInfo insn, Stack<String> stack, StringBuilder code) {
        if (insn.constant instanceof String) {
            String escaped = ((String) insn.constant)
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
            String tempVar = "jstr_" + (tempVarCounter++);
            code.append("    jstring ").append(tempVar)
                    .append(" = env->NewStringUTF(\"").append(escaped).append("\");\n");
            stack.push(tempVar);
        } else if (insn.constant instanceof Integer) {
            stack.push("(jint)" + insn.constant);
        } else if (insn.constant instanceof Long) {
            stack.push("(jlong)" + insn.constant + "LL");
        } else if (insn.constant instanceof Float) {
            stack.push("(jfloat)" + insn.constant + "f");
        } else if (insn.constant instanceof Double) {
            stack.push("(jdouble)" + insn.constant);
        } else if (insn.constant instanceof org.objectweb.asm.Type) {
            org.objectweb.asm.Type type = (org.objectweb.asm.Type) insn.constant;
            String className = type.getClassName().replace('.', '/');
            String tempVar = "jclass_" + (tempVarCounter++);
            code.append("    jclass ").append(tempVar)
                    .append(" = env->FindClass(\"").append(className).append("\");\n");
            stack.push(tempVar);
        } else {
            stack.push("nullptr");
        }
        return null;
    }

    private String handleBinaryOp(Stack<String> stack, StringBuilder code, String op) {
        if (stack.size() < 2) return "// Stack underflow at " + op;
        String b = stack.pop();
        String a = stack.pop();
        String resultVar = "temp_" + (tempVarCounter++);

        if (op.equals(">>>")) {
            code.append("    auto ").append(resultVar).append(" = (unsigned)")
                    .append(a).append(" >> ").append(b).append(";\n");
        } else {
            code.append("    auto ").append(resultVar).append(" = ")
                    .append(a).append(" ").append(op).append(" ").append(b).append(";\n");
        }
        stack.push(resultVar);
        return null;
    }

    private String handleUnaryOp(Stack<String> stack, StringBuilder code, String op) {
        if (stack.isEmpty()) return "// Stack underflow at unary " + op;
        String val = stack.pop();
        String resultVar = "temp_" + (tempVarCounter++);
        code.append("    auto ").append(resultVar).append(" = ").append(op).append(val).append(";\n");
        stack.push(resultVar);
        return null;
    }

    private String handleConversion(Stack<String> stack, StringBuilder code, String cast) {
        if (stack.isEmpty()) return "// Stack underflow at conversion";
        String val = stack.pop();
        String resultVar = "temp_" + (tempVarCounter++);
        code.append("    auto ").append(resultVar).append(" = ").append(cast).append(val).append(";\n");
        stack.push(resultVar);
        return null;
    }

    private String handleCompare(Stack<String> stack, StringBuilder code, String type) {
        if (stack.size() < 2) return "// Stack underflow at compare";
        String b = stack.pop();
        String a = stack.pop();
        String resultVar = "cmp_" + (tempVarCounter++);
        code.append("    jint ").append(resultVar).append(" = (").append(a)
                .append(" > ").append(b).append(") ? 1 : ((").append(a)
                .append(" < ").append(b).append(") ? -1 : 0);\n");
        stack.push(resultVar);
        return null;
    }

    private String getOrCreateLabel(org.objectweb.asm.Label label, Map<String, String> labels) {
        String labelName = labels.get(label.toString());
        if (labelName == null) {
            labelName = "label_" + (labelCounter++);
            labels.put(label.toString(), labelName);
        }
        return labelName;
    }

    private String handleIfCondition(InstructionInfo insn, Stack<String> stack,
                                     StringBuilder code, Map<String, String> labels) {
        if (stack.isEmpty()) return "// Stack underflow at IF";
        String val = stack.pop();
        String labelName = getOrCreateLabel(insn.label, labels);

        String condition = switch (insn.opcode) {
            case Opcodes.IFEQ -> val + " == 0";
            case Opcodes.IFNE -> val + " != 0";
            case Opcodes.IFLT -> val + " < 0";
            case Opcodes.IFGE -> val + " >= 0";
            case Opcodes.IFGT -> val + " > 0";
            case Opcodes.IFLE -> val + " <= 0";
            default -> "false";
        };

        return "if (" + condition + ") goto " + labelName + ";";
    }

    private String handleIfCompare(InstructionInfo insn, Stack<String> stack,
                                   StringBuilder code, Map<String, String> labels) {
        if (stack.size() < 2) return "// Stack underflow at IF_CMP";
        String b = stack.pop();
        String a = stack.pop();
        String labelName = getOrCreateLabel(insn.label, labels);

        String condition = switch (insn.opcode) {
            case Opcodes.IF_ICMPEQ, Opcodes.IF_ACMPEQ -> a + " == " + b;
            case Opcodes.IF_ICMPNE, Opcodes.IF_ACMPNE -> a + " != " + b;
            case Opcodes.IF_ICMPLT -> a + " < " + b;
            case Opcodes.IF_ICMPGE -> a + " >= " + b;
            case Opcodes.IF_ICMPGT -> a + " > " + b;
            case Opcodes.IF_ICMPLE -> a + " <= " + b;
            default -> "false";
        };

        return "if (" + condition + ") goto " + labelName + ";";
    }
    private String handleGetStatic(InstructionInfo insn, Stack<String> stack, StringBuilder code) {
        String fieldVar = "field_" + (tempVarCounter++);
        String javaClassName = insn.owner;
        int currentTemp = tempVarCounter++;

        code.append("    jclass cls_").append(currentTemp).append(" = env->FindClass(\"")
                .append(javaClassName).append("\");\n");
        code.append("    jobject ").append(fieldVar).append(" = nullptr;\n");
        code.append("    if (cls_").append(currentTemp).append(" == nullptr) {\n");
        code.append("        if (env->ExceptionCheck()) env->ExceptionDescribe();\n");
        code.append("    } else {\n");

        code.append("        jfieldID fid_").append(currentTemp).append(" = env->GetStaticFieldID(cls_")
                .append(currentTemp).append(", \"").append(insn.name)
                .append("\", \"").append(insn.descriptor).append("\");\n");
        code.append("        if (fid_").append(currentTemp).append(" == nullptr) {\n");
        code.append("            if (env->ExceptionCheck()) env->ExceptionDescribe();\n");
        code.append("        } else {\n");

        String getMethod = jniHelper.getStaticFieldMethod(insn.descriptor);
        code.append("            ").append(fieldVar).append(" = env->").append(getMethod)
                .append("(cls_").append(currentTemp).append(", fid_").append(currentTemp).append(");\n");
        code.append("        }\n");
        code.append("    }\n");

        stack.push(fieldVar);
        return null;
    }

    private String handlePutStatic(InstructionInfo insn, Stack<String> stack, StringBuilder code) {
        if (stack.isEmpty()) return "// Stack underflow at PUTSTATIC";
        String value = stack.pop();
        String javaClassName = insn.owner;
        int currentTemp = tempVarCounter++;

        code.append("    jclass cls_").append(currentTemp).append(" = env->FindClass(\"")
                .append(javaClassName).append("\");\n");
        code.append("    jfieldID fid_").append(currentTemp).append(" = env->GetStaticFieldID(cls_")
                .append(currentTemp).append(", \"").append(insn.name)
                .append("\", \"").append(insn.descriptor).append("\");\n");

        String setMethod = jniHelper.setStaticFieldMethod(insn.descriptor);
        code.append("    env->").append(setMethod).append("(cls_").append(currentTemp)
                .append(", fid_").append(currentTemp).append(", ").append(value).append(");\n");

        return null;
    }

    private String handleGetField(InstructionInfo insn, Stack<String> stack, StringBuilder code) {
        if (stack.isEmpty()) return "// Stack underflow at GETFIELD";
        String obj = stack.pop();
        String fieldVar = "field_" + (tempVarCounter++);
        String javaClassName = insn.owner;
        int currentTemp = tempVarCounter++;

        String cppType = typeMapper.mapJavaTypeToCpp(
                typeMapper.parseType(insn.descriptor)
        );
        code.append("    ").append(cppType).append(" ").append(fieldVar)
                .append(" = ").append(typeMapper.getDefaultValue(
                        typeMapper.parseType(insn.descriptor)
                )).append(";\n");

        code.append("    if (").append(obj).append(" != nullptr) {\n");
        code.append("        jclass cls_").append(currentTemp).append(" = env->GetObjectClass(")
                .append(obj).append(");\n");
        code.append("        if (cls_").append(currentTemp).append(" != nullptr) {\n");

        code.append("            jfieldID fid_").append(currentTemp).append(" = env->GetFieldID(cls_")
                .append(currentTemp).append(", \"").append(insn.name)
                .append("\", \"").append(insn.descriptor).append("\");\n");
        code.append("            if (fid_").append(currentTemp).append(" != nullptr) {\n");

        String getMethod = jniHelper.getFieldMethod(insn.descriptor);
        code.append("                ").append(fieldVar).append(" = env->").append(getMethod)
                .append("(").append(obj).append(", fid_").append(currentTemp).append(");\n");
        code.append("            } else if (env->ExceptionCheck()) {\n");
        code.append("                env->ExceptionDescribe();\n");
        code.append("                env->ExceptionClear();\n");
        code.append("            }\n");
        code.append("        }\n");
        code.append("    }\n");

        stack.push(fieldVar);
        return null;
    }

    private String handlePutField(InstructionInfo insn, Stack<String> stack, StringBuilder code) {
        if (stack.size() < 2) return "// Stack underflow at PUTFIELD";
        String value = stack.pop();
        String obj = stack.pop();
        int currentTemp = tempVarCounter++;

        code.append("    jclass cls_").append(currentTemp).append(" = env->GetObjectClass(")
                .append(obj).append(");\n");
        code.append("    jfieldID fid_").append(currentTemp).append(" = env->GetFieldID(cls_")
                .append(currentTemp).append(", \"").append(insn.name)
                .append("\", \"").append(insn.descriptor).append("\");\n");

        String setMethod = jniHelper.setFieldMethod(insn.descriptor);
        code.append("    env->").append(setMethod).append("(").append(obj)
                .append(", fid_").append(currentTemp).append(", ").append(value).append(");\n");

        return null;
    }

    private String handleInvokeVirtual(InstructionInfo insn, Stack<String> stack, StringBuilder code) {
        String[] paramTypes = typeMapper.getParameterTypes(insn.descriptor);
        if (stack.size() < paramTypes.length + 1) return "// Stack underflow at INVOKEVIRTUAL";

        List<String> args = new ArrayList<>();
        for (int i = paramTypes.length - 1; i >= 0; i--) {
            args.add(0, stack.pop());
        }
        String obj = stack.pop();

        String returnType = typeMapper.getReturnType(insn.descriptor);
        int currentTemp = tempVarCounter++;
        boolean isVoidMethod = returnType.equals("void");

        String resultVar = null;
        if (!isVoidMethod) {
            resultVar = "result_" + tempVarCounter++;
            String cppType = typeMapper.mapJavaTypeToCpp(returnType);
            code.append("    ").append(cppType).append(" ").append(resultVar)
                    .append(" = ").append(typeMapper.getDefaultValue(returnType)).append(";\n");
        }

        code.append("    if (").append(obj).append(" != nullptr) {\n");

        code.append("        jclass cls_").append(currentTemp).append(" = env->GetObjectClass(")
                .append(obj).append(");\n");
        code.append("        if (cls_").append(currentTemp).append(" != nullptr) {\n");

        code.append("            jmethodID mid_").append(currentTemp).append(" = env->GetMethodID(cls_")
                .append(currentTemp).append(", \"").append(insn.name)
                .append("\", \"").append(insn.descriptor).append("\");\n");
        code.append("            if (mid_").append(currentTemp).append(" != nullptr) {\n");

        String callMethod = jniHelper.getCallMethod(insn.descriptor, false);

        if (!isVoidMethod) {
            code.append("                ").append(resultVar).append(" = env->").append(callMethod)
                    .append("(").append(obj).append(", mid_").append(currentTemp);
            for (String arg : args) {
                code.append(", ").append(arg);
            }
            code.append(");\n");
            code.append("                if (env->ExceptionCheck()) {\n");
            code.append("                    env->ExceptionDescribe();\n");
            code.append("                    env->ExceptionClear();\n");
            code.append("                    ").append(resultVar).append(" = ")
                    .append(typeMapper.getDefaultValue(returnType)).append(";\n");
            code.append("                }\n");
        } else {
            code.append("                env->").append(callMethod).append("(").append(obj)
                    .append(", mid_").append(currentTemp);
            for (String arg : args) {
                code.append(", ").append(arg);
            }
            code.append(");\n");
            code.append("                if (env->ExceptionCheck()) {\n");
            code.append("                    env->ExceptionDescribe();\n");
            code.append("                    env->ExceptionClear();\n");
            code.append("                }\n");
        }

        code.append("            } else if (env->ExceptionCheck()) {\n");
        code.append("                env->ExceptionDescribe();\n");
        code.append("                env->ExceptionClear();\n");
        code.append("            }\n");
        code.append("        } else if (env->ExceptionCheck()) {\n");
        code.append("            env->ExceptionDescribe();\n");
        code.append("            env->ExceptionClear();\n");
        code.append("        }\n");
        code.append("    }\n");

        if (resultVar != null) {
            stack.push(resultVar);
        }

        return null;
    }

    private String handleInvokeSpecial(InstructionInfo insn, Stack<String> stack, StringBuilder code) {
        String[] paramTypes = typeMapper.getParameterTypes(insn.descriptor);
        if (stack.size() < paramTypes.length + 1) return "// Stack underflow at INVOKESPECIAL";

        List<String> args = new ArrayList<>();
        for (int i = paramTypes.length - 1; i >= 0; i--) {
            args.add(0, stack.pop());
        }
        String obj = stack.pop();

        if (insn.name.equals("<init>")) {
            return "// Constructor call handled by NEW";
        }

        int currentTemp = tempVarCounter++;
        String returnType = typeMapper.getReturnType(insn.descriptor);
        boolean isVoidMethod = returnType.equals("void");

        String resultVar = null;
        if (!isVoidMethod) {
            resultVar = "result_" + tempVarCounter++;
            String cppType = typeMapper.mapJavaTypeToCpp(returnType);
            code.append("    ").append(cppType).append(" ").append(resultVar)
                    .append(" = ").append(typeMapper.getDefaultValue(returnType)).append(";\n");
        }

        code.append("    jclass cls_").append(currentTemp).append(" = env->FindClass(\"")
                .append(insn.owner).append("\");\n");
        code.append("    if (cls_").append(currentTemp).append(" != nullptr) {\n");

        code.append("        jmethodID mid_").append(currentTemp).append(" = env->GetMethodID(cls_")
                .append(currentTemp).append(", \"").append(insn.name)
                .append("\", \"").append(insn.descriptor).append("\");\n");
        code.append("        if (mid_").append(currentTemp).append(" != nullptr) {\n");

        String callMethod = jniHelper.getCallMethod(insn.descriptor, false);

        if (!isVoidMethod) {
            code.append("            ").append(resultVar).append(" = env->").append(callMethod)
                    .append("(").append(obj).append(", mid_").append(currentTemp);
            for (String arg : args) {
                code.append(", ").append(arg);
            }
            code.append(");\n");
            code.append("            if (env->ExceptionCheck()) {\n");
            code.append("                env->ExceptionDescribe();\n");
            code.append("                env->ExceptionClear();\n");
            code.append("            }\n");
        } else {
            code.append("            env->").append(callMethod).append("(").append(obj)
                    .append(", mid_").append(currentTemp);
            for (String arg : args) {
                code.append(", ").append(arg);
            }
            code.append(");\n");
            code.append("            if (env->ExceptionCheck()) {\n");
            code.append("                env->ExceptionDescribe();\n");
            code.append("                env->ExceptionClear();\n");
            code.append("            }\n");
        }

        code.append("        } else if (env->ExceptionCheck()) {\n");
        code.append("            env->ExceptionDescribe();\n");
        code.append("            env->ExceptionClear();\n");
        code.append("        }\n");
        code.append("    } else if (env->ExceptionCheck()) {\n");
        code.append("        env->ExceptionDescribe();\n");
        code.append("        env->ExceptionClear();\n");
        code.append("    }\n");

        if (resultVar != null) {
            stack.push(resultVar);
        }

        return null;
    }

    private String handleInvokeStatic(InstructionInfo insn, Stack<String> stack, StringBuilder code) {
        String[] paramTypes = typeMapper.getParameterTypes(insn.descriptor);
        if (stack.size() < paramTypes.length) return "// Stack underflow at INVOKESTATIC";

        List<String> args = new ArrayList<>();
        for (int i = paramTypes.length - 1; i >= 0; i--) {
            args.add(0, stack.pop());
        }

        String returnType = typeMapper.getReturnType(insn.descriptor);
        int currentTemp = tempVarCounter++;
        boolean isVoidMethod = returnType.equals("void");

        String resultVar = null;
        if (!isVoidMethod) {
            resultVar = "result_" + tempVarCounter++;
            String cppType = typeMapper.mapJavaTypeToCpp(returnType);
            code.append("    ").append(cppType).append(" ").append(resultVar)
                    .append(" = ").append(typeMapper.getDefaultValue(returnType)).append(";\n");
        }

        code.append("    jclass cls_").append(currentTemp).append(" = env->FindClass(\"")
                .append(insn.owner).append("\");\n");
        code.append("    if (cls_").append(currentTemp).append(" != nullptr) {\n");

        code.append("        jmethodID mid_").append(currentTemp).append(" = env->GetStaticMethodID(cls_")
                .append(currentTemp).append(", \"").append(insn.name)
                .append("\", \"").append(insn.descriptor).append("\");\n");
        code.append("        if (mid_").append(currentTemp).append(" != nullptr) {\n");

        String callMethod = jniHelper.getCallMethod(insn.descriptor, true);

        if (!isVoidMethod) {
            code.append("            ").append(resultVar).append(" = env->").append(callMethod)
                    .append("(cls_").append(currentTemp).append(", mid_").append(currentTemp);
            for (String arg : args) {
                code.append(", ").append(arg);
            }
            code.append(");\n");
            code.append("            if (env->ExceptionCheck()) {\n");
            code.append("                env->ExceptionDescribe();\n");
            code.append("                env->ExceptionClear();\n");
            code.append("                ").append(resultVar).append(" = ")
                    .append(typeMapper.getDefaultValue(returnType)).append(";\n");
            code.append("            }\n");
        } else {
            code.append("            env->").append(callMethod).append("(cls_").append(currentTemp)
                    .append(", mid_").append(currentTemp);
            for (String arg : args) {
                code.append(", ").append(arg);
            }
            code.append(");\n");
            code.append("            if (env->ExceptionCheck()) {\n");
            code.append("                env->ExceptionDescribe();\n");
            code.append("                env->ExceptionClear();\n");
            code.append("            }\n");
        }

        code.append("        } else if (env->ExceptionCheck()) {\n");
        code.append("            env->ExceptionDescribe();\n");
        code.append("            env->ExceptionClear();\n");
        code.append("        }\n");
        code.append("    } else if (env->ExceptionCheck()) {\n");
        code.append("        env->ExceptionDescribe();\n");
        code.append("        env->ExceptionClear();\n");
        code.append("    }\n");

        if (resultVar != null) {
            stack.push(resultVar);
        }

        return null;
    }

    private String handleInvokeInterface(InstructionInfo insn, Stack<String> stack, StringBuilder code) {
        return handleInvokeVirtual(insn, stack, code);
    }

    private String handleNew(InstructionInfo insn, Stack<String> stack, StringBuilder code) {
        String objVar = "obj_" + (tempVarCounter++);
        int currentTemp = tempVarCounter++;

        code.append("    jclass cls_").append(currentTemp).append(" = env->FindClass(\"")
                .append(insn.typeDesc).append("\");\n");
        code.append("    jmethodID mid_").append(currentTemp)
                .append(" = env->GetMethodID(cls_").append(currentTemp)
                .append(", \"<init>\", \"()V\");\n");
        code.append("    jobject ").append(objVar).append(" = env->NewObject(cls_")
                .append(currentTemp).append(", mid_").append(currentTemp).append(");\n");
        stack.push(objVar);
        return null;
    }

    private String handleNewArray(InstructionInfo insn, Stack<String> stack, StringBuilder code) {
        if (stack.isEmpty()) return "// Stack underflow at NEWARRAY";
        String size = stack.pop();
        String arrayVar = "arr_" + (tempVarCounter++);

        String arrayType = switch (insn.operand) {
            case 4 -> "Boolean";
            case 5 -> "Char";
            case 6 -> "Float";
            case 7 -> "Double";
            case 8 -> "Byte";
            case 9 -> "Short";
            case 10 -> "Int";
            case 11 -> "Long";
            default -> "Int";
        };

        code.append("    j" + arrayType.toLowerCase() + "Array ").append(arrayVar)
                .append(" = env->New").append(arrayType).append("Array(").append(size).append(");\n");
        stack.push(arrayVar);
        return null;
    }

    private String handleANewArray(InstructionInfo insn, Stack<String> stack, StringBuilder code) {
        if (stack.isEmpty()) return "// Stack underflow at ANEWARRAY";
        String size = stack.pop();
        String arrayVar = "arr_" + (tempVarCounter++);
        int currentTemp = tempVarCounter++;

        code.append("    jclass cls_").append(currentTemp).append(" = env->FindClass(\"")
                .append(insn.typeDesc).append("\");\n");
        code.append("    jobjectArray ").append(arrayVar)
                .append(" = env->NewObjectArray(").append(size).append(", cls_")
                .append(currentTemp).append(", nullptr);\n");
        stack.push(arrayVar);
        return null;
    }

    private String handleArrayLength(Stack<String> stack, StringBuilder code) {
        if (stack.isEmpty()) return "// Stack underflow at ARRAYLENGTH";
        String array = stack.pop();
        String lenVar = "len_" + (tempVarCounter++);
        code.append("    jsize ").append(lenVar).append(" = env->GetArrayLength((jarray)")
                .append(array).append(");\n");
        stack.push(lenVar);
        return null;
    }

    private String handleArrayLoad(int opcode, Stack<String> stack, StringBuilder code) {
        if (stack.size() < 2) return "Stack underflow at ARRAYLOAD";
        String index = stack.pop();
        String array = stack.pop();
        String elemVar = "elem" + (tempVarCounter++);

        // Объявляем переменную ДО if блока
        switch (opcode) {
            case Opcodes.IALOAD:
                code.append("jint " + elemVar + " = 0;\n");
                code.append("if (" + array + " != nullptr) {\n");
                code.append("    jint* arrdata = env->GetIntArrayElements((jintArray)" + array + ", nullptr);\n");
                code.append("    if (arrdata != nullptr) {\n");
                code.append("        " + elemVar + " = arrdata[" + index + "];\n");
                code.append("        env->ReleaseIntArrayElements((jintArray)" + array + ", arrdata, JNI_ABORT);\n");
                code.append("    }\n");
                code.append("}\n");
                break;

            case Opcodes.LALOAD:
                code.append("jlong " + elemVar + " = 0LL;\n");
                code.append("if (" + array + " != nullptr) {\n");
                code.append("    jlong* arrdata = env->GetLongArrayElements((jlongArray)" + array + ", nullptr);\n");
                code.append("    if (arrdata != nullptr) {\n");
                code.append("        " + elemVar + " = arrdata[" + index + "];\n");
                code.append("        env->ReleaseLongArrayElements((jlongArray)" + array + ", arrdata, JNI_ABORT);\n");
                code.append("    }\n");
                code.append("}\n");
                break;

            case Opcodes.FALOAD:
                code.append("jfloat " + elemVar + " = 0.0f;\n");
                code.append("if (" + array + " != nullptr) {\n");
                code.append("    jfloat* arrdata = env->GetFloatArrayElements((jfloatArray)" + array + ", nullptr);\n");
                code.append("    if (arrdata != nullptr) {\n");
                code.append("        " + elemVar + " = arrdata[" + index + "];\n");
                code.append("        env->ReleaseFloatArrayElements((jfloatArray)" + array + ", arrdata, JNI_ABORT);\n");
                code.append("    }\n");
                code.append("}\n");
                break;

            case Opcodes.DALOAD:
                code.append("jdouble " + elemVar + " = 0.0;\n");
                code.append("if (" + array + " != nullptr) {\n");
                code.append("    jdouble* arrdata = env->GetDoubleArrayElements((jdoubleArray)" + array + ", nullptr);\n");
                code.append("    if (arrdata != nullptr) {\n");
                code.append("        " + elemVar + " = arrdata[" + index + "];\n");
                code.append("        env->ReleaseDoubleArrayElements((jdoubleArray)" + array + ", arrdata, JNI_ABORT);\n");
                code.append("    }\n");
                code.append("}\n");
                break;

            case Opcodes.AALOAD:
                code.append("jobject " + elemVar + " = nullptr;\n");
                code.append("if (" + array + " != nullptr) {\n");
                code.append("    " + elemVar + " = env->GetObjectArrayElement((jobjectArray)" + array + ", " + index + ");\n");
                code.append("    if (env->ExceptionCheck()) {\n");
                code.append("        env->ExceptionClear();\n");
                code.append("        " + elemVar + " = nullptr;\n");
                code.append("    }\n");
                code.append("}\n");
                break;

            case Opcodes.BALOAD:
                code.append("jbyte " + elemVar + " = 0;\n");
                code.append("if (" + array + " != nullptr) {\n");
                code.append("    jbyte* arrdata = env->GetByteArrayElements((jbyteArray)" + array + ", nullptr);\n");
                code.append("    if (arrdata != nullptr) {\n");
                code.append("        " + elemVar + " = arrdata[" + index + "];\n");
                code.append("        env->ReleaseByteArrayElements((jbyteArray)" + array + ", arrdata, JNI_ABORT);\n");
                code.append("    }\n");
                code.append("}\n");
                break;

            case Opcodes.CALOAD:
                code.append("jchar " + elemVar + " = 0;\n");
                code.append("if (" + array + " != nullptr) {\n");
                code.append("    jchar* arrdata = env->GetCharArrayElements((jcharArray)" + array + ", nullptr);\n");
                code.append("    if (arrdata != nullptr) {\n");
                code.append("        " + elemVar + " = arrdata[" + index + "];\n");
                code.append("        env->ReleaseCharArrayElements((jcharArray)" + array + ", arrdata, JNI_ABORT);\n");
                code.append("    }\n");
                code.append("}\n");
                break;

            case Opcodes.SALOAD:
                code.append("jshort " + elemVar + " = 0;\n");
                code.append("if (" + array + " != nullptr) {\n");
                code.append("    jshort* arrdata = env->GetShortArrayElements((jshortArray)" + array + ", nullptr);\n");
                code.append("    if (arrdata != nullptr) {\n");
                code.append("        " + elemVar + " = arrdata[" + index + "];\n");
                code.append("        env->ReleaseShortArrayElements((jshortArray)" + array + ", arrdata, JNI_ABORT);\n");
                code.append("    }\n");
                code.append("}\n");
                break;

            default:
                code.append("jint " + elemVar + " = 0;\n");
        }

        stack.push(elemVar);
        return null;
    }

    private String handleArrayStore(int opcode, Stack<String> stack, StringBuilder code) {
        if (stack.size() < 3) return "Stack underflow at ARRAYSTORE";
        String value = stack.pop();
        String index = stack.pop();
        String array = stack.pop();

        code.append("if (" + array + " != nullptr) {\n");

        switch (opcode) {
            case Opcodes.IASTORE:
                code.append("    jint* arrdata = env->GetIntArrayElements((jintArray)" + array + ", nullptr);\n");
                code.append("    if (arrdata != nullptr) {\n");
                code.append("        arrdata[" + index + "] = " + value + ";\n");
                code.append("        env->ReleaseIntArrayElements((jintArray)" + array + ", arrdata, 0);\n");
                code.append("    }\n");
                break;

            case Opcodes.LASTORE:
                code.append("    jlong* arrdata = env->GetLongArrayElements((jlongArray)" + array + ", nullptr);\n");
                code.append("    if (arrdata != nullptr) {\n");
                code.append("        arrdata[" + index + "] = " + value + ";\n");
                code.append("        env->ReleaseLongArrayElements((jlongArray)" + array + ", arrdata, 0);\n");
                code.append("    }\n");
                break;

            case Opcodes.FASTORE:
                code.append("    jfloat* arrdata = env->GetFloatArrayElements((jfloatArray)" + array + ", nullptr);\n");
                code.append("    if (arrdata != nullptr) {\n");
                code.append("        arrdata[" + index + "] = " + value + ";\n");
                code.append("        env->ReleaseFloatArrayElements((jfloatArray)" + array + ", arrdata, 0);\n");
                code.append("    }\n");
                break;

            case Opcodes.DASTORE:
                code.append("    jdouble* arrdata = env->GetDoubleArrayElements((jdoubleArray)" + array + ", nullptr);\n");
                code.append("    if (arrdata != nullptr) {\n");
                code.append("        arrdata[" + index + "] = " + value + ";\n");
                code.append("        env->ReleaseDoubleArrayElements((jdoubleArray)" + array + ", arrdata, 0);\n");
                code.append("    }\n");
                break;

            case Opcodes.AASTORE:
                code.append("    env->SetObjectArrayElement((jobjectArray)" + array + ", " + index + ", " + value + ");\n");
                break;

            case Opcodes.BASTORE:
                code.append("    jbyte* arrdata = env->GetByteArrayElements((jbyteArray)" + array + ", nullptr);\n");
                code.append("    if (arrdata != nullptr) {\n");
                code.append("        arrdata[" + index + "] = " + value + ";\n");
                code.append("        env->ReleaseByteArrayElements((jbyteArray)" + array + ", arrdata, 0);\n");
                code.append("    }\n");
                break;

            case Opcodes.CASTORE:
                code.append("    jchar* arrdata = env->GetCharArrayElements((jcharArray)" + array + ", nullptr);\n");
                code.append("    if (arrdata != nullptr) {\n");
                code.append("        arrdata[" + index + "] = " + value + ";\n");
                code.append("        env->ReleaseCharArrayElements((jcharArray)" + array + ", arrdata, 0);\n");
                code.append("    }\n");
                break;

            case Opcodes.SASTORE:
                code.append("    jshort* arrdata = env->GetShortArrayElements((jshortArray)" + array + ", nullptr);\n");
                code.append("    if (arrdata != nullptr) {\n");
                code.append("        arrdata[" + index + "] = " + value + ";\n");
                code.append("        env->ReleaseShortArrayElements((jshortArray)" + array + ", arrdata, 0);\n");
                code.append("    }\n");
                break;
        }

        code.append("}\n");
        return null;
    }

    private String boxPrimitiveIfNeeded(String value, String javaType, StringBuilder code) {
        switch (javaType) {
            case "int":
                String intBoxVar = "boxed" + (tempVarCounter++);
                int currentTemp = tempVarCounter++;
                code.append("jclass intCls" + currentTemp + " = env->FindClass(\"java/lang/Integer\");\n");
                code.append("jmethodID intCtor" + currentTemp + " = env->GetMethodID(intCls" + currentTemp + ", \"<init>\", \"(I)V\");\n");
                code.append("jobject " + intBoxVar + " = env->NewObject(intCls" + currentTemp + ", intCtor" + currentTemp + ", " + value + ");\n");
                return intBoxVar;

            case "long":
                String longBoxVar = "boxed" + (tempVarCounter++);
                currentTemp = tempVarCounter++;
                code.append("jclass longCls" + currentTemp + " = env->FindClass(\"java/lang/Long\");\n");
                code.append("jmethodID longCtor" + currentTemp + " = env->GetMethodID(longCls" + currentTemp + ", \"<init>\", \"(J)V\");\n");
                code.append("jobject " + longBoxVar + " = env->NewObject(longCls" + currentTemp + ", longCtor" + currentTemp + ", " + value + ");\n");
                return longBoxVar;

            case "float":
                String floatBoxVar = "boxed" + (tempVarCounter++);
                currentTemp = tempVarCounter++;
                code.append("jclass floatCls" + currentTemp + " = env->FindClass(\"java/lang/Float\");\n");
                code.append("jmethodID floatCtor" + currentTemp + " = env->GetMethodID(floatCls" + currentTemp + ", \"<init>\", \"(F)V\");\n");
                code.append("jobject " + floatBoxVar + " = env->NewObject(floatCls" + currentTemp + ", floatCtor" + currentTemp + ", " + value + ");\n");
                return floatBoxVar;

            case "double":
                String doubleBoxVar = "boxed" + (tempVarCounter++);
                currentTemp = tempVarCounter++;
                code.append("jclass doubleCls" + currentTemp + " = env->FindClass(\"java/lang/Double\");\n");
                code.append("jmethodID doubleCtor" + currentTemp + " = env->GetMethodID(doubleCls" + currentTemp + ", \"<init>\", \"(D)V\");\n");
                code.append("jobject " + doubleBoxVar + " = env->NewObject(doubleCls" + currentTemp + ", doubleCtor" + currentTemp + ", " + value + ");\n");
                return doubleBoxVar;

            case "boolean":
                String boolBoxVar = "boxed" + (tempVarCounter++);
                currentTemp = tempVarCounter++;
                code.append("jclass boolCls" + currentTemp + " = env->FindClass(\"java/lang/Boolean\");\n");
                code.append("jmethodID boolCtor" + currentTemp + " = env->GetMethodID(boolCls" + currentTemp + ", \"<init>\", \"(Z)V\");\n");
                code.append("jobject " + boolBoxVar + " = env->NewObject(boolCls" + currentTemp + ", boolCtor" + currentTemp + ", " + value + ");\n");
                return boolBoxVar;

            case "byte":
                String byteBoxVar = "boxed" + (tempVarCounter++);
                currentTemp = tempVarCounter++;
                code.append("jclass byteCls" + currentTemp + " = env->FindClass(\"java/lang/Byte\");\n");
                code.append("jmethodID byteCtor" + currentTemp + " = env->GetMethodID(byteCls" + currentTemp + ", \"<init>\", \"(B)V\");\n");
                code.append("jobject " + byteBoxVar + " = env->NewObject(byteCls" + currentTemp + ", byteCtor" + currentTemp + ", " + value + ");\n");
                return byteBoxVar;

            case "char":
                String charBoxVar = "boxed" + (tempVarCounter++);
                currentTemp = tempVarCounter++;
                code.append("jclass charCls" + currentTemp + " = env->FindClass(\"java/lang/Character\");\n");
                code.append("jmethodID charCtor" + currentTemp + " = env->GetMethodID(charCls" + currentTemp + ", \"<init>\", \"(C)V\");\n");
                code.append("jobject " + charBoxVar + " = env->NewObject(charCls" + currentTemp + ", charCtor" + currentTemp + ", " + value + ");\n");
                return charBoxVar;

            case "short":
                String shortBoxVar = "boxed" + (tempVarCounter++);
                currentTemp = tempVarCounter++;
                code.append("jclass shortCls" + currentTemp + " = env->FindClass(\"java/lang/Short\");\n");
                code.append("jmethodID shortCtor" + currentTemp + " = env->GetMethodID(shortCls" + currentTemp + ", \"<init>\", \"(S)V\");\n");
                code.append("jobject " + shortBoxVar + " = env->NewObject(shortCls" + currentTemp + ", shortCtor" + currentTemp + ", " + value + ");\n");
                return shortBoxVar;

            default:
                return value;
        }
    }

    private String handleThrow(Stack<String> stack, StringBuilder code) {
        if (stack.isEmpty()) return "// Stack underflow at ATHROW";
        String exception = stack.pop();
        return "env->Throw((jthrowable)" + exception + ");";
    }

    private String handleCheckCast(InstructionInfo insn, Stack<String> stack, StringBuilder code) {
        if (stack.isEmpty()) return "// Stack underflow at CHECKCAST";
        return "// CHECKCAST to " + insn.typeDesc;
    }

    private String handleInstanceOf(InstructionInfo insn, Stack<String> stack, StringBuilder code) {
        if (stack.isEmpty()) return "// Stack underflow at INSTANCEOF";
        String obj = stack.pop();
        String resultVar = "instanceof_" + (tempVarCounter++);
        int currentTemp = tempVarCounter++;

        code.append("    jclass cls_").append(currentTemp).append(" = env->FindClass(\"")
                .append(insn.typeDesc).append("\");\n");
        code.append("    jboolean ").append(resultVar).append(" = env->IsInstanceOf(")
                .append(obj).append(", cls_").append(currentTemp).append(");\n");

        stack.push(resultVar);
        return null;
    }

    private String handleTableSwitch(InstructionInfo insn, Stack<String> stack,
                                     StringBuilder code, Map<String, String> labels) {
        if (stack.isEmpty()) return "// Stack underflow at TABLESWITCH";
        String value = stack.pop();

        StringBuilder sw = new StringBuilder();
        sw.append("switch(").append(value).append(") {\n");

        if (insn.switchLabels != null) {
            for (int i = 0; i < insn.switchLabels.size(); i++) {
                String caseLabel = getOrCreateLabel(insn.switchLabels.get(i), labels);
                sw.append("        case ").append(i).append(": goto ").append(caseLabel).append(";\n");
            }
        }

        if (insn.switchDefault != null) {
            String defaultLabel = getOrCreateLabel(insn.switchDefault, labels);
            sw.append("        default: goto ").append(defaultLabel).append(";\n");
        }

        sw.append("    }");
        return sw.toString();
    }

    private String handleLookupSwitch(InstructionInfo insn, Stack<String> stack,
                                      StringBuilder code, Map<String, String> labels) {
        if (stack.isEmpty()) return "// Stack underflow at LOOKUPSWITCH";
        String value = stack.pop();

        StringBuilder sw = new StringBuilder();
        sw.append("switch(").append(value).append(") {\n");

        if (insn.switchLabels != null) {
            for (int i = 0; i < insn.switchLabels.size(); i++) {
                String caseLabel = getOrCreateLabel(insn.switchLabels.get(i), labels);
                sw.append("        case ").append(i).append(": goto ").append(caseLabel).append(";\n");
            }
        }

        if (insn.switchDefault != null) {
            String defaultLabel = getOrCreateLabel(insn.switchDefault, labels);
            sw.append("        default: goto ").append(defaultLabel).append(";\n");
        }

        sw.append("    }");
        return sw.toString();
    }
}