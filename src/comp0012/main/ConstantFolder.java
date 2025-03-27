package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
import org.apache.bcel.generic.TargetLostException;


import org.apache.bcel.generic.ConstantPushInstruction;
import org.apache.bcel.generic.LDC;
import org.apache.bcel.generic.InstructionTargeter;
import org.apache.bcel.generic.LDC2_W;
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
			if (il == null) continue;

			//Task 1 Call
			simpleFolding(il, cpgen);

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

