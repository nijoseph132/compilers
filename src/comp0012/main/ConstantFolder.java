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

            boolean changed;
            do {
                changed = simpleFolding(il, cp);
                il.setPositions();
            } while (changed);

            mg.setMaxStack();
            mg.setMaxLocals();
            mg.stripAttributes(true);
            cg.replaceMethod(method, mg.getMethod());
        }

        cg.setConstantPool(cp);
        cg.setMajor(50);  
        this.optimized = cg.getJavaClass();
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

				InstructionHandle newHandle = il.insert(match[0], replacement);

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

	private void constantFolding(InstructionList il, ConstantPoolGen cpgen) {
		Map<Integer, Object> constantVars = new HashMap<>();
		Map<Integer, InstructionHandle> lastStore = new HashMap<>();

		for (InstructionHandle ih = il.getStart(); ih != null; ih = ih.getNext()) {
			Instruction inst = ih.getInstruction();

			if (inst instanceof StoreInstruction) {
				int varIndex = ((StoreInstruction) inst).getIndex();
				lastStore.put(varIndex, ih);
				
				InstructionHandle prevIh = ih.getPrev();
				Object constantValue = null;

				if (prevIh != null) {
					Instruction prevInst = prevIh.getInstruction();

					if (prevInst instanceof ConstantPushInstruction) {
						constantValue = ((ConstantPushInstruction) prevInst).getValue().intValue();
					} else if (prevInst instanceof LDC || prevInst instanceof LDC2_W) {
						int index;
						if (prevInst instanceof LDC) {
							index = ((LDC) prevInst).getIndex();
						} else {
							index = ((LDC2_W) prevInst).getIndex();
						}
						
						Constant c = cpgen.getConstant(index);
						if (c instanceof ConstantInteger) {
							constantValue = ((ConstantInteger) c).getBytes();
						} else if (c instanceof ConstantLong) {
							constantValue = ((ConstantLong) c).getBytes();
						} else if (c instanceof ConstantFloat) {
							constantValue = ((ConstantFloat) c).getBytes();
						} else if (c instanceof ConstantDouble) {
							constantValue = ((ConstantDouble) c).getBytes();
						}
					} else if (prevInst instanceof ICONST) {
						constantValue = ((ICONST) prevInst).getValue().intValue();
					} else if (prevInst instanceof LCONST) {
						constantValue = ((LCONST) prevInst).getValue().longValue();
					} else if (prevInst instanceof FCONST) {
						constantValue = ((FCONST) prevInst).getValue().floatValue();
					} else if (prevInst instanceof DCONST) {
						constantValue = ((DCONST) prevInst).getValue().doubleValue();
					}
				}

				if (constantValue != null) {
					if (constantVars.containsKey(varIndex)) {
						constantVars.remove(varIndex);
					} else {
						constantVars.put(varIndex, constantValue);
					}
				} else {
					constantVars.remove(varIndex);
				}
			}
		}

		// Second pass to remove non-constant declarations
		Map<Integer, Object> verifiedConstants = new HashMap<>();
		for (Map.Entry<Integer, Object> entry : constantVars.entrySet()) {
			int varIndex = entry.getKey();
			InstructionHandle storeIh = lastStore.get(varIndex);
			boolean isModified = false;
			
			for (InstructionHandle ih = storeIh.getNext(); ih != null; ih = ih.getNext()) {
				Instruction i = ih.getInstruction();
				if (i instanceof StoreInstruction && ((StoreInstruction) i).getIndex() == varIndex) {
					isModified = true;
					break;
				}
			}
			
			if (!isModified) {
				verifiedConstants.put(varIndex, entry.getValue());
			}
		}

		// Fold all found constants
		replaceLoadsWithConstants(il, cpgen, verifiedConstants);

		// Finally, remove initial variable declaration
		removeInitialDeclarations(il, verifiedConstants);
	}

	private void replaceLoadsWithConstants(InstructionList il, ConstantPoolGen cpgen, Map<Integer, Object> constantVars) {
		InstructionFactory factory = new InstructionFactory(cpgen);
		Map<InstructionHandle, Instruction> newInstructions = new HashMap<>();

		for (InstructionHandle ih = il.getStart(); ih != null; ih = ih.getNext()) {
			Instruction inst = ih.getInstruction();
			if (inst instanceof LoadInstruction) {
				int varIndex = ((LoadInstruction) inst).getIndex();
				if (constantVars.containsKey(varIndex)) {
					Instruction constInst = factory.createConstant(constantVars.get(varIndex));
					newInstructions.put(ih, constInst);
				}
			}
		}

		for (Map.Entry<InstructionHandle, Instruction> entry : newInstructions.entrySet()) {
			InstructionHandle handle = entry.getKey();
			Instruction newInstr = entry.getValue();
		
			try {
				il.insert(handle, newInstr);
				il.delete(handle);
			} catch (Exception ignored) {}
		}
	}

	private void removeInitialDeclarations(InstructionList il, Map<Integer, Object> verifiedConstants) {
		Set<InstructionHandle> toRemove = new HashSet<>();
	
		for (InstructionHandle ih = il.getStart(); ih != null; ih = ih.getNext()) {
			Instruction inst = ih.getInstruction();
	
			if (inst instanceof StoreInstruction) {
				int varIndex = ((StoreInstruction) inst).getIndex();
	
				// Check if this is a constant variable we've folded
				if (verifiedConstants.containsKey(varIndex)) {
					// Look at the previous instruction to see if it's pushing a constant
					InstructionHandle prevIh = ih.getPrev();
					if (prevIh != null) {
						Instruction prevInst = prevIh.getInstruction();
	
						// Check if previous instruction is pushing a constant (not a load)
						if (isConstantPush(prevInst)) {
							// Mark both the constant push and the store for removal
							toRemove.add(prevIh);
							toRemove.add(ih);
						}
					}
				}
			}
		}
	
		// Actually remove the marked instructions
		for (InstructionHandle ih : toRemove) {
			try {
				il.delete(ih);
			} catch (TargetLostException e) {
				// Handle exception if needed
			}
		}
	}
	
	// Helper to check if an instruction pushes a constant
	private boolean isConstantPush(Instruction inst) {
		return inst instanceof ConstantPushInstruction ||
			   inst instanceof LDC ||
			   inst instanceof LDC2_W ||
			   inst instanceof ICONST ||
			   inst instanceof LCONST ||
			   inst instanceof FCONST ||
			   inst instanceof DCONST;
	}
}
