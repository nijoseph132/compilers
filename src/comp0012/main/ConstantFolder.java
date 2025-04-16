package comp0012.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.ArrayList;

import org.apache.bcel.Constants;
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

    /**
      * Constructs a ConstantFolder by parsing the given class file.
      * This constructor reads the .class file located at the specified path,
      * and initializes the internal representation of the original class.
      *
      * @param classFilePath the path to the input .class file
      */
    public ConstantFolder(String classFilePath) {
        try {
            ClassParser parser = new ClassParser(classFilePath);
            this.original = parser.parse();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
      * Writes the optimized class file to disk. This method first triggers the optimization process,
      * then writes the resulting optimized bytecode to the specified file path.
      *
      * @param outputPath the destination file path where the optimized class should be saved
      */
    public void write(String outputPath) {
        this.optimize();
        try (FileOutputStream out = new FileOutputStream(new File(outputPath))) {
            this.optimized.dump(out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Runs simple constant folding optimization on all methods in the class.
     */
    public void optimize() {
        ClassGen cg = new ClassGen(original);
        ConstantPoolGen cp = cg.getConstantPool();

        for (Method method : cg.getMethods()) {
            MethodGen mg = new MethodGen(method, cg.getClassName(), cp);
            mg.removeNOPs();
            InstructionList il = mg.getInstructionList();
            if (il == null) continue;

            System.out.println(cg.getClassName() + ": " + method.getName());
            System.out.println("Original: " + il.size() + " instructions");
            System.out.println(il);

            runPeepholeOptimisation(il, cp, mg);

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
     * Loops over the code making any optimisations until no more can be made
     * 
     * @param il The instruction list of instructions to be optimised
     * @param cp BCEL constant pool gen
     * @param mg The method generator associated with the instruction list
     */
	public void runPeepholeOptimisation(InstructionList il, ConstantPoolGen cp, MethodGen mg) {
        int iterations = 1;
		boolean changed;
        do {
            ArrayList<InstructionHandle> loopBounds = getLoopBounds(il);
            ArrayList<InstructionHandle> branchBounds = getBranchBounds(il, loopBounds);
            
            changed = false;
            // Remove conversion instructions
            changed |= removeConversionInstructions(il, cp, loopBounds);
            // Perform dynamic folding
            changed |= dynamicFolding(il, mg, loopBounds, branchBounds);
            // Perform constant folding
            changed |= constantFolding(il, cp, loopBounds, branchBounds);
            // Perform simple folding
            changed |= simpleFolding(il, cp, loopBounds);
            // Perform dead branch deletion
            changed |= deadBranchDeletion(il, cp, loopBounds);
            // Perform dead variable deletion
            changed |= deadVariableDeletion(il, cp, loopBounds);
            ++iterations;
        } while (changed);
        il.setPositions(true);
        System.out.println("Went through iterations: " + (iterations - 1));
        System.out.println("After dead code: " + il.size() + " instructions");
        System.out.println(il);
	}

    /**
      * Safely deletes an instruction handle from the instruction list, updating any jump targets
      * that previously pointed to the deleted instruction to point to a new target.
      *
      * @param il The instruction list of instructions
      * @param handle The instruction handle to delete
      * @param newJumpLabel The instruction handle to redirect any lost jump targets to
      */
    private void safeDelete(InstructionList il, InstructionHandle handle, InstructionHandle newJumpLabel){
        // InstructionHandle nextHandle = handle.getNext();
        try {
            il.delete(handle);
        } catch (TargetLostException e) {
            // TargetLostException raised if handle is targeted by GOTO
            for (InstructionHandle target : e.getTargets()) {
                for (InstructionTargeter targeter : target.getTargeters()) targeter.updateTarget(target, newJumpLabel);
            }
        }
    }

    /**
      * Removes store instructions to variables that are never subsequently loaded, along with contributing instructions.
      *
      * @param il The instruction list of instructions
      * @param cp BCEL constant pool get
      * @param loopBounds Instruction handles marking the bounds of loops to avoid incorrect deletions
      * @return Returns true if any instructions were removed and false otherwise
      */
    private boolean deadVariableDeletion(InstructionList il, ConstantPoolGen cp, ArrayList<InstructionHandle> loopBounds) {
        boolean modificationsMade = false;

        // find which vairables are never used after a store
        HashMap<Integer, Boolean> isUsed = new HashMap<>();
        for (InstructionHandle ih = il.getStart(); ih != null;) {
            InstructionHandle next = ih.getNext();
            Instruction inst = ih.getInstruction();
            if (inst instanceof StoreInstruction) {
                isUsed.put(((StoreInstruction)inst).getIndex(), false);
            } else if (inst instanceof LoadInstruction) {
                isUsed.put(((LoadInstruction)inst).getIndex(), true);
            }
            ih = next;
        }

        // delete them and any instructions that "contribute" to it
        for (InstructionHandle ih = il.getStart(); ih != null;) {
            InstructionHandle next = ih.getNext();
            Instruction inst = ih.getInstruction();
            if (inst instanceof StoreInstruction) {
                int variableIndex = ((StoreInstruction)inst).getIndex();
                if (!isUsed.get(variableIndex)) {
                    boolean skip = false;
                    ArrayList<InstructionHandle> toDelete = new ArrayList<>();
                    int counter = 1;
                    toDelete.add(ih);
                    InstructionHandle currentIh = ih;
                    while (counter > 0 && currentIh != null) {
                        currentIh = currentIh.getPrev();
                        counter--;
                        Instruction current = currentIh.getInstruction();
                        if (current instanceof ArithmeticInstruction) {
                            if (current instanceof INEG || current instanceof LNEG || current instanceof FNEG || current instanceof DNEG) {
                                counter += 1;
                            } else {
                                counter += 2;
                            }
                        } else if (current instanceof InvokeInstruction) {
                            String signature = ((InvokeInstruction)current).getSignature(cp);
                            int numArgs = Type.getArgumentTypes(signature).length;
                            counter += numArgs + 1;
                        } else if (current instanceof ConversionInstruction) {
                            counter += 1;
                        } else if (current instanceof IINC) {
                            // annoying
                            skip = true;
                            counter = 0;
                        }
                        toDelete.add(currentIh);
                    }
                    
                    if (!skip) {
                        for (InstructionHandle a : toDelete) {
                            safeDelete(il, a, next);
                        }
                    }
                }
            }

            ih = next;
        }
        return modificationsMade;
    }

    /**
      * Performs dead branch elimination by evaluating constant comparisons and removing unreachable code.
      *
      * @param il The instruction list of instructions
      * @param cp BCEL constant pool get
      * @param loopBounds Instruction handles marking the bounds of loops to avoid incorrect deletions
      * @return Returns true if any branches or instructions were removed and false otherwise
      */
    private boolean deadBranchDeletion(InstructionList il, ConstantPoolGen cp, ArrayList<InstructionHandle> loopBounds) {
        boolean modificationsMade = false;
        int comparisonResult = 2;
        for (InstructionHandle ih = il.getStart(); ih != null;) {
            InstructionHandle next = ih.getNext();
            Instruction inst = ih.getInstruction();
            if (inst instanceof LCMP) {
                // LCMP should be followed by a IfInstruction
                // find the two previous constants
                InstructionHandle prevIh = ih.getPrev();
                InstructionHandle prevPrevIh = (prevIh != null) ? ih.getPrev().getPrev() : null;
                Long a = (prevIh != null && prevPrevIh != null) ? (Long) getValue(ih.getPrev().getPrev().getInstruction(), cp) : null;
                Long b = (prevIh != null) ? (Long) getValue(ih.getPrev().getInstruction(), cp) : null;
                // do nothing if either one is not a constant
                if (a != null && b != null && isConstantLoad(prevIh) && isConstantLoad(prevPrevIh)) {
                    if (a > b) {
                        comparisonResult = 1;
                    } else if (a < b) {
                        comparisonResult = -1;
                    } else {
                        comparisonResult = 0;
                    }

                    // can delete the two loads and the LCMP
                    modificationsMade = true;
                    safeDelete(il, ih.getPrev().getPrev(), next);
                    safeDelete(il, ih.getPrev(), next);
                    safeDelete(il, ih, next);
                }
            } else if (inst instanceof IfInstruction) {
                boolean skip = false;
                // if comparisonResult hasn't been set by a previous LCMP
                Number a;
                Number b;
                if (comparisonResult == 2) {
                    if (inst instanceof  IFLE || inst instanceof IFLT || inst instanceof IFGE || inst instanceof IFGT || inst instanceof IFEQ || inst instanceof IFNE) {
                        InstructionHandle prevIh = ih.getPrev();
                        InstructionHandle prevPrevIh = (prevIh != null) ? ih.getPrev().getPrev() : null;
                        a = (ih.getPrev() != null) ? getValue(ih.getPrev().getInstruction(), cp) : null;
                        b = 0;
                        if (a == null || !isConstantLoad(prevIh) || !isConstantLoad(prevPrevIh)) skip = true;
                        if (!skip) {
                            modificationsMade = true;
                            safeDelete(il, ih.getPrev(), ih);
                        }
                    } else {
                        InstructionHandle prevIh = ih.getPrev();
                        a = (ih.getPrev() != null && ih.getPrev().getPrev() != null) ? getValue(ih.getPrev().getPrev().getInstruction(), cp) : null;
                        b = (ih.getPrev() != null) ? getValue(ih.getPrev().getInstruction(), cp) : null;
                        if (a == null || b == null || !isConstantLoad(prevIh)) skip = true;
                        if (!skip) {
                            modificationsMade = true;
                            safeDelete(il, ih.getPrev().getPrev(), ih);
                            safeDelete(il, ih.getPrev(), ih);
                        }
                    }
                    if (!skip) {
                        if (a.intValue() > b.intValue()) {
                            comparisonResult = 1;
                        } else if (a.intValue() < b.intValue()) {
                            comparisonResult = -1;
                        } else {
                            comparisonResult = 0;
                        }
                    }
                }
                if (skip) {
                    ih = next;
                    continue;
                }

                boolean branchResult = false;
                if (inst instanceof IFLE || inst instanceof IF_ICMPLE) {
                    branchResult = comparisonResult <= 0;
                } else if (inst instanceof IFGE || inst instanceof IF_ICMPGE) {
                    branchResult = comparisonResult >= 0;
                } else if (inst instanceof IFEQ || inst instanceof IF_ICMPEQ) {
                    branchResult = comparisonResult == 0;
                } else if (inst instanceof IFLT || inst instanceof IF_ICMPLT) {
                    branchResult = comparisonResult < 0;
                } else if (inst instanceof IFGT || inst instanceof IF_ICMPGT) {
                    branchResult = comparisonResult > 0;
                } else if (inst instanceof IFNE || inst instanceof IF_ICMPNE) {
                    branchResult = comparisonResult != 0;
                } else {
                    // shouldn't reach here, but just in case
                    skip = true;
                }

                if (skip) {
                    ih = next;
                    continue;
                }
                InstructionHandle deleteStart;
                InstructionHandle deleteEnd;
                InstructionHandle replacementTarget;

                InstructionHandle ifTargetHandle = ((IfInstruction)ih.getInstruction()).getTarget();
                if (branchResult) {
                    deleteStart = ih.getNext();
                    deleteEnd = ifTargetHandle.getPrev();
                    replacementTarget = ifTargetHandle;
                    next = ifTargetHandle;
                } else {
                    if (ifTargetHandle.getPrev().getInstruction() instanceof GotoInstruction && !loopBounds.contains(ifTargetHandle.getPrev())) {
                        InstructionHandle goToTargetHandle = ((GotoInstruction) ifTargetHandle.getPrev().getInstruction()).getTarget();
                        deleteStart = ifTargetHandle.getPrev();
                        deleteEnd = goToTargetHandle.getPrev();
                        replacementTarget = goToTargetHandle;
                    } else {
                        deleteStart = null;
                        deleteEnd = null;
                        replacementTarget = next;
                    }
                }
                
                // i got nullpointer exception when deleting (branchResult == false) blocks
                // storing them in a list first worked
                modificationsMade = true;
                List<InstructionHandle> toDelete = new ArrayList<>();
                for (InstructionHandle current = deleteStart; current != null && current != deleteEnd.getNext(); current = current.getNext()) {
                    toDelete.add(current);
                }
                for (InstructionHandle h : toDelete) {
                    safeDelete(il, h, replacementTarget);
                }
                safeDelete(il, ih, replacementTarget);
                // reset to "null" state
                comparisonResult = 2;
            }
            ih = next;
        }
        return modificationsMade;
    }

    /**
      * Performs dynamic variable folding by remapping variable reassignment outside loop and branch bounds
      * to use single use variables only.
      *
      * @param il The instruction list to modify
      * @param mg The method generator associated with the instruction list
      * @param loopBounds Instruction handles marking the bounds of any loops
      * @param branchBounds Instruction handles marking the bounds of any branches
      * @return Returns true if any modifications were made, otherwise, returns false
      */
    private boolean dynamicFolding(InstructionList il, MethodGen mg, ArrayList<InstructionHandle> loopBounds, ArrayList<InstructionHandle> branchBounds) {
        boolean modificationsMade = false;
        int maxLocals = mg.getMaxLocals();
        HashMap<Integer, Integer> varMap = new HashMap<>();
        
        for (InstructionHandle ih = il.getStart(); ih != null;) {
            InstructionHandle next = ih.getNext();
            Instruction inst = ih.getInstruction();
    
            if (inst instanceof StoreInstruction store && !variableChangesInBounds(ih, loopBounds) && !variableChangesInBounds(ih, branchBounds)) {
                int varIndex = store.getIndex();
                int newVarIndex;
    
                if (varMap.containsKey(varIndex)) {
                    newVarIndex = ++maxLocals;
                    modificationsMade = true;
                } else {
                    newVarIndex = varIndex;
                }
                
                varMap.put(varIndex, newVarIndex);
                store.setIndex(newVarIndex);
            } else if (inst instanceof LoadInstruction load) {
                int varIndex = load.getIndex();
                if (varMap.containsKey(varIndex)) {
                    int newVarIndex = varMap.get(varIndex);
                    load.setIndex(newVarIndex);
                }
            }
            ih = next;
        }
        mg.setMaxLocals(maxLocals);
        return modificationsMade;
    }

    /**
     * Returns the instruction handles marking the bounds of loops in the given instruction list.
     *
     * @param il The instruction list to scan for loop instructions
     * @return A list of instruction handles representing loop bounds
     */
    private ArrayList<InstructionHandle> getLoopBounds(InstructionList il) {
        ArrayList<InstructionHandle> arr = new ArrayList<>();
        for (InstructionHandle ih : il.getInstructionHandles()) {
            Instruction inst = ih.getInstruction();
            if (inst instanceof GotoInstruction) {
                InstructionHandle targetIh = ((GotoInstruction)inst).getTarget();
                if (targetIh.getPosition() < ih.getPosition()) {
                    arr.add(targetIh);
                    arr.add(ih);
                }
            }
        }
        return arr;
    }

    /**
     * Returns the instruction handles marking the bounds of conditional branches in the given instruction list.
     *
     * @param il The instruction list to scan for branch instructions
     * @param loopBounds A list of instruction handles marking known loop boundaries
     * @return The list of instruction handles representing branch bounds
     */
    private ArrayList<InstructionHandle> getBranchBounds(InstructionList il, ArrayList<InstructionHandle> loopBounds) {
        ArrayList<InstructionHandle> arr = new ArrayList<>();
        for (InstructionHandle ih : il.getInstructionHandles()) {
            Instruction inst = ih.getInstruction();
            if (inst instanceof IfInstruction) {
                InstructionHandle targetIh = ((IfInstruction)inst).getTarget();
                if (targetIh.getPrev().getInstruction() instanceof GotoInstruction && !loopBounds.contains(targetIh.getPrev())) {
                    targetIh = ((GotoInstruction)targetIh.getPrev().getInstruction()).getTarget();
                }
                arr.add(ih);
                arr.add(targetIh);
            }
        }
        return arr;
    }

    /**
     *  Checks whether a variable changes within the bounds of a list of instructions
     *
     * @param ih InstructionHandle for instruction to check
     * @param bounds ArrayList of instructions to check from
     * @return Returns true is variable changes otherwise return false
     */
    private boolean variableChangesInBounds(InstructionHandle ih, ArrayList<InstructionHandle> bounds) {
        int variableIndex;
        if (ih.getInstruction() instanceof LoadInstruction) {
            variableIndex = ((LoadInstruction) ih.getInstruction()).getIndex();
        } else if (ih.getInstruction() instanceof StoreInstruction) {
            variableIndex = ((StoreInstruction) ih.getInstruction()).getIndex();
        } 
        else {
            // shouldnt be reachable
            variableIndex = -1;
        }

        for (int i = 0; i < bounds.size(); i += 2) {
            InstructionHandle start = bounds.get(i);
            InstructionHandle end = bounds.get(i + 1);
            // iterate through ihs of the loop
            for (InstructionHandle current = start; current != end.getNext(); current = current.getNext()) {
                // modifications happen with stores and IINC
                Instruction currentInstruction = current.getInstruction();
                if (currentInstruction instanceof StoreInstruction) {
                    if (((StoreInstruction) currentInstruction).getIndex() == variableIndex) {
                        return true;  
                    } 
                } else if (currentInstruction instanceof IINC){
                    if (((IINC) currentInstruction).getIndex() == variableIndex) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Performs one pass of simple folding over a method's instruction list. This is done by
     * matching patterns of the form: PushInstruction PushInstruction ArithmeticInstruction.
     * e.g., 'ldc 2', 'ldc 3', 'iadd' → replaced with 'ldc 5'.
     * 
     * @param il Bytecode instriction list for the method
     * @param cpgen BCEL constant pool get
     * @param loopBounds Signifies the bounds of any loops in the method
     * @return Returns the instruction list with all simple arithmetic instructions replaced
     */
    private boolean simpleFolding(InstructionList il, ConstantPoolGen cp, ArrayList<InstructionHandle> loopBounds) {
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
                safeDelete(il, match[0], match[0].getPrev());
                safeDelete(il, match[1], match[0].getPrev());
                safeDelete(il, match[2], match[0].getPrev());
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
      *
      * @param inst Instruction and the constant pool.
      * @param cp BCEL constant pool gen
      * @return Returns the value it pushes to the stack, or null otherwise.
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
     * 
     * @param op Arithmetic operation instruction
     * @param a Operand 1
     * @param b Operand 2
     * @param cp BCEL constant pool gen
     * @return Retuns a single instruction to directly load the result
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

        if (op instanceof IF_ICMPGT) {
            return new LDC((a.intValue() > b.intValue()) ? cp.addInteger(0) : cp.addInteger(1));
        }

        return null;
    }

    /**
     * Performs constant folding by finding all variables with constant declarations
	 * that are not reassigned. Then replaces all references to that variable with the
	 * constant value itself before removing the initial variable declaration fully.
     * 
     * @param il Bytecode instriction list for the method
     * @param cpgen BCEL constant pool get
     * @param loopBounds Signifies the bounds of any loops in the method
     * @param branchBounds Signifies the bounds of any if-else branches in the method
     * @return Returns the instruction list with all constant variables replaced
     */
	private boolean constantFolding(InstructionList il, ConstantPoolGen cpgen, ArrayList<InstructionHandle> loopBounds, ArrayList<InstructionHandle> branchBounds) {
        boolean modificationsMade = false;
		Map<Integer, Object> constantVars = new HashMap<>();
	
		// Find all variable declarations with constant values that are never re-assigned
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
			} else if (inst instanceof IINC) {
				int varIndex = ((IINC) inst).getIndex();
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
			} else if (inst instanceof StoreInstruction && !variableChangesInBounds(ih, loopBounds) &&  !variableChangesInBounds(ih, branchBounds)) {
				int varIndex = ((StoreInstruction) inst).getIndex();
				if (constantVars.containsKey(varIndex) && isConstantLoad(ih.getPrev())) {
                    toRemove.add(ih.getPrev());
					toRemove.add(ih);
				}
			} 
		}
	
		// Replace variable usages with direct constant loads
        if (replacements.size() > 0) modificationsMade = true;
		replacements.forEach((handle, newInstr) -> {
            il.insert(handle, newInstr);
            safeDelete(il, handle, handle.getPrev());
		});
        
        // Remove initial variable declarations
        if (toRemove.size() > 0) modificationsMade = true;
		toRemove.forEach(handle -> {
            safeDelete(il, handle, handle.getNext());
		});

        return modificationsMade;
	}

    /**
     * Loops over instruction list and removes any type cast operations by replacing them
	 * with a single intruction to load the resulting value directly as a constant
     * 
     * @param il Insturction list for method
     * @param cpgen BCEL constant pool gen
     * @param loopBounds Signifies the bounds of any loops in the method
     * @return Returns the instruction list with all cast instructions replaced
     */
	private boolean removeConversionInstructions(InstructionList il, ConstantPoolGen cpgen, ArrayList<InstructionHandle> loopBounds) {
        boolean modificationsMade = false;
		InstructionFactory factory = new InstructionFactory(cpgen);
	
		for (InstructionHandle ih = il.getStart(); ih != null; ih = ih.getNext()) {
			Instruction inst = ih.getInstruction();
			
			if (inst instanceof ConversionInstruction) {
				InstructionHandle prevIh = ih.getPrev();

				if (prevIh == null || !isConstantLoad(prevIh)) {
					continue;
				}
			
				Object originalValue = getConstantValue(prevIh, cpgen);
				if (originalValue == null) {
					continue;
				}
					
				Object convertedValue = convertValue(originalValue, (ConversionInstruction) inst);
				if (convertedValue == null) {
					continue;
				}
				Instruction newLoad = factory.createConstant(convertedValue);	
                il.insert(prevIh, newLoad);
                safeDelete(il, prevIh, prevIh.getPrev());
                safeDelete(il, ih, prevIh.getPrev());
                modificationsMade = true;
			}
		}

        return modificationsMade;
	}
	
    /**
     * When a constant value is pushed to the stack this method gets the constant
     * value itself depending the type of the instruction that is pushing it.
     * 
     * @param prevIh InstructionHandle for constant load instruction
     * @param cpgen BCEL constant pool gen
     * @return Constant value pushed to stack or null otherwise
     */
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

    /**
     * Get the result of a type cast from a conversion instruction
     * 
     * @param value The original value
     * @param convInst BCEL ConversionInstruction
     * @return The result of the cast otherise null
     */
	private Object convertValue(Object value, ConversionInstruction convInst) {
		if (convInst instanceof I2D && value instanceof Integer) {
			return ((Integer) value).doubleValue();
		} else if (convInst instanceof I2F && value instanceof Integer) {
			return ((Integer) value).floatValue();
		} else if (convInst instanceof I2L && value instanceof Integer) {
			return ((Integer) value).longValue();
		} else if (convInst instanceof L2I && value instanceof Long) {
			return ((Long) value).intValue();
		} else if (convInst instanceof L2F && value instanceof Long) {
			return ((Long) value).floatValue();
		} else if (convInst instanceof L2D && value instanceof Long) {
			return ((Long) value).doubleValue();
		} else if (convInst instanceof F2I && value instanceof Float) {
			return ((Float) value).intValue();
		} else if (convInst instanceof F2L && value instanceof Float) {
			return ((Float) value).longValue();
		} else if (convInst instanceof F2D && value instanceof Float) {
			return ((Float) value).doubleValue();
		} else if (convInst instanceof D2I && value instanceof Double) {
			return ((Double) value).intValue();
		} else if (convInst instanceof D2L && value instanceof Double) {
			return ((Double) value).longValue();
		} else if (convInst instanceof D2F && value instanceof Double) {
			return ((Double) value).floatValue();
		}
		return null;
	}

     /**
      * Gets the constant value from objects type bcel.classfile.Constant
      *
      * @param c Instance of bcel.classfile.Constant
      * @return Retuns the stored constant value or null otherwise
      */
	private Object getConstantValue(Constant c) {
		if (c instanceof ConstantInteger) return ((ConstantInteger) c).getBytes();
		if (c instanceof ConstantLong) return ((ConstantLong) c).getBytes();
		if (c instanceof ConstantFloat) return ((ConstantFloat) c).getBytes();
		if (c instanceof ConstantDouble) return ((ConstantDouble) c).getBytes();
		return null;
	}

    /**
     * Returns true if an instruction is a type of load instruction
     *  
     * @param ih InstructionHandle containing BCEL instruction
     * @return Returns true if instruction is a constant load otherwise false
     */
	private boolean isConstantLoad(InstructionHandle ih) {
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
