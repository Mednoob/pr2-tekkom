import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

/**
 * Entry point of the compiler.
 *
 *   java Compiler <source-file> [-o <pcode-file>] [-s] [-d]
 *
 *   -o  write the generated assembly (default: source with .pcode)
 *   -s  print the symbol table (C1: optional symbol-table dump)
 *   -d  also print the generated code to stdout
 */
public class Compiler {

    public static void main(String[] args) {
        String in = null;
        String out = null;
        boolean symbols = false;
        boolean dump = false;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-o") && i + 1 < args.length) {
                out = args[++i];
            } else if (args[i].equals("-s")) {
                symbols = true;
            } else if (args[i].equals("-d")) {
                dump = true;
            } else if (in == null) {
                in = args[i];
            } else {
                usage();
            }
        }
        if (in == null) {
            usage();
        }
        if (out == null) {
            int dot = in.lastIndexOf('.');
            out = (dot >= 0 ? in.substring(0, dot) : in) + ".pcode";
        }

        try {
            Lexer lex = new Lexer(new BufferedReader(new FileReader(in)));
            Parser parser = new Parser(lex);
            parser.setShowSymbols(symbols);
            parser.parseProgram();

            List<Instr> code = parser.getCode();
            checkPatched(code);

            PrintWriter pw = new PrintWriter(new FileWriter(out));
            for (Instr ins : code) {
                pw.println(ins.toString());
            }
            pw.close();

            System.out.println("Compilation succeeded: " + in + " -> " + out
                    + "  (" + code.size() + " instructions)");

            if (dump) {
                int pc = 0;
                for (Instr ins : code) {
                    System.out.printf("%4d  %s%n", pc++, ins.toString());
                }
            }
        } catch (Parser.CompileError e) {
            System.err.println(in + ":" + e.line + ":" + e.col + ": error: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            System.err.println(in + ": error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("I/O error: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void checkPatched(List<Instr> code) {
        for (Instr ins : code) {
            if (ins.a != null && ins.a.length >= 1 && ins.a[0] == -1
                    && (ins.op.equals("JMP") || ins.op.equals("JZ")
                        || ins.op.equals("INT") || ins.op.equals("FREE"))) {
                throw new RuntimeException("internal error: unpatched " + ins.op);
            }
        }
    }

    private static void usage() {
        System.err.println("usage: java Compiler <source-file> [-o pcode-file] [-s] [-d]");
        System.exit(2);
    }
}
