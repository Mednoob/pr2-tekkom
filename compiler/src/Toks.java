/**
 * Token codes shared by the JFlex-generated lexer (Lexer.java) and the
 * recursive-descent parser (Parser.java).
 */
public interface Toks {
    int EOF = 0;

    int IDENT = 1;
    int INT = 2;
    int STRING = 3;

    // keywords
    int IF = 10;
    int THEN = 11;
    int ELSE = 12;
    int END = 13;
    int REPEAT = 14;
    int UNTIL = 15;
    int LOOP = 16;
    int EXIT = 17;
    int PUT = 18;
    int GET = 19;
    int VAR = 20;
    int FUNC = 21;
    int PROC = 22;
    int INTEGER = 23;
    int BOOLEAN = 24;
    int TRUE = 25;
    int FALSE = 26;
    int SKIP = 27;

    // punctuation / operators
    int LBRACE = 30;
    int RBRACE = 31;
    int LPAREN = 32;
    int RPAREN = 33;
    int LBRACKET = 34;
    int RBRACKET = 35;
    int SEMI = 36;
    int COLON = 37;
    int COMMA = 38;
    int ASSIGN = 39;
    int EQ = 40;
    int NE = 41;
    int LT = 42;
    int GT = 43;
    int LE = 44;
    int GE = 45;
    int PLUS = 46;
    int MINUS = 47;
    int STAR = 48;
    int SLASH = 49;
    int AMP = 50;
    int PIPE = 51;
    int TILDE = 52;

    static String describe(int t) {
        switch (t) {
            case EOF: return "end of input";
            case IDENT: return "identifier";
            case INT: return "integer literal";
            case STRING: return "text literal";
            case IF: return "'if'";
            case THEN: return "'then'";
            case ELSE: return "'else'";
            case END: return "'end'";
            case REPEAT: return "'repeat'";
            case UNTIL: return "'until'";
            case LOOP: return "'loop'";
            case EXIT: return "'exit'";
            case PUT: return "'put'";
            case GET: return "'get'";
            case VAR: return "'var'";
            case FUNC: return "'func'";
            case PROC: return "'proc'";
            case INTEGER: return "'integer'";
            case BOOLEAN: return "'boolean'";
            case TRUE: return "'true'";
            case FALSE: return "'false'";
            case SKIP: return "'skip'";
            case LBRACE: return "'{'";
            case RBRACE: return "'}'";
            case LPAREN: return "'('";
            case RPAREN: return "')'";
            case LBRACKET: return "'['";
            case RBRACKET: return "']'";
            case SEMI: return "';'";
            case COLON: return "':'";
            case COMMA: return "','";
            case ASSIGN: return "':='";
            case EQ: return "'='";
            case NE: return "'#'";
            case LT: return "'<'";
            case GT: return "'>'";
            case LE: return "'<='";
            case GE: return "'>='";
            case PLUS: return "'+'";
            case MINUS: return "'-'";
            case STAR: return "'*'";
            case SLASH: return "'/'";
            case AMP: return "'&'";
            case PIPE: return "'|'";
            case TILDE: return "'~'";
            default: return "token(" + t + ")";
        }
    }
}
