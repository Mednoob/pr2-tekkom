import java.util.ArrayList;
import java.util.List;

/** One entry of the symbol table. */
public class Sym {
    public static final int VAR   = 1;
    public static final int ARRAY = 2;
    public static final int FUNC  = 3;
    public static final int PROC  = 4;
    public static final int PARAM = 5;

    public String name;
    public int    kind;
    public Type   type;
    public int    level;      // lexical level of the declaration
    public int    frameLevel; // run-time frame nesting of the declaration
    public int    offset;     // offset inside the frame (locals start at 0)
    public int    size = 1;   // number of cells (array length for ARRAY)
    public boolean isArray = false;
    public boolean isParam = false;

    public int        paramCount = 0;
    public List<Type> paramTypes = new ArrayList<Type>();
    public int        codeAddr = -1;   // entry address for FUNC/PROC

    public Sym(String name, int kind, Type type, int level, int offset) {
        this.name = name;
        this.kind = kind;
        this.type = type;
        this.level = level;
        this.offset = offset;
    }

    public String kindName() {
        switch (kind) {
            case VAR:   return "variable";
            case ARRAY: return "array";
            case FUNC:  return "function";
            case PROC:  return "procedure";
            case PARAM: return "parameter";
            default:    return "?";
        }
    }
}
