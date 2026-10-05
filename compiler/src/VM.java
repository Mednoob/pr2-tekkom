import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Interpreter for the stack machine produced by {@link Compiler}.
 *
 *   java VM <pcode-file> [-t]
 *
 *   -t  trace every executed instruction to stderr
 *
 * Frame layout (relative to the base pointer BP):
 *   BP+0 static link, BP+1 dynamic link (caller BP), BP+2 saved free pointer,
 *   BP+3 function result, BP+4... locals and parameters.
 */
public class VM {

    private static final int FRAME_BASE = 4;
    private static final int MEM_SIZE   = 1 << 18;

    private final int[] mem = new int[MEM_SIZE];
    private final Deque<Integer> opStack  = new ArrayDeque<Integer>();
    private final Deque<Integer> retStack = new ArrayDeque<Integer>();

    private int pc;
    private int bp;
    private int freep;

    private List<Instr> code;
    private boolean trace;

    private BufferedReader input =
            new BufferedReader(new InputStreamReader(System.in));

    // -------------------------------------------------------------- loading

    static List<Instr> load(String file) throws Exception {
        List<Instr> code = new ArrayList<Instr>();
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("OUTS")) {
                String rest = line.substring(4).trim();
                if (rest.startsWith("\"") && rest.endsWith("\"") && rest.length() >= 2) {
                    rest = rest.substring(1, rest.length() - 1);
                }
                code.add(Instr.text("OUTS", unescape(rest)));
                continue;
            }
            String[] p = line.split("\\s+");
            String op = p[0];
            int[] a = new int[Math.max(0, p.length - 1)];
            for (int i = 1; i < p.length; i++) {
                a[i - 1] = Integer.parseInt(p[i]);
            }
            code.add(new Instr(op, a));
        }
        br.close();
        return code;
    }

    static String unescape(String t) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '\\' && i + 1 < t.length()) {
                char n = t.charAt(++i);
                switch (n) {
                    case 'n':  b.append('\n'); break;
                    case 't':  b.append('\t'); break;
                    case 'r':  b.append('\r'); break;
                    case '"':  b.append('"');  break;
                    case '\\': b.append('\\'); break;
                    default:   b.append(n);    break;
                }
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- runner

    public void run() {
        pc = 0;
        bp = 0;
        freep = FRAME_BASE;
        mem[0] = 0; // static link
        mem[1] = 0; // dynamic link
        mem[2] = 0; // saved free pointer
        mem[3] = 0; // result

        while (pc >= 0 && pc < code.size()) {
            Instr in = code.get(pc);
            if (trace) {
                System.err.printf("pc=%4d bp=%4d sp=%4d  %s%n", pc, bp, freep, in);
            }
            pc++;
            switch (in.op) {
                case "LIT":  push(in.a[0]); break;
                case "LOD":  push(mem[frame(in.a[0]) + FRAME_BASE + in.a[1]]); break;
                case "STO":  mem[frame(in.a[0]) + FRAME_BASE + in.a[1]] = pop(); break;
                case "LDA":  push(frame(in.a[0]) + FRAME_BASE + in.a[1]); break;
                case "LDI":  push(mem[pop()]); break;
                case "STI": {
                    int val = pop();
                    int addr = pop();
                    mem[addr] = val;
                    break;
                }
                case "CHK": {
                    int idx = pop();
                    if (idx < in.a[0] || idx > in.a[1]) {
                        throw new RuntimeException("array index " + idx
                                + " out of bounds [" + in.a[0] + ".." + in.a[1] + "]");
                    }
                    push(idx);
                    break;
                }
                case "INT":  freep += in.a[0]; checkMem(); break;
                case "FREE": freep -= in.a[0]; break;
                case "JMP":  pc = in.a[0]; break;
                case "JZ":   if (pop() == 0) { pc = in.a[0]; } break;
                case "ADD":  { int b = pop(), a = pop(); push(a + b); } break;
                case "SUB":  { int b = pop(), a = pop(); push(a - b); } break;
                case "MUL":  { int b = pop(), a = pop(); push(a * b); } break;
                case "DIV":  {
                    int b = pop(), a = pop();
                    if (b == 0) {
                        throw new RuntimeException("division by zero");
                    }
                    push(a / b);
                    break;
                }
                case "NEG":  push(-pop()); break;
                case "NOT":  push(pop() == 0 ? 1 : 0); break;
                case "AND":  { int b = pop(), a = pop(); push((a != 0 && b != 0) ? 1 : 0); } break;
                case "OR":   { int b = pop(), a = pop(); push((a != 0 || b != 0) ? 1 : 0); } break;
                case "EQ":   { int b = pop(), a = pop(); push(a == b ? 1 : 0); } break;
                case "NE":   { int b = pop(), a = pop(); push(a != b ? 1 : 0); } break;
                case "LT":   { int b = pop(), a = pop(); push(a <  b ? 1 : 0); } break;
                case "LE":   { int b = pop(), a = pop(); push(a <= b ? 1 : 0); } break;
                case "GT":   { int b = pop(), a = pop(); push(a >  b ? 1 : 0); } break;
                case "GE":   { int b = pop(), a = pop(); push(a >= b ? 1 : 0); } break;
                case "IN": {
                    String s;
                    try {
                        s = input.readLine();
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("I/O error while reading input");
                    }
                    if (s == null) {
                        throw new RuntimeException("end of input while reading an integer");
                    }
                    s = s.trim();
                    try {
                        push(Integer.parseInt(s));
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("expected an integer but got '" + s + "'");
                    }
                    break;
                }
                case "OUT":  System.out.print(pop()); break;
                case "OUTNL": System.out.println(); break;
                case "OUTS": System.out.print(in.s); break;
                case "CALL":
                case "CALF": {
                    int addr = in.a[0];
                    int argc = in.a[1];
                    int dist = in.a[2];
                    int sl = bp;
                    for (int i = 0; i < dist; i++) {
                        sl = mem[sl];
                    }
                    int b = freep;
                    mem[b]     = sl;
                    mem[b + 1] = bp;
                    mem[b + 2] = freep;
                    mem[b + 3] = 0;
                    for (int i = argc - 1; i >= 0; i--) {
                        mem[b + FRAME_BASE + i] = pop();
                    }
                    freep = b + FRAME_BASE + argc;
                    checkMem();
                    bp = b;
                    retStack.push(pc);
                    pc = addr;
                    break;
                }
                case "RET": {
                    freep = mem[bp + 2];
                    bp = mem[bp + 1];
                    pc = retStack.pop();
                    break;
                }
                case "RETF": {
                    int result = mem[bp + 3];
                    freep = mem[bp + 2];
                    bp = mem[bp + 1];
                    pc = retStack.pop();
                    push(result);
                    break;
                }
                case "STRES": mem[bp + 3] = pop(); break;
                case "HALT": return;
                default:
                    throw new RuntimeException("unknown instruction: " + in.op);
            }
        }
        if (pc >= code.size()) {
            // Fell off the end without HALT: treat as normal termination.
            return;
        }
    }

    private void push(int v) {
        opStack.push(v);
    }

    private int pop() {
        if (opStack.isEmpty()) {
            throw new RuntimeException("operand stack underflow");
        }
        return opStack.pop();
    }

    private int frame(int level) {
        int f = bp;
        for (int i = 0; i < level; i++) {
            f = mem[f];
        }
        return f;
    }

    private void checkMem() {
        if (freep >= MEM_SIZE) {
            throw new RuntimeException("memory exhausted (stack overflow)");
        }
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        String file = null;
        boolean trace = false;
        for (String a : args) {
            if (a.equals("-t")) {
                trace = true;
            } else if (file == null) {
                file = a;
            }
        }
        if (file == null) {
            System.err.println("usage: java VM <pcode-file> [-t]");
            System.exit(2);
        }
        VM vm = new VM();
        vm.code = load(file);
        vm.trace = trace;
        try {
            vm.run();
        } catch (RuntimeException e) {
            System.err.println("runtime error: " + e.getMessage());
            System.exit(1);
        }
    }
}
