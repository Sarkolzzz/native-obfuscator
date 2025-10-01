package ru.sarkolsss.bytecode;

import org.objectweb.asm.tree.*;
import org.objectweb.asm.*;
import java.util.*;

public class MethodBodyAnalyzer {

    public static MethodBodyInfo analyzeMethod(MethodNode method, String className) {
        MethodBodyInfo info = new MethodBodyInfo();
        info.className = className;
        info.methodName = method.name;
        info.descriptor = method.desc;
        info.instructions = new ArrayList<>();

        if (method.instructions == null || method.instructions.size() == 0) {
            return info;
        }

        for (AbstractInsnNode insn : method.instructions) {
            InstructionInfo insnInfo = analyzeInstruction(insn);
            if (insnInfo != null) {
                info.instructions.add(insnInfo);
            }
        }

        return info;
    }

    private static InstructionInfo analyzeInstruction(AbstractInsnNode insn) {
        InstructionInfo info = new InstructionInfo();
        info.opcode = insn.getOpcode();

        if (insn instanceof LabelNode) {
            info.opcode = -1;
            info.type = "LABEL";
            info.label = ((LabelNode) insn).getLabel();
            return info;
        }

        switch (insn.getType()) {
            case AbstractInsnNode.INSN -> info.type = "INSN";
            case AbstractInsnNode.INT_INSN -> {
                info.type = "INT_INSN";
                info.operand = ((IntInsnNode) insn).operand;
            }
            case AbstractInsnNode.VAR_INSN -> {
                info.type = "VAR_INSN";
                info.var = ((VarInsnNode) insn).var;
            }
            case AbstractInsnNode.TYPE_INSN -> {
                info.type = "TYPE_INSN";
                info.typeDesc = ((TypeInsnNode) insn).desc;
            }
            case AbstractInsnNode.FIELD_INSN -> {
                info.type = "FIELD_INSN";
                FieldInsnNode finsn = (FieldInsnNode) insn;
                info.owner = finsn.owner;
                info.name = finsn.name;
                info.descriptor = finsn.desc;
            }
            case AbstractInsnNode.METHOD_INSN -> {
                info.type = "METHOD_INSN";
                MethodInsnNode minsn = (MethodInsnNode) insn;
                info.owner = minsn.owner;
                info.name = minsn.name;
                info.descriptor = minsn.desc;
            }
            case AbstractInsnNode.LDC_INSN -> {
                info.type = "LDC_INSN";
                info.constant = ((LdcInsnNode) insn).cst;
            }
            case AbstractInsnNode.IINC_INSN -> {
                info.type = "IINC_INSN";
                IincInsnNode iinsn = (IincInsnNode) insn;
                info.var = iinsn.var;
                info.increment = iinsn.incr;
            }
            case AbstractInsnNode.JUMP_INSN -> {
                info.type = "JUMP_INSN";
                info.label = ((JumpInsnNode) insn).label.getLabel();
            }
            case AbstractInsnNode.TABLESWITCH_INSN -> {
                info.type = "TABLESWITCH_INSN";
                TableSwitchInsnNode tsinsn = (TableSwitchInsnNode) insn;
                info.switchDefault = tsinsn.dflt.getLabel();
                info.switchLabels = new ArrayList<>();
                for (LabelNode ln : tsinsn.labels) {
                    info.switchLabels.add(ln.getLabel());
                }
            }
            case AbstractInsnNode.LOOKUPSWITCH_INSN -> {
                info.type = "LOOKUPSWITCH_INSN";
                LookupSwitchInsnNode lsinsn = (LookupSwitchInsnNode) insn;
                info.switchDefault = lsinsn.dflt.getLabel();
                info.switchLabels = new ArrayList<>();
                for (LabelNode ln : lsinsn.labels) {
                    info.switchLabels.add(ln.getLabel());
                }
            }
            case AbstractInsnNode.LINE -> {
                return null;
            }
            case AbstractInsnNode.FRAME -> {
                return null;
            }
            default -> {
                return null;
            }
        }

        return info;
    }
}
