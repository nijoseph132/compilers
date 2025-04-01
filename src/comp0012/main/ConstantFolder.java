package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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


public class ConstantFolder
{
	ClassParser parser = null;
	ClassGen gen = null;

	JavaClass original = null;
	JavaClass optimized = null;

	public ConstantFolder(String classFilePath)
	{
		try{
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch(IOException e){
			e.printStackTrace();
		}
	}
	
	public void optimize() {
		ClassGen cgen = new ClassGen(original);
		ConstantPoolGen cpgen = cgen.getConstantPool();

		for (Method method : cgen.getMethods()) {
			MethodGen mg = new MethodGen(method, cgen.getClassName(), cpgen);
			InstructionList il = mg.getInstructionList();
			if (il == null) continue;

			// Task 1 Call
			simpleFolding(il, cpgen);

			// Task 2 call
			System.out.println("\n=== Method: " + method.getName() + " ===\n\n");
			constantFolding(il, cpgen);

			//Call your methods here

			mg.setInstructionList(il);
			mg.setMaxStack();
			mg.setMaxLocals();
			cgen.replaceMethod(method, mg.getMethod());
    }
        
		this.optimized = gen.getJavaClass();
	}

	/**
		 * Performs simple constant folding over the instruction list.
		 * Uses patterns defined in the FoldOp enum to detect constant expressions and simplify them.
	*/
	private void simpleFolding(InstructionList il, ConstantPoolGen cpgen) {
		InstructionFinder finder = new InstructionFinder(il);

		for (FoldOp op : FoldOp.values()) {
			for (Iterator<?> iter = finder.search(op.pattern); iter.hasNext(); ) {
				InstructionHandle[] match = (InstructionHandle[]) iter.next();

				try {
					Number a = getValue(match[0].getInstruction());
					Number b = getValue(match[1].getInstruction());
					if (a == null || b == null) continue;

					Number result = op.fold(a, b);
					Instruction folded = op.push(result, cpgen);

					InstructionHandle newHandle = il.insert(match[0], folded);
					il.delete(match[0], match[2]);

					for (InstructionTargeter t : match[2].getTargeters()) {
						t.updateTarget(match[2], newHandle);
					}

				} catch (Exception ignored) {}
			}
		}
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

		// Finally, fold all found constants
		System.out.println("Constant variables: " + verifiedConstants);
		replaceLoadsWithConstants(il, cpgen, constantVars);
	}
	/*/
	private void replaceLoadsWithConstants(InstructionList il, ConstantPoolGen cpgen, Map<Integer, Object> constantVars) {
		List<InstructionHandle> toReplace = new ArrayList<>();
		InstructionFactory factory = new InstructionFactory(cpgen);
	
		// Find all load instructions for defined constant variables
		for (InstructionHandle ih = il.getStart(); ih != null; ih = ih.getNext()) {
			Instruction inst = ih.getInstruction();
			if (inst instanceof LoadInstruction) {
				int varIndex = ((LoadInstruction) inst).getIndex();
				if (constantVars.containsKey(varIndex)) {
					toReplace.add(ih);
				}
			}
		}
		System.out.println("To replace: " + toReplace);

		for (InstructionHandle ih : toReplace) {
			int varIndex = ((LoadInstruction) ih.getInstruction()).getIndex();
			Object value = constantVars.get(varIndex);
	
			try {
				Instruction constInst = factory.createConstant(value);
				System.out.println(constInst);
			} catch (IllegalArgumentException e) {
				System.err.println("Could not create constant for value: " + value);
			}
		}
	}
	*/

	private void replaceLoadsWithConstants(InstructionList il, ConstantPoolGen cpgen, 
                                     Map<Integer, Object> constantVars) {
		InstructionFactory factory = new InstructionFactory(cpgen);
		List<InstructionHandle> replacements = new ArrayList<>();
		Map<InstructionHandle, Instruction> newInstructions = new HashMap<>();

		for (InstructionHandle ih = il.getStart(); ih != null; ih = ih.getNext()) {
			Instruction inst = ih.getInstruction();
			if (inst instanceof LoadInstruction) {
				int varIndex = ((LoadInstruction) inst).getIndex();
				if (constantVars.containsKey(varIndex)) {
					Instruction constInst = factory.createConstant(constantVars.get(varIndex));
					replacements.add(ih);
					newInstructions.put(ih, constInst);
				}
			}
		}
		System.out.println(newInstructions);

		for (InstructionHandle oldIh : replacements) {
			Instruction newInst = newInstructions.get(oldIh);
			InstructionHandle newIh = il.insert(oldIh, newInst);

			try {
                il.delete(oldIh);
            } catch (TargetLostException e) {
                for (InstructionHandle target : e.getTargets()) {
                    target.setInstruction(InstructionConstants.NOP);
                }
            }
		}
	}

	public void write(String optimisedFilePath)
	{
		this.optimize();

		try {
			FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
			this.optimized.dump(out);
		} catch (FileNotFoundException e) {
			// Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
		Helper method to extract a constant value from an instruction. ConstantPushInstruction, LDC, and LDC2_W.
	*/
	private Number getValue(Instruction inst) {
		if (inst instanceof ConstantPushInstruction cpi) {
			return cpi.getValue();
		} else if (inst instanceof LDC ldc) {
			Object val = ldc.getValue(null);
			return (val instanceof Number) ? (Number) val : null;
		} else if (inst instanceof LDC2_W ldc2) {
			Object val = ldc2.getValue(null);
			return (val instanceof Number) ? (Number) val : null;
		}
		return null;
	}

/**
	* Enum FoldOp represents a single arithmetic folding operation.
	 * Each enum value defines:
	 * - a pattern to match
	 * - how to fold the constants
	 * - how to emit a new constant instruction (LDC or LDC2_W)
*/
	private enum FoldOp {
		IADD("ConstantPushInstruction ConstantPushInstruction IADD") {
			public Number fold(Number a, Number b) { return a.intValue() + b.intValue(); }
			public Instruction push(Number result, ConstantPoolGen cp) {
				return new LDC(cp.addInteger(result.intValue()));
			}
		},
		FADD("LDC LDC FADD") {
			public Number fold(Number a, Number b) { return a.floatValue() + b.floatValue(); }
			public Instruction push(Number result, ConstantPoolGen cp) {
				return new LDC(cp.addFloat(result.floatValue()));
			}
		},
		LADD("LDC2_W LDC2_W LADD") {
			public Number fold(Number a, Number b) { return a.longValue() + b.longValue(); }
			public Instruction push(Number result, ConstantPoolGen cp) {
				return new LDC2_W(cp.addLong(result.longValue()));
			}
		},
		DADD("LDC2_W LDC2_W DADD") {
			public Number fold(Number a, Number b) { return a.doubleValue() + b.doubleValue(); }
			public Instruction push(Number result, ConstantPoolGen cp) {
				return new LDC2_W(cp.addDouble(result.doubleValue()));
			}
		};

		public final String pattern;
		FoldOp(String pattern) { this.pattern = pattern; }

		public abstract Number fold(Number a, Number b);
		public abstract Instruction push(Number result, ConstantPoolGen cp);
	}

}

