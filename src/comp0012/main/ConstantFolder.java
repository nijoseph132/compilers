package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.util.InstructionFinder;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.StoreInstruction;
import org.apache.bcel.generic.TargetLostException;


import org.apache.bcel.generic.ConstantPushInstruction;
import org.apache.bcel.generic.ICONST;
import org.apache.bcel.generic.LDC;
import org.apache.bcel.generic.InstructionTargeter;
import org.apache.bcel.generic.LDC2_W;
import org.apache.bcel.generic.LoadInstruction;
import org.apache.bcel.generic.Instruction;



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
	
	public void optimize()
	{
		ClassGen cgen = new ClassGen(original);
		ConstantPoolGen cpgen = cgen.getConstantPool();

		for (Method method : cgen.getMethods()) {
			MethodGen mg = new MethodGen(method, cgen.getClassName(), cpgen);
			InstructionList il = mg.getInstructionList();
            // System.out.println(il);
			if (il == null) continue;

            // Code methodCode = method.getCode();
            // InstructionList il = new InstructionList(methodCode.getCode());
            // MethodGen mg = new MethodGen(method.getAccessFlags(), method.getReturnType(), method.getArgumentTypes(), null, method.getName(), cgen.getClassName(), il , cpgen);

            dynamicFolding(il, mg);
            simpleFolding(il, cpgen);
            // Just a skeleton

            // boolean hasChanges = false;
            // do {
            //     hasChanges = false;
            //     hasChnages |= constantFolding(il, cpgen);
            //     hasChanges |= simpleFolding(il, cpgen);
            // } while (hasChanges);

			//Call your methods here

			mg.setInstructionList(il);
			mg.setMaxStack();
			mg.setMaxLocals();
            cgen.replaceMethod(method, mg.getMethod());
        }
        
		this.optimized = gen.getJavaClass();
	}


    private void dynamicFolding(InstructionList il, MethodGen mg) {
        HashMap<Integer, Integer> varMap = new HashMap<>();
        int maxLocals = mg.getMaxLocals(); // Track the highest used variable index
    
        for (InstructionHandle handle = il.getStart(); handle != null;) {
            InstructionHandle next = handle.getNext();
            Instruction inst = handle.getInstruction();
            // System.out.println("instruction list: " + il);
    
            if (inst instanceof StoreInstruction store) {
                int varIndex = store.getIndex();
                int newVarIndex;
    
                if (varMap.containsKey(varIndex)) {
                    newVarIndex = ++maxLocals;
                } else {
                    newVarIndex = varIndex;
                    // varMap.put(varIndex, varIndex);
                }
                
                varMap.put(varIndex, newVarIndex);
                // Replace the instruction with a new instance of the same type
                Instruction newStore = createNewStoreInstruction(store, newVarIndex);

                // System.out.println("store, new var index: " + varMap.get(varIndex));
                // System.out.println("store instruction" + store);
                // System.out.println("instruction list: " + il);

                // handle.setInstruction(newStore);
                il.insert(handle, newStore);
				try {
					il.delete(handle);
				} catch (TargetLostException e) {
					e.printStackTrace();
				}
            } else if (inst instanceof LoadInstruction load) {
                int varIndex = load.getIndex();
                if (varMap.containsKey(varIndex)) {
                    int newVarIndex = varMap.get(varIndex);
    
                    // Replace the instruction with a new instance of the same type
                    Instruction newLoad = createNewLoadInstruction(load, newVarIndex);
                    
                    // System.out.println("load: new var index: " + varMap.get(varIndex));
                    // System.out.println("load instruction" + load);

                    // handle.setInstruction(newLoad); 
                    il.insert(handle, newLoad);
                    try {
                        il.delete(handle);
                    } catch (TargetLostException e) {
                        e.printStackTrace();
                    }
                }
            }
            handle = next;
        }
    
        mg.setMaxLocals(maxLocals); // Ensure the method has enough space for new variables
    }
    
    // Helper method to create a new instance of a StoreInstruction
    private StoreInstruction createNewStoreInstruction(StoreInstruction store, int index) {
        if (store instanceof org.apache.bcel.generic.ISTORE) return new org.apache.bcel.generic.ISTORE(index);
        if (store instanceof org.apache.bcel.generic.LSTORE) return new org.apache.bcel.generic.LSTORE(index);
        if (store instanceof org.apache.bcel.generic.FSTORE) return new org.apache.bcel.generic.FSTORE(index);
        if (store instanceof org.apache.bcel.generic.DSTORE) return new org.apache.bcel.generic.DSTORE(index);
        return new org.apache.bcel.generic.ASTORE(index); // Default case (ASTORE for object types)
    }
    
    // Helper method to create a new instance of a LoadInstruction
    private LoadInstruction createNewLoadInstruction(LoadInstruction load, int index) {
        if (load instanceof org.apache.bcel.generic.ILOAD) return new org.apache.bcel.generic.ILOAD(index);
        if (load instanceof org.apache.bcel.generic.LLOAD) return new org.apache.bcel.generic.LLOAD(index);
        if (load instanceof org.apache.bcel.generic.FLOAD) return new org.apache.bcel.generic.FLOAD(index);
        if (load instanceof org.apache.bcel.generic.DLOAD) return new org.apache.bcel.generic.DLOAD(index);
        return new org.apache.bcel.generic.ALOAD(index); // Default case (ALOAD for object types)
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

//Helper method to extract a constant value from an instruction. ConstantPushInstruction, LDC, and LDC2_W.
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

