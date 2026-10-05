/** Types of the language. */
public enum Type {
    INT, BOOL, VOID, ERROR, NONE;

    @Override
    public String toString() {
        switch (this) {
            case INT:   return "integer";
            case BOOL:  return "boolean";
            case VOID:  return "void";
            case ERROR: return "<error>";
            default:    return "<none>";
        }
    }
}
