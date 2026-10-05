import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

import java_cup.runtime.Symbol;

/**
 * Entry point of the JFlex + CUP front end.
 *
 *   java CupCompiler <source-file> [-o <pcode-file>] [-d]
 *
 * The generated code is the same stack-machine code consumed by {@link VM},
 * so this compiler can replace the hand-written one:
 *
 *   java CupCompiler tests/good/src/01_arithmetic.src
 *   java VM tests/good/pcode_cup/01_arithmetic.pcode
 */
public class CupCompiler {

    public static void main(String[] args) {
        String in = null;
        String out = null;
        boolean dump = false;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-o") && i + 1 < args.length) {
                out = args[++i];
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
            Yylex lex = new Yylex(new BufferedReader(new FileReader(in)));
            CupParser parser = new CupParser(lex);
            parser.yylex = lex;

            Symbol result = parser.parse();

            if (parser.errorCount > 0 || result == null || result.value == null) {
                System.err.println(in + ": compilation failed with "
                        + parser.errorCount + " syntax error(s)");
                System.exit(1);
            }

            Ast.Program program = (Ast.Program) result.value;

            // Semantic analysis (C-rules) and code generation (R-rules).
            TreeCompiler tc = new TreeCompiler();
            List<Instr> code = tc.compile(program);

            PrintWriter pw = new PrintWriter(new FileWriter(out));
            for (Instr ins : code) {
                pw.println(ins.toString());
            }
            pw.close();

            System.out.println("Compilation succeeded (JFlex+CUP): " + in + " -> " + out
                    + "  (" + code.size() + " instructions)");

            if (dump) {
                int pc = 0;
                for (Instr ins : code) {
                    System.out.printf("%4d  %s%n", pc++, ins.toString());
                }
            }
        } catch (TreeCompiler.CompileError e) {
            System.err.println(in + ":" + e.line + ": error: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            System.err.println(in + ": error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println(in + ": error: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void usage() {
        System.err.println("usage: java CupCompiler <source-file> [-o pcode-file] [-d]");
        System.exit(2);
    }
}
