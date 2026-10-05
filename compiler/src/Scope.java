import java.util.LinkedHashMap;
import java.util.Map;

/** A lexical scope: a map from identifier to symbol table entry. */
public class Scope {
    public int level;
    public Map<String, Sym> map = new LinkedHashMap<String, Sym>();

    public Scope(int level) {
        this.level = level;
    }
}
