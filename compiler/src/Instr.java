/**
 * One instruction of the abstract stack machine.
 * Operands are stored in {@code a}; {@code s} holds a text constant for OUTS.
 */
public class Instr {
    public String op;
    public int[]  a;
    public String s;

    public Instr(String op, int... a) {
        this.op = op;
        this.a = a;
    }

    public static Instr text(String op, String s) {
        Instr i = new Instr(op, 0);
        i.s = s;
        return i;
    }

    private static String esc(String t) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            switch (c) {
                case '\n': b.append("\\n"); break;
                case '\t': b.append("\\t"); break;
                case '\r': b.append("\\r"); break;
                case '"':  b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                default:   b.append(c);
            }
        }
        return b.toString();
    }

    @Override
    public String toString() {
        switch (op) {
            case "LIT":
            case "JMP":
            case "JZ":
            case "INT":
            case "FREE":
                return op + " " + a[0];
            case "LOD":
            case "STO":
            case "LDA":
            case "CHK":
                return op + " " + a[0] + " " + a[1];
            case "CALL":
            case "CALF":
                return op + " " + a[0] + " " + a[1] + " " + a[2];
            case "OUTS":
                return "OUTS \"" + esc(s) + "\"";
            default:
                return op;
        }
    }
}
