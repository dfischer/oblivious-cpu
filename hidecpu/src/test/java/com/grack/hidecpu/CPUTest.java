package com.grack.hidecpu;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.grack.hidecpu.assembler.BranchType;
import com.grack.hidecpu.assembler.Compiler;
import com.grack.hidecpu.assembler.Line;
import com.grack.hidecpu.assembler.OpSource;
import com.grack.hidecpu.assembler.OpTarget;
import com.grack.hidecpu.assembler.Opcode;
import com.grack.hidecpu.assembler.Program;
import com.grack.hidecpu.assembler.Value;
import com.grack.homomorphic.graph.Graph;
import com.grack.homomorphic.light.LightBitFactory;
import com.grack.homomorphic.light.StandardStateFactory;
import com.grack.homomorphic.logging.LoggingBitFactory;
import com.grack.homomorphic.logging.LoggingStateFactory;
import com.grack.homomorphic.ops.State;
import com.grack.homomorphic.ops.StateFactory;
import com.grack.homomorphic.ops.Word;

public class CPUTest {
	private Map<String, Object> initialState;

	@Before
	public void setup() {
		Program program = new Program();

		List<Line> lines = program.getLines();
		lines.add(new Line(Opcode.LOAD, OpSource.CONSTANT, OpTarget.R0,
				new Value(15)));
		lines.add(new Line("doubler"));
		lines.add(new Line(Opcode.SUB, OpSource.CONSTANT, OpTarget.R0,
				new Value(1)));
		lines.add(new Line(Opcode.ROL, OpSource.R0_RELATIVE_LOAD, OpTarget.R1,
				new Value("data")));
		lines.add(new Line(Opcode.CMP, OpSource.CONSTANT, OpTarget.R1,
				new Value(10)));
		lines.add(new Line(Opcode.BRA, BranchType.LT, new Value("no_sub")));
		lines.add(new Line(Opcode.SUB, OpSource.CONSTANT, OpTarget.R1,
				new Value(9)));

		lines.add(new Line("no_sub"));
		lines.add(new Line(Opcode.STORE, OpSource.R0_RELATIVE_LOAD,
				OpTarget.R1, new Value("data")));
		lines.add(new Line(Opcode.LOOP, OpSource.CONSTANT, OpTarget.R0,
				new Value("doubler")));
		// ??
		lines.add(new Line(Opcode.STF, OpSource.CONSTANT, OpTarget.R0,
				new Value(1)));
		lines.add(new Line(Opcode.LOAD, OpSource.CONSTANT, OpTarget.R1,
				new Value(0)));
		lines.add(new Line(Opcode.LOAD, OpSource.CONSTANT, OpTarget.R0,
				new Value(16)));
		lines.add(new Line("adder"));
		lines.add(new Line(Opcode.ADD, OpSource.R0_RELATIVE_LOAD, OpTarget.R1,
				new Value("data")));
		lines.add(new Line(Opcode.LOOP, OpSource.CONSTANT, OpTarget.R0,
				new Value("adder")));

		lines.add(new Line(Opcode.STORE, OpSource.CONSTANT, OpTarget.R1,
				new Value("sum")));

		lines.add(new Line("check"));
		lines.add(new Line(Opcode.CMP, OpSource.CONSTANT, OpTarget.R1,
				new Value(10)));
		lines.add(new Line(Opcode.BRA, BranchType.LT, new Value("done")));
		lines.add(new Line(Opcode.SUB, OpSource.CONSTANT, OpTarget.R1,
				new Value(10)));
		lines.add(new Line(Opcode.JUMP, OpSource.CONSTANT, OpTarget.R0,
				new Value("check")));

		lines.add(new Line("done"));
		lines.add(new Line(Opcode.STORE, OpSource.CONSTANT, OpTarget.R1,
				new Value("res")));
		lines.add(new Line("halt"));
		lines.add(new Line(Opcode.JUMP, OpSource.CONSTANT, OpTarget.R0,
				new Value("halt")));

		lines.add(new Line("sum"));
		lines.add(new Line(new Value(0)));
		lines.add(new Line("res"));
		lines.add(new Line(new Value(0)));
		lines.add(new Line("data"));
		for (int i : new int[] { 5, 4, 9, 7, 0, 3, 6, 5, 0, 2, 1, 6, 1, 6, 1, 8 }) {
			lines.add(new Line(new Value(i)));
		}
		
//		System.out.println(program);

		Compiler compiler = new Compiler();
		compiler.compile(program);

		initialState = new HashMap<>();
		initialState.put("memory", program.getProgram());
	}
	
	@Test
	public void runUntilDone() {
		CPU cpu = new CPU();
		LightBitFactory factory = new LightBitFactory();
		StateFactory stateFactory = new StandardStateFactory(factory, initialState, true);
		State state = stateFactory.createState();
		cpu.initialize(factory, stateFactory);
		long lastPc = -1;
		for (int i = 0; i < 300; i++) {
			cpu.tick(state);
			long pc = factory.extract(state.getWordRegister("pc"));
			if (pc == lastPc)
				break;
		}

		dumpMemory(factory, state);
		
		System.out.println("XOR count = " + factory.getXorCount());
		System.out.println("AND count = " + factory.getAndCount());
	}

	@Test
	public void cpuLogging() throws FileNotFoundException, IOException {
		LoggingBitFactory factory = new LoggingBitFactory();
		LoggingStateFactory stateFactory = new LoggingStateFactory(factory);
		CPU cpu = new CPU();
		cpu.initialize(factory, stateFactory);
		State state = stateFactory.createState();
		cpu.tick(state);

		// Bit bit = state.getBitRegister("alu_carry");
		// System.out.println(((LoggingBit) bit.nativeBit()).describe());

		Graph graph = factory.toGraph();
		graph.optimize();

		try (Writer w = new OutputStreamWriter(new FileOutputStream(
				"/tmp/output.txt"))) {
			graph.toC(w);
		}
	}
	
	private void dumpMemory(LightBitFactory factory, State state) {
		Word[] memory = state.getWordArrayRegister("memory");
		for (int j = 0; j < memory.length; j++) {
			long mem = factory.extract(memory[j]) & 0xff;
			System.out.println(String.format("%3d: %s %5d", j,
					memory[j].toString(), mem));
		}
	}
}
