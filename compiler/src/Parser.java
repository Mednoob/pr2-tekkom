import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Recursive-descent parser, context checker (C-rules) and code generator
 * (R-rules) for the language of CourseGrammar2.
 *
 * The parser works in one pass.  Type information flows up the parse tree
 * (each expression method returns its {@link Type}); the C-rules are the
 * checks performed on the way and the R-rules are the instructions emitted
 * by {@link #emit}.
 */
public class Parser implements Toks {

    /** Number of hidden cells in front of every frame's locals:
     *  [0]=static link, [1]=dynamic link (caller bp), [2]=saved free pointer,
     *  [3]=function result slot.  Local variable 0 lives at frame+4. */
    public static final int FRAME_BASE = 4;

    // ---------------------------------------------------------------- state

    private final Lexer lex;

    private int    tok;
    private String ttext = "";
    private int    tline = 1;
    private int    tcol  = 1;
    private int    tint  = 0;
    private String tstr  = "";

    private final List<Instr> code = new ArrayList<Instr>();

    private final Deque<Scope>   scopes       = new ArrayDeque<Scope>();
    private final Deque<Integer> savedOffsets = new ArrayDeque<Integer>();
    private final Deque<Integer> savedLevels  = new ArrayDeque<Integer>();

    private int level      = 0;   // current lexical (scope) level
    private int frameLevel = 0;   // current run-time frame nesting (calls only)
    private int nextOffset = 0;   // next free local slot in the current frame

    private final List<LoopCtx> loops = new ArrayList<LoopCtx>();

    private boolean showSymbols = false;

    private static class LoopCtx {
        final List<Integer> exits = new ArrayList<Integer>();
    }

    // ------------------------------------------------------------- errors

    public static class CompileError extends RuntimeException {
        public final int line, col;
        public CompileError(String msg, int line, int col) {
            super(msg);
            this.line = line;
            this.col = col;
        }
    }

    private void err(String msg) {
        throw new CompileError(msg, tline, tcol);
    }

    // --------------------------------------------------------------- ctor

    public Parser(Lexer lex) {
        this.lex = lex;
        next();
    }

    public List<Instr> getCode() {
        return code;
    }

    public void setShowSymbols(boolean b) {
        showSymbols = b;
    }

    // ------------------------------------------------------------ helpers

    private void next() {
        try {
            tok = lex.nextToken();
        } catch (java.io.IOException e) {
            throw new RuntimeException("I/O error while reading source: " + e.getMessage());
        }
        ttext = lex.text;
        tline = lex.line;
        tcol  = lex.col;
        tint  = lex.intVal;
        tstr  = lex.strVal;
    }

    private void expect(int t, String what) {
        if (tok != t) {
            err("expected " + what + " but found " + Toks.describe(tok));
        }
        next();
    }

    private String expectIdent() {
        if (tok != IDENT) {
            err("expected an identifier but found " + Toks.describe(tok));
        }
        String s = ttext;
        next();
        return s;
    }

    // --------------------------------------------------------- emitter

    private int emit(String op) {
        code.add(new Instr(op));
        return code.size() - 1;
    }

    private int emit(String op, int a) {
        code.add(new Instr(op, a));
        return code.size() - 1;
    }

    private int emit(String op, int a, int b) {
        code.add(new Instr(op, a, b));
        return code.size() - 1;
    }

    private int emit(String op, int a, int b, int c) {
        code.add(new Instr(op, a, b, c));
        return code.size() - 1;
    }

    private int emitText(String op, String s) {
        code.add(Instr.text(op, s));
        return code.size() - 1;
    }

    private void patch(int idx, int value) {
        code.get(idx).a[0] = value;
    }

    // ----------------------------------------------------- symbol table

    private Sym lookup(String name) {
        for (Scope s : scopes) {
            Sym sym = s.map.get(name);
            if (sym != null) {
                return sym;
            }
        }
        return null;
    }

    private Sym declare(String name, int kind, Type type) {
        Scope s = scopes.peek();
        if (s.map.containsKey(name)) {
            err("identifier '" + name + "' is already declared in this scope");
        }
        Sym sym = new Sym(name, kind, type, level, nextOffset);
        sym.frameLevel = frameLevel;
        s.map.put(name, sym);
        return sym;
    }

    private void enterScope() {
        savedOffsets.push(nextOffset);
        level++;
        scopes.push(new Scope(level));
    }

    private void leaveScope() {
        scopes.pop();
        level--;
        nextOffset = savedOffsets.pop();
    }

    private void enterFuncFrame(Sym f) {
        savedLevels.push(level);
        savedOffsets.push(nextOffset);
        level = f.level + 1;
        frameLevel++;
        nextOffset = 0;
        scopes.push(new Scope(level));
    }

    private void leaveFuncFrame() {
        scopes.pop();
        nextOffset = savedOffsets.pop();
        level = savedLevels.pop();
        frameLevel--;
    }

    // ============================================================= program

    public void parseProgram() {
        level = 0;
        nextOffset = 0;
        scopes.push(new Scope(0));

        expect(LBRACE, "'{'");

        int intAddr = emit("INT", -1);       // R3 (patched after declarations)
        int base = nextOffset;
        parseDeclarations();
        int n = nextOffset - base;
        patch(intAddr, n);                   // R37: allocate scope variables

        if (tok == SEMI) {
            next();
        }
        parseStatements();
        expect(RBRACE, "'}'");

        emit("HALT");                        // R38

        if (tok != EOF) {
            err("unexpected " + Toks.describe(tok) + " after the program body");
        }
        if (showSymbols) {
            dumpSymbols();
        }
    }

    // -------------------------------------------------------- declarations

    private void parseDeclarations() {
        while (tok == VAR || tok == INTEGER || tok == BOOLEAN || tok == PROC) {
            parseDeclaration();
            // The grammar has no separator between declarations, but a ';'
            // is accepted here as a convenience.
            while (tok == SEMI) {
                next();
            }
        }
    }

    private void parseDeclaration() {
        if (tok == VAR) {
            parseVarDecl();
        } else if (tok == PROC) {
            parseProcDecl();
        } else if (tok == INTEGER || tok == BOOLEAN) {
            parseFuncDecl();
        } else {
            err("expected a declaration");
        }
    }

    private Type parseType() {
        if (tok == INTEGER) {
            next();
            return Type.INT;
        }
        if (tok == BOOLEAN) {
            next();
            return Type.BOOL;
        }
        err("expected a type ('integer' or 'boolean') but found " + Toks.describe(tok));
        return Type.ERROR;
    }

    /** 'var' identifier optArrayBound ':' type                                    */
    private void parseVarDecl() {
        next(); // 'var'
        String name = expectIdent();

        boolean isArray = false;
        int size = 1;
        if (tok == LBRACKET) {
            next();
            int bound = constExpr();
            if (bound < 0) {
                err("array bound must be non-negative (R39)");
            }
            expect(RBRACKET, "']'");
            isArray = true;
            size = bound;
        }
        expect(COLON, "':'");
        Type t = parseType();

        Sym s = declare(name, isArray ? Sym.ARRAY : Sym.VAR, t);
        s.isArray = isArray;
        s.size = size;
        nextOffset += size;                  // reserve space (R3/R37)
    }

    /** type 'func' identifier [ '(' parameters ')' ] '=' expression              */
    private void parseFuncDecl() {
        Type retType = parseType();
        expect(FUNC, "'func'");
        String name = expectIdent();

        Sym f = declare(name, Sym.FUNC, retType);
        int jmp = emit("JMP", -1);           // R7: jump over the body
        f.codeAddr = code.size();            // entry point of the function

        enterFuncFrame(f);
        if (tok == LPAREN) {
            parseParams(f);
        }
        f.paramCount = f.paramTypes.size();
        expect(EQ, "'='");
        Type et = expression();
        if (et != retType) {                 // C36
            err("function '" + name + "' must return " + retType
                    + " but its body has type " + et);
        }
        emit("STRES");                       // store result in slot 3
        emit("RETF");                        // R43
        leaveFuncFrame();

        patch(jmp, code.size());
    }

    /** 'proc' identifier [ '(' parameters ')' ] scope                            */
    private void parseProcDecl() {
        next(); // 'proc'
        String name = expectIdent();

        Sym p = declare(name, Sym.PROC, Type.VOID);
        int jmp = emit("JMP", -1);           // R7: jump over the body
        p.codeAddr = code.size();

        enterFuncFrame(p);
        if (tok == LPAREN) {
            parseParams(p);
        }
        p.paramCount = p.paramTypes.size();
        parseScopeInFrame();
        emit("RET");                         // R42
        leaveFuncFrame();

        patch(jmp, code.size());
    }

    /** parameters: identifier ':' type (',' identifier ':' type)*                 */
    private void parseParams(Sym f) {
        expect(LPAREN, "'('");
        if (tok != RPAREN) {
            parseParam(f);
            while (tok == COMMA) {
                next();
                parseParam(f);
            }
        }
        expect(RPAREN, "')'");
    }

    private void parseParam(Sym f) {
        String name = expectIdent();
        expect(COLON, "':'");
        Type t = parseType();
        Sym p = declare(name, Sym.PARAM, t);
        p.isParam = true;
        p.size = 1;
        nextOffset += 1;
        f.paramTypes.add(t);
    }

    // ------------------------------------------------------------ statements

    private void parseStatements() {
        parseStatements(true);
    }

    /**
     * @param allowSemi when true, ';' is accepted as a statement separator
     *        (used inside scopes).  Inside a block expression the trailing ';'
     *        that separates the statements from the result expression must be
     *        preserved, so semicolons are not skipped there.
     */
    private void parseStatements(boolean allowSemi) {
        while (true) {
            if (allowSemi && tok == SEMI) {
                next();
                continue;
            }
            if (!startsStatement(tok)) {
                break;
            }
            parseStatement();
        }
    }

    private boolean startsStatement(int t) {
        return t == IDENT || t == IF || t == REPEAT || t == LOOP
                || t == EXIT || t == PUT || t == GET || t == LBRACE;
    }

    private void parseStatement() {
        switch (tok) {
            case IDENT:  parseAssignOrCall(); break;
            case IF:     parseIf();           break;
            case REPEAT: parseRepeat();       break;
            case LOOP:   parseLoop();         break;
            case EXIT:   parseExit();         break;
            case PUT:    parsePut();          break;
            case GET:    parseGet();          break;
            case LBRACE:
                enterScope();                 // C0
                parseScopeBody();
                leaveScope();                 // C2
                break;
            default:
                err("expected a statement but found " + Toks.describe(tok));
        }
    }

    /** A '{' declarations ';' statements '}' block used as a statement.           */
    private void parseScopeBody() {
        expect(LBRACE, "'{'");
        int intAddr = emit("INT", -1);
        int base = nextOffset;
        parseDeclarations();
        int n = nextOffset - base;
        patch(intAddr, n);
        if (tok == SEMI) {
            next();
        }
        parseStatements();
        expect(RBRACE, "'}'");
        emit("FREE", n);                     // R5: leave statement scope
    }

    /** Like {@link #parseScopeBody} but the frame was already entered and it is
     *  left by RET, so no FREE is emitted. Used for procedure bodies.            */
    private void parseScopeInFrame() {
        expect(LBRACE, "'{'");
        int intAddr = emit("INT", -1);
        int base = nextOffset;
        parseDeclarations();
        int n = nextOffset - base;
        patch(intAddr, n);
        if (tok == SEMI) {
            next();
        }
        parseStatements();
        expect(RBRACE, "'}'");
    }

    /** identifier assignOrCall                                                   */
    private void parseAssignOrCall() {
        String name = ttext;
        Sym s = lookup(name);                // C6
        if (s == null) {
            err("undeclared identifier '" + name + "'");
        }
        next();

        if (tok == ASSIGN) {                 // scalar assignment
            if (s.kind == Sym.ARRAY) {
                err("array '" + name + "' must be indexed before assignment"); // C20
            }
            if (s.kind != Sym.VAR && s.kind != Sym.PARAM) {
                err("'" + name + "' is not a variable");
            }
            next();
            Type et = expression();
            checkAssign(s, et, name);        // C16
            emit("STO", frameLevel - s.frameLevel, s.offset);        // R33
        } else if (tok == LBRACKET) {        // array element assignment
            if (s.kind != Sym.ARRAY) {
                err("'" + name + "' is not an array");     // C21
            }
            next();
            emit("LDA", frameLevel - s.frameLevel, s.offset);        // R40
            Type it = simpleExpression();
            if (it != Type.INT) {
                err("array index must be integer but has type " + it); // C12
            }
            expect(RBRACKET, "']'");
            emit("CHK", 0, s.size - 1);                     // R41
            emit("ADD");
            expect(ASSIGN, "':='");
            Type et = expression();
            checkAssign(s, et, name);
            emit("STI");                                    // R33
        } else if (tok == LPAREN) {          // procedure call with arguments
            if (s.kind != Sym.PROC) {
                err("'" + name + "' is not a procedure");   // C28
            }
            int argc = parseArgList(s, name);
            emit("CALL", s.codeAddr, argc, frameLevel - s.frameLevel); // R44
        } else {                             // procedure call without arguments
            if (s.kind != Sym.PROC) {
                err("'" + name + "' is not a procedure");   // C28
            }
            if (s.paramCount != 0) {
                err("procedure '" + name + "' needs " + s.paramCount
                        + " argument(s)");                   // C29
            }
            emit("CALL", s.codeAddr, 0, frameLevel - s.frameLevel);
        }
    }

    private void checkAssign(Sym s, Type et, String name) {
        if (et != s.type) {                                     // C16
            err("cannot assign a value of type " + et + " to '" + name
                    + "' of type " + s.type);
        }
    }

    /** '(' expression (',' expression)* ')' with C30/C31/C32 checks.
     *  Returns the number of arguments; the arguments are already on the
     *  operand stack. */
    private int parseArgList(Sym s, String name) {
        expect(LPAREN, "'('");
        List<Type> at = new ArrayList<Type>();
        if (tok != RPAREN) {
            at.add(expression());            // C34
            while (tok == COMMA) {
                next();
                at.add(expression());        // C34
            }
        }
        expect(RPAREN, "')'");
        if (at.size() != s.paramCount) {                         // C32
            err("'" + name + "' expects " + s.paramCount + " argument(s) but got "
                    + at.size());
        }
        for (int i = 0; i < at.size(); i++) {                    // C31
            if (at.get(i) != s.paramTypes.get(i)) {
                err("argument " + (i + 1) + " of '" + name + "' must be "
                        + s.paramTypes.get(i) + " but got " + at.get(i));
            }
        }
        return at.size();
    }

    /** 'if' expression 'then' statements optElse 'end' 'if'                       */
    private void parseIf() {
        next(); // 'if'
        Type c = expression();
        if (c != Type.BOOL) {                // C13
            err("'if' condition must be boolean but has type " + c);
        }
        int jz = emit("JZ", -1);             // R8: forward conditional branch
        expect(THEN, "'then'");
        parseStatements();
        if (tok == ELSE) {
            next();
            int jmp = emit("JMP", -1);       // jump over the else branch
            patch(jz, code.size());          // R10
            parseStatements();
            expect(END, "'end'");
            expect(IF, "'if'");
            patch(jmp, code.size());
        } else {
            expect(END, "'end'");
            expect(IF, "'if'");
            patch(jz, code.size());
        }
    }

    /** 'repeat' statements 'until' expression                                     */
    private void parseRepeat() {
        next(); // 'repeat'
        int start = code.size();             // R11
        parseStatements();
        expect(UNTIL, "'until'");
        Type c = expression();
        if (c != Type.BOOL) {                // C13
            err("'until' condition must be boolean but has type " + c);
        }
        emit("JZ", start);                   // R12: backward branch while false
    }

    /** 'loop' statements 'end' 'loop'                                             */
    private void parseLoop() {
        next(); // 'loop'
        LoopCtx ctx = new LoopCtx();
        loops.add(ctx);
        int start = code.size();
        parseStatements();
        expect(END, "'end'");
        expect(LOOP, "'loop'");
        emit("JMP", start);
        int end = code.size();
        for (int e : ctx.exits) {
            patch(e, end);
        }
        loops.remove(loops.size() - 1);
    }

    /** 'exit'                                                                     */
    private void parseExit() {
        next();
        if (loops.isEmpty()) {
            err("'exit' used outside of a 'loop'");
        }
        int j = emit("JMP", -1);             // R52
        loops.get(loops.size() - 1).exits.add(j);
    }

    /** 'put' outputs                                                              */
    private void parsePut() {
        next(); // 'put'
        parseOutput();
        while (tok == COMMA) {
            next();
            parseOutput();
        }
        emit("OUTNL");                       // R30
    }

    /** output: expression | Text | 'skip'                                        */
    private void parseOutput() {
        if (tok == STRING) {
            emitText("OUTS", tstr);          // R29
            next();
        } else if (tok == SKIP) {
            emit("OUTNL");                   // R30
            next();
        } else {
            Type t = expression();
            if (t == Type.VOID || t == Type.NONE || t == Type.ERROR) {
                err("cannot print a value of type " + t);
            }
            emit("OUT");                     // R28
        }
    }

    /** 'get' inputs                                                               */
    private void parseGet() {
        next(); // 'get'
        parseInput();
        while (tok == COMMA) {
            next();
            parseInput();
        }
    }

    /** input: identifier optSubscript                                             */
    private void parseInput() {
        String name = ttext;
        Sym s = lookup(name);                // C6
        if (s == null) {
            err("undeclared identifier '" + name + "'");
        }
        next();

        if (tok == LBRACKET) {
            if (s.kind != Sym.ARRAY) {
                err("'" + name + "' is not an array");       // C21
            }
            if (s.type != Type.INT) {
                err("input target must be integer but '" + name + "' has type "
                        + s.type);                            // C17
            }
            next();
            emit("LDA", frameLevel - s.frameLevel, s.offset);           // R40
            Type it = simpleExpression();
            if (it != Type.INT) {
                err("array index must be integer but has type " + it); // C12
            }
            expect(RBRACKET, "']'");
            emit("CHK", 0, s.size - 1);                        // R41
            emit("ADD");
            emit("IN");                                       // R27
            emit("STI");
        } else {
            if (s.kind == Sym.FUNC || s.kind == Sym.PROC) {
                err("'" + name + "' is not a variable");
            }
            if (s.isArray) {
                err("array '" + name + "' must be indexed");   // C20
            }
            if (s.type != Type.INT) {
                err("input target must be integer but '" + name + "' has type "
                        + s.type);                            // C17
            }
            emit("IN");                                       // R27
            emit("STO", frameLevel - s.frameLevel, s.offset);
        }
    }

    // ============================================================ expressions

    private Type expression() {
        Type t = simpleExpression();
        if (tok == EQ || tok == NE) {
            int op = tok;
            next();
            Type r = simpleExpression();
            if (!((t == Type.INT && r == Type.INT) || (t == Type.BOOL && r == Type.BOOL))) {
                err("'=' and '#' require operands of the same type "
                        + "(int/int or bool/bool) but got " + t + " and " + r); // C14
            }
            emit(op == EQ ? "EQ" : "NE");    // R21 / R22
            return Type.BOOL;
        }
        if (tok == LT || tok == GT || tok == LE || tok == GE) {
            int op = tok;
            next();
            Type r = simpleExpression();
            if (t != Type.INT || r != Type.INT) {              // C15
                err("'<', '>', '<=' and '>=' require integer operands but got "
                        + t + " and " + r);
            }
            switch (op) {
                case LT: emit("LT"); break;  // R23
                case LE: emit("LE"); break;  // R24
                case GT: emit("GT"); break;  // R25
                default: emit("GE"); break;  // R26
            }
            return Type.BOOL;
        }
        return t;
    }

    private Type simpleExpression() {
        Type t = term();
        while (tok == PLUS || tok == MINUS || tok == PIPE) {
            int op = tok;
            next();
            Type r = term();
            if (op == PIPE) {
                if (t != Type.BOOL || r != Type.BOOL) {         // C13
                    err("'|' requires boolean operands but got " + t + " and " + r);
                }
                emit("OR");                                  // R20
                t = Type.BOOL;
            } else {
                if (t != Type.INT || r != Type.INT) {           // C12
                    err((op == PLUS ? "'+'" : "'-'")
                            + " requires integer operands but got " + t + " and " + r);
                }
                emit(op == PLUS ? "ADD" : "SUB");            // R14 / R15
                t = Type.INT;
            }
        }
        return t;
    }

    private Type term() {
        Type t = factor();
        while (tok == STAR || tok == SLASH || tok == AMP) {
            int op = tok;
            next();
            Type r = factor();
            if (op == AMP) {
                if (t != Type.BOOL || r != Type.BOOL) {         // C13
                    err("'&' requires boolean operands but got " + t + " and " + r);
                }
                emit("AND");                                 // R19
                t = Type.BOOL;
            } else {
                if (t != Type.INT || r != Type.INT) {           // C12
                    err((op == STAR ? "'*'" : "'/'")
                            + " requires integer operands but got " + t + " and " + r);
                }
                emit(op == STAR ? "MUL" : "DIV");            // R16 / R17
                t = Type.INT;
            }
        }
        return t;
    }

    private Type factor() {
        if (tok == PLUS) {
            next();
            Type t = factor();
            if (t != Type.INT) {
                err("unary '+' requires an integer operand but got " + t); // C12
            }
            return Type.INT;
        }
        if (tok == MINUS) {
            next();
            Type t = factor();
            if (t != Type.INT) {
                err("unary '-' requires an integer operand but got " + t); // C12
            }
            emit("NEG");                                     // R13
            return Type.INT;
        }
        if (tok == TILDE) {
            next();
            Type t = factor();
            if (t != Type.BOOL) {
                err("unary '~' requires a boolean operand but got " + t); // C13
            }
            emit("NOT");                                     // R18
            return Type.BOOL;
        }
        return primary();
    }

    private Type primary() {
        if (tok == INT) {
            emit("LIT", tint);                               // R36
            next();
            return Type.INT;
        }
        if (tok == TRUE) {
            emit("LIT", 1);                                  // R35
            next();
            return Type.BOOL;
        }
        if (tok == FALSE) {
            emit("LIT", 0);                                  // R34
            next();
            return Type.BOOL;
        }
        if (tok == LPAREN) {
            next();
            Type t = expression();
            expect(RPAREN, "')'");
            return t;
        }
        if (tok == LBRACE) {
            return blockExpression();
        }
        if (tok == IDENT) {
            return identifierExpression();
        }
        err("expected an expression but found " + Toks.describe(tok));
        return Type.ERROR;
    }

    /** '{' declarations ';' statements ';' expression '}'                         */
    private Type blockExpression() {
        enterScope();                        // C0
        expect(LBRACE, "'{'");
        int intAddr = emit("INT", -1);
        int base = nextOffset;
        parseDeclarations();
        int n = nextOffset - base;
        patch(intAddr, n);
        if (tok == SEMI) {
            next();
        }
        parseStatements(false);
        expect(SEMI, "';' before the result expression");
        Type t = expression();
        expect(RBRACE, "'}'");
        emit("FREE", n);                     // R6: drop the scope
        leaveScope();                        // C2
        return t;
    }

    /** identifier subsOrCall                                                      */
    private Type identifierExpression() {
        String name = ttext;
        Sym s = lookup(name);                // C6
        if (s == null) {
            err("undeclared identifier '" + name + "'");
        }
        next();

        if (tok == LPAREN) {                 // function call with arguments
            if (s.kind != Sym.FUNC) {
                err("'" + name + "' is not a function");       // C33
            }
            int argc = parseArgList(s, name);
            emit("CALF", s.codeAddr, argc, frameLevel - s.frameLevel);   // R46/R47
            return s.type;
        }
        if (tok == LBRACKET) {               // array element
            if (s.kind != Sym.ARRAY) {
                err("'" + name + "' is not an array");         // C21
            }
            next();
            emit("LDA", frameLevel - s.frameLevel, s.offset);            // R40
            Type it = simpleExpression();
            if (it != Type.INT) {
                err("array index must be integer but has type " + it); // C12
            }
            expect(RBRACKET, "']'");
            emit("CHK", 0, s.size - 1);                        // R41
            emit("ADD");
            emit("LDI");                                       // R32
            return s.type;
        }
        // bare identifier
        if (s.kind == Sym.FUNC) {            // C37 -> function with no arguments
            if (s.paramCount != 0) {                            // C29
                err("function '" + name + "' needs " + s.paramCount + " argument(s)");
            }
            emit("CALF", s.codeAddr, 0, frameLevel - s.frameLevel);      // R46/R47
            return s.type;
        }
        if (s.kind == Sym.VAR || s.kind == Sym.PARAM) {
            if (s.isArray) {                                    // C20
                err("array '" + name + "' must be indexed");
            }
            emit("LOD", frameLevel - s.frameLevel, s.offset);            // R32
            return s.type;
        }
        if (s.kind == Sym.ARRAY) {                              // C20
            err("array '" + name + "' must be indexed");
        }
        err("'" + name + "' cannot be used in an expression"); // procedure
        return Type.ERROR;
    }

    // ==================================================== constant expressions

    private int constExpr() {
        int v = constTerm();
        while (tok == PLUS || tok == MINUS || tok == PIPE) {
            int op = tok;
            next();
            int r = constTerm();
            if (op == PLUS) {
                v += r;
            } else if (op == MINUS) {
                v -= r;
            } else {
                err("array bound must be a constant integer expression");
            }
        }
        return v;
    }

    private int constTerm() {
        int v = constFactor();
        while (tok == STAR || tok == SLASH || tok == AMP) {
            int op = tok;
            next();
            int r = constFactor();
            if (op == STAR) {
                v *= r;
            } else if (op == SLASH) {
                if (r == 0) {
                    err("division by zero in array bound");
                }
                v /= r;
            } else {
                err("array bound must be a constant integer expression");
            }
        }
        return v;
    }

    private int constFactor() {
        if (tok == PLUS) {
            next();
            return constFactor();
        }
        if (tok == MINUS) {
            next();
            return -constFactor();
        }
        if (tok == INT) {
            int v = tint;
            next();
            return v;
        }
        if (tok == LPAREN) {
            next();
            int v = constExpr();
            expect(RPAREN, "')'");
            return v;
        }
        err("array bound must be a constant integer expression");
        return 0;
    }

    // ============================================================ diagnostics

    private void dumpSymbols() {
        System.out.println("---- symbol table (innermost scope first) ----");
        int i = 0;
        for (Scope s : scopes) {
            System.out.println("scope level " + s.level + ":");
            for (Sym sym : s.map.values()) {
                StringBuilder b = new StringBuilder();
                b.append("   ").append(sym.name).append(" : ").append(sym.kindName());
                if (sym.type != Type.VOID) {
                    b.append(" of ").append(sym.type);
                }
                if (sym.isArray) {
                    b.append("[").append(sym.size).append("]");
                }
                b.append("  level=").append(sym.level).append(" offset=").append(sym.offset);
                if (sym.kind == Sym.FUNC || sym.kind == Sym.PROC) {
                    b.append(" entry=").append(sym.codeAddr);
                }
                System.out.println(b.toString());
            }
            i++;
        }
        System.out.println("----------------------------------------------");
    }
}
