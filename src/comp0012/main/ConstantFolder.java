package comp0012.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;
import java.io.FileNotFoundException;


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
}
