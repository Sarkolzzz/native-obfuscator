package ru.sarkolsss.bytecode;

import org.objectweb.asm.Label;
import java.util.List;

public class InstructionInfo {
    public int opcode;
    public String type;
    public int operand;
    public int var;
    public String typeDesc;
    public String owner;
    public String name;
    public String descriptor;
    public Object constant;
    public int increment;
    public Label label;
    public Label switchDefault;
    public List<Label> switchLabels;
}