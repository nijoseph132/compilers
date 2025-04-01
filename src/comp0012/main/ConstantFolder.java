package comp0012.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.ConstantPushInstruction;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.LDC;
import org.apache.bcel.generic.LDC2_W;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.TargetLostException;
import org.apache.bcel.util.InstructionFinder;
import java.io.FileNotFoundException;
import org.apache.bcel.generic.InstructionTargeter;


public class ConstantFolder {
    ClassParser parser = null;
    ClassGen gen = null;
    JavaClass original = null;
    JavaClass optimized = null;

    public ConstantFolder(String classFilePath) {
        try {
            this.parser = new ClassParser(classFilePath);
            this.original = this.parser.parse();
            this.gen = new ClassGen(this.original);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	public void optimize() {
		// Create a new ClassGen based on the original class.
		ClassGen cgen = new ClassGen(original);
		ConstantPoolGen cpgen = cgen.getConstantPool();

		Method[] methods = cgen.getMethods();
		for (int i = 0; i < methods.length; i++) {
			// Remove the check for "simple" here

			MethodGen mg = new MethodGen(methods[i], cgen.getClassName(), cpgen);
			mg.removeNOPs();
			InstructionList il = mg.getInstructionList();
			if (il == null)
				continue;

			// Repeatedly apply constant folding until no changes occur.
			boolean changed;
			do {
				changed = simpleFolding(il, cpgen);
				il.setPositions(); // update instruction offsets after modifications
			} while (changed);

			// Strip unnecessary attributes and update stack/local variable info.
			mg.stripAttributes(true);
			mg.setMaxStack();
			mg.setMaxLocals();
			cgen.replaceMethod(methods[i], mg.getMethod());
		}

		// Update the ClassGen with the new constant pool and set the class version.
		cgen.setConstantPool(cpgen);
		cgen.setMajor(50);
		this.optimized = cgen.getJavaClass();
	}


    // Returns true if any folding occurred.
    private boolean simpleFolding(InstructionList il, ConstantPoolGen cpgen) {
        boolean changed = false;
        InstructionFinder finder = new InstructionFinder(il);

        // Loop through each supported binary arithmetic operation.
        for (FoldOp op : FoldOp.values()) {
            // Use a pattern that matches any PushInstruction (which includes LDC, LDC2_W, etc.)
            for (Iterator<?> iter = finder.search(op.pattern); iter.hasNext(); ) {
                InstructionHandle[] match = (InstructionHandle[]) iter.next();
                try {
                    Number a = getValue(match[0].getInstruction());
                    Number b = getValue(match[1].getInstruction());
                    if (a == null || b == null)
                        continue;
                    Number result = op.fold(a, b);
                    Instruction folded = op.push(result, cpgen);
                    InstructionHandle newHandle = il.insert(match[0], folded);
					try {
						// Delete the original three instructions
						il.delete(match[0], match[2]);
					} catch (TargetLostException ex) {
						// If there were branch targets on those instructions,
						// BCEL throws this exception and provides them for you to update:
						for (InstructionHandle lost : ex.getTargets()) {
							for (InstructionTargeter targeter : lost.getTargeters()) {
								targeter.updateTarget(lost, newHandle);
							}
						}
					}

// If we made it here, everything was deleted (and possibly retargeted),
// so we can safely say we've changed the code.
changed = true;

                    changed = true;
                } catch (ArithmeticException e) {
                    // Skip cases like division by zero.
                    continue;
                } catch (Exception ignored) {}
            }
        }
        return changed;
    }

	public void write(String optimisedFilePath)
	{
		this.optimize();

		try (FileOutputStream out = new FileOutputStream(new File(optimisedFilePath))) {
			this.optimized.dump(out);
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}


    private Number getValue(Instruction inst) {
        if (inst instanceof ConstantPushInstruction)
            return ((ConstantPushInstruction) inst).getValue();
        else if (inst instanceof LDC) {
            Object val = ((LDC) inst).getValue(null);
            return (val instanceof Number) ? (Number) val : null;
        } else if (inst instanceof LDC2_W) {
            Object val = ((LDC2_W) inst).getValue(null);
            return (val instanceof Number) ? (Number) val : null;
        }
        return null;
    }

    // Enumeration for folding operations with patterns matching PushInstructions.
    private enum FoldOp {
        IADD("PushInstruction PushInstruction IADD") {
            public Number fold(Number a, Number b) { return a.intValue() + b.intValue(); }
            public Instruction push(Number result, ConstantPoolGen cp) {
                return new LDC(cp.addInteger(result.intValue()));
            }
        },
        FADD("PushInstruction PushInstruction FADD") {
            public Number fold(Number a, Number b) { return a.floatValue() + b.floatValue(); }
            public Instruction push(Number result, ConstantPoolGen cp) {
                return new LDC(cp.addFloat(result.floatValue()));
            }
        },
        LADD("PushInstruction PushInstruction LADD") {
            public Number fold(Number a, Number b) { return a.longValue() + b.longValue(); }
            public Instruction push(Number result, ConstantPoolGen cp) {
                return new LDC2_W(cp.addLong(result.longValue()));
            }
        },
        DADD("PushInstruction PushInstruction DADD") {
            public Number fold(Number a, Number b) { return a.doubleValue() + b.doubleValue(); }
            public Instruction push(Number result, ConstantPoolGen cp) {
                return new LDC2_W(cp.addDouble(result.doubleValue()));
            }
        },
        ISUB("PushInstruction PushInstruction ISUB") {
            public Number fold(Number a, Number b) { return a.intValue() - b.intValue(); }
            public Instruction push(Number result, ConstantPoolGen cp) {
                return new LDC(cp.addInteger(result.intValue()));
            }
        },
        FSUB("PushInstruction PushInstruction FSUB") {
            public Number fold(Number a, Number b) { return a.floatValue() - b.floatValue(); }
            public Instruction push(Number result, ConstantPoolGen cp) {
                return new LDC(cp.addFloat(result.floatValue()));
            }
        },
        LSUB("PushInstruction PushInstruction LSUB") {
            public Number fold(Number a, Number b) { return a.longValue() - b.longValue(); }
            public Instruction push(Number result, ConstantPoolGen cp) {
                return new LDC2_W(cp.addLong(result.longValue()));
            }
        },
        DSUB("PushInstruction PushInstruction DSUB") {
            public Number fold(Number a, Number b) { return a.doubleValue() - b.doubleValue(); }
            public Instruction push(Number result, ConstantPoolGen cp) {
                return new LDC2_W(cp.addDouble(result.doubleValue()));
            }
        },
        IMUL("PushInstruction PushInstruction IMUL") {
            public Number fold(Number a, Number b) { return a.intValue() * b.intValue(); }
            public Instruction push(Number result, ConstantPoolGen cp) {
                return new LDC(cp.addInteger(result.intValue()));
            }
        },
        FMUL("PushInstruction PushInstruction FMUL") {
            public Number fold(Number a, Number b) { return a.floatValue() * b.floatValue(); }
            public Instruction push(Number result, ConstantPoolGen cp) {
                return new LDC(cp.addFloat(result.floatValue()));
            }
        },
        LMUL("PushInstruction PushInstruction LMUL") {
            public Number fold(Number a, Number b) { return a.longValue() * b.longValue(); }
            public Instruction push(Number result, ConstantPoolGen cp) {
                return new LDC2_W(cp.addLong(result.longValue()));
            }
        },
        DMUL("PushInstruction PushInstruction DMUL") {
            public Number fold(Number a, Number b) { return a.doubleValue() * b.doubleValue(); }
            public Instruction push(Number result, ConstantPoolGen cp) {
                return new LDC2_W(cp.addDouble(result.doubleValue()));
            }
        },
        IDIV("PushInstruction PushInstruction IDIV") {
            public Number fold(Number a, Number b) {
                if (b.intValue() == 0)
                    throw new ArithmeticException("Division by zero");
                return a.intValue() / b.intValue();
            }
            public Instruction push(Number result, ConstantPoolGen cp) {
                return new LDC(cp.addInteger(result.intValue()));
            }
        },
        FDIV("PushInstruction PushInstruction FDIV") {
            public Number fold(Number a, Number b) {
                if (b.floatValue() == 0.0f)
                    throw new ArithmeticException("Division by zero");
                return a.floatValue() / b.floatValue();
            }
            public Instruction push(Number result, ConstantPoolGen cp) {
                return new LDC(cp.addFloat(result.floatValue()));
            }
        },
        LDIV("PushInstruction PushInstruction LDIV") {
            public Number fold(Number a, Number b) {
                if (b.longValue() == 0L)
                    throw new ArithmeticException("Division by zero");
                return a.longValue() / b.longValue();
            }
            public Instruction push(Number result, ConstantPoolGen cp) {
                return new LDC2_W(cp.addLong(result.longValue()));
            }
        },
        DDIV("PushInstruction PushInstruction DDIV") {
            public Number fold(Number a, Number b) {
                if (b.doubleValue() == 0.0d)
                    throw new ArithmeticException("Division by zero");
                return a.doubleValue() / b.doubleValue();
            }
            public Instruction push(Number result, ConstantPoolGen cp) {
                return new LDC2_W(cp.addDouble(result.doubleValue()));
            }
        };

        public final String pattern;
        FoldOp(String pattern) {
            this.pattern = pattern;
        }
        public abstract Number fold(Number a, Number b);
        public abstract Instruction push(Number result, ConstantPoolGen cp);
    }
}
