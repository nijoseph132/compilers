package comp0012.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantDouble;
import org.apache.bcel.classfile.ConstantFloat;
import org.apache.bcel.classfile.ConstantInteger;
import org.apache.bcel.classfile.ConstantLong;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;

public class ConstantFolder {
    JavaClass original;
    JavaClass optimized;
	boolean modificationsMade;

    public ConstantFolder(String classFilePath) {
        try {
            ClassParser parser = new ClassParser(classFilePath);
            this.original = parser.parse();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Writes the optimized class file to disk.
     * Expected input: output file path.
     * Expected outcome: optimized bytecode written to the given path.
     */
    public void write(String outputPath) {
        this.optimize();
        try (FileOutputStream out = new FileOutputStream(new File(outputPath))) {
            this.optimized.dump(out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	// Runs simple constant folding optimization on all methods in the class.
    public void optimize() {
        ClassGen cg = new ClassGen(original);
        ConstantPoolGen cp = cg.getConstantPool();

        for (Method method : cg.getMethods()) {
            MethodGen mg = new MethodGen(method, cg.getClassName(), cp);
            mg.removeNOPs();
            InstructionList il = mg.getInstructionList();
            if (il == null) continue;

            runPeepholeOptimisation(il, cp);

            mg.setMaxStack();
            mg.setMaxLocals();
            mg.stripAttributes(true);
            cg.replaceMethod(method, mg.getMethod());
        }

        cg.setConstantPool(cp);
        cg.setMajor(50);  
        this.optimized = cg.getJavaClass();
    }

	/*
	 * Loops over the code making any optimisations until no more can be made
	 */
	public void runPeepholeOptimisation(InstructionList il, ConstantPoolGen cp) {
		// Perform SimpleFolding on constant expressions
		boolean changed;
		do {
			modificationsMade = false;

			// Perform simple folding
			do {
				changed = simpleFolding(il, cp);
			} while (changed);

			// Perform constant folding
			constantFolding(il, cp);
			il.setPositions();
			
		} while (modificationsMade);
	}

    /**
     * Performs one pass of constant folding over a method's instruction list.
     * Matches patterns of the form: PushInstruction PushInstruction ArithmeticInstruction.
     * Expected outcome: e.g., 'ldc 2', 'ldc 3', 'iadd' → replaced with 'ldc 5'.
     */
    private boolean simpleFolding(InstructionList il, ConstantPoolGen cp) {
		boolean changed = false;
		InstructionFinder finder = new InstructionFinder(il);
		String pattern = "PushInstruction PushInstruction ArithmeticInstruction";

		for (Iterator<?> it = finder.search(pattern); it.hasNext(); ) {
			InstructionHandle[] match = (InstructionHandle[]) it.next();
			Instruction i1 = match[0].getInstruction();
			Instruction i2 = match[1].getInstruction();
			Instruction op = match[2].getInstruction();

			Number n1 = getValue(i1, cp);
			Number n2 = getValue(i2, cp);
			if (n1 == null || n2 == null) continue;

			try {
				Instruction replacement = fold(op, n1, n2, cp);
				if (replacement == null) continue;

				il.insert(match[0], replacement);
				try {
					il.delete(match[0]);
				} catch (TargetLostException e) {
					e.printStackTrace();
				}

				try {
					il.delete(match[1]);
				} catch (TargetLostException e) {
					e.printStackTrace();
				}

				try {
					il.delete(match[2]);
				} catch (TargetLostException e) {
					e.printStackTrace();
				}

				changed = true;
			} catch (ArithmeticException e) {
				System.out.println("Division by zero skipped.");
			}
		}
		return changed;
	}


    /**
     * Extracts constant values from supported instructions (ICONST, BIPUSH, SIPUSH, LDC, LDC2_W).
     * Expected input: an instruction and the constant pool.
     * Expected outcome: the Number it pushes to the stack, or null if unsupported.
     */
    private Number getValue(Instruction inst, ConstantPoolGen cp) {
        if (inst instanceof ConstantPushInstruction) {
            return ((ConstantPushInstruction) inst).getValue();
        } else if (inst instanceof LDC) {
            Object v = ((LDC) inst).getValue(cp);
            return (v instanceof Number) ? (Number) v : null;
        } else if (inst instanceof LDC2_W) {
            Object v = ((LDC2_W) inst).getValue(cp);
            return (v instanceof Number) ? (Number) v : null;
        }
        return null;
    }

    /**
     * Computes the result of applying an arithmetic operation to two constants,
     * and returns the appropriate BCEL instruction to push the result.
     * Supported operations: +, -, *, /, % for int, long, float, double.
     * Expected outcome: new instruction to replace the operation sequence.
     */
    private Instruction fold(Instruction op, Number a, Number b, ConstantPoolGen cp) {
        if (op instanceof IADD)
            return new LDC(cp.addInteger(a.intValue() + b.intValue()));
        if (op instanceof ISUB)
            return new LDC(cp.addInteger(a.intValue() - b.intValue()));
        if (op instanceof IMUL)
            return new LDC(cp.addInteger(a.intValue() * b.intValue()));
        if (op instanceof IDIV) {
            if (b.intValue() == 0) throw new ArithmeticException();
            return new LDC(cp.addInteger(a.intValue() / b.intValue()));
        }
        if (op instanceof IREM) {
            if (b.intValue() == 0) throw new ArithmeticException();
            return new LDC(cp.addInteger(a.intValue() % b.intValue()));
        }

        if (op instanceof LADD)
            return new LDC2_W(cp.addLong(a.longValue() + b.longValue()));
        if (op instanceof LSUB)
            return new LDC2_W(cp.addLong(a.longValue() - b.longValue()));
        if (op instanceof LMUL)
            return new LDC2_W(cp.addLong(a.longValue() * b.longValue()));
        if (op instanceof LDIV) {
            if (b.longValue() == 0L) throw new ArithmeticException();
            return new LDC2_W(cp.addLong(a.longValue() / b.longValue()));
        }
        if (op instanceof LREM) {
            if (b.longValue() == 0L) throw new ArithmeticException();
            return new LDC2_W(cp.addLong(a.longValue() % b.longValue()));
        }

        if (op instanceof FADD)
            return new LDC(cp.addFloat(a.floatValue() + b.floatValue()));
        if (op instanceof FSUB)
            return new LDC(cp.addFloat(a.floatValue() - b.floatValue()));
        if (op instanceof FMUL)
            return new LDC(cp.addFloat(a.floatValue() * b.floatValue()));
        if (op instanceof FDIV) {
            if (b.floatValue() == 0.0f) throw new ArithmeticException();
            return new LDC(cp.addFloat(a.floatValue() / b.floatValue()));
        }
        if (op instanceof FREM) {
            if (b.floatValue() == 0.0f) throw new ArithmeticException();
            return new LDC(cp.addFloat(a.floatValue() % b.floatValue()));
        }

        if (op instanceof DADD)
            return new LDC2_W(cp.addDouble(a.doubleValue() + b.doubleValue()));
        if (op instanceof DSUB)
            return new LDC2_W(cp.addDouble(a.doubleValue() - b.doubleValue()));
        if (op instanceof DMUL)
            return new LDC2_W(cp.addDouble(a.doubleValue() * b.doubleValue()));
        if (op instanceof DDIV) {
            if (b.doubleValue() == 0.0d) throw new ArithmeticException();
            return new LDC2_W(cp.addDouble(a.doubleValue() / b.doubleValue()));
        }
        if (op instanceof DREM) {
            if (b.doubleValue() == 0.0d) throw new ArithmeticException();
            return new LDC2_W(cp.addDouble(a.doubleValue() % b.doubleValue()));
        }

        return null;
    }

	/*
	 * Performs constant folding by finding all variables with constant declarations
	 * that are not reassigned. Then replaces all references to that variable with the
	 * constant value itself before removing the variable declaration fully.
	 */
	private void constantFolding(InstructionList il, ConstantPoolGen cpgen) {
		Map<Integer, Object> constantVars = new HashMap<>();
	
		// Find all variable declarations with constant values
		for (InstructionHandle ih = il.getStart(); ih != null; ih = ih.getNext()) {
			Instruction inst = ih.getInstruction();
			if (inst instanceof StoreInstruction) {
				int varIndex = ((StoreInstruction) inst).getIndex();
				Object constantValue = getConstantValue(ih.getPrev(), cpgen);
	
				if (constantValue != null) {
					constantVars.compute(varIndex, (k, v) -> v == null ? constantValue : null);
				} else {
					constantVars.remove(varIndex);
				}
			}
		}
	
		InstructionFactory factory = new InstructionFactory(cpgen);
		Map<InstructionHandle, Instruction> replacements = new HashMap<>();
		Set<InstructionHandle> toRemove = new HashSet<>();
	
		// Determine bytecode modifications for constant variables
		for (InstructionHandle ih = il.getStart(); ih != null; ih = ih.getNext()) {
			Instruction inst = ih.getInstruction();
	
			if (inst instanceof LoadInstruction) {
				int varIndex = ((LoadInstruction) inst).getIndex();
				if (constantVars.containsKey(varIndex)) {
					replacements.put(ih, factory.createConstant(constantVars.get(varIndex)));
				}
			} else if (inst instanceof StoreInstruction) {
				int varIndex = ((StoreInstruction) inst).getIndex();
				if (constantVars.containsKey(varIndex) && isConstantPush(ih.getPrev())) {
					toRemove.add(ih.getPrev());
					toRemove.add(ih);
				}
			}
		}
	
		// Make add determined changes
		replacements.forEach((handle, newInstr) -> {
			try {
				il.insert(handle, newInstr);
				il.delete(handle);
				modificationsMade = true;
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	
		toRemove.forEach(handle -> {
			try {
				il.delete(handle);
				modificationsMade = true;
			} catch (TargetLostException e) {
				e.printStackTrace();
			}
		});
	}
	
	private Object getConstantValue(InstructionHandle prevIh, ConstantPoolGen cpgen) {
		if (prevIh == null) return null;
		Instruction prevInst = prevIh.getInstruction();
	
		if (prevInst instanceof ConstantPushInstruction) {
			return ((ConstantPushInstruction) prevInst).getValue();
		} else if (prevInst instanceof LDC) {
			return getConstantValue(cpgen.getConstant(((LDC) prevInst).getIndex()));
		} else if (prevInst instanceof LDC2_W) {
			return getConstantValue(cpgen.getConstant(((LDC2_W) prevInst).getIndex()));
		} else if (prevInst instanceof ICONST) {
			return ((ICONST) prevInst).getValue();
		} else if (prevInst instanceof LCONST) {
			return ((LCONST) prevInst).getValue();
		} else if (prevInst instanceof FCONST) {
			return ((FCONST) prevInst).getValue();
		} else if (prevInst instanceof DCONST) {
			return ((DCONST) prevInst).getValue();
		}
		return null;
	}
	
	private Object getConstantValue(Constant c) {
		if (c instanceof ConstantInteger) return ((ConstantInteger) c).getBytes();
		if (c instanceof ConstantLong) return ((ConstantLong) c).getBytes();
		if (c instanceof ConstantFloat) return ((ConstantFloat) c).getBytes();
		if (c instanceof ConstantDouble) return ((ConstantDouble) c).getBytes();
		return null;
	}
		
	private boolean isConstantPush(InstructionHandle ih) {
		if (ih == null) return false;
		Instruction inst = ih.getInstruction();
		return inst instanceof ConstantPushInstruction ||
			   inst instanceof LDC ||
			   inst instanceof LDC2_W ||
			   inst instanceof ICONST ||
			   inst instanceof LCONST ||
			   inst instanceof FCONST ||
			   inst instanceof DCONST;
	}
}
