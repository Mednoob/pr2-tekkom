import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Walks the {@link Ast} produced by the JFlex+CUP parser, applies the context
 * checking rules (C0..C40) and the code generation rules (R0..R53), and emits
 * the same stack-machine instructions as the hand-written compiler
 * ({@link Parser}).
 */
public class TreeCompiler {

    public static final int FRAME_BASE = 4;

    // ---------------------------------------------------------------- state

    private final List<Instr> code = new ArrayList<Instr>();

    private final Deque<Scope>   scopes       = new ArrayDeque<Scope>();
    private final Deque<Integer> savedOffsets = new ArrayDeque<Integer>();
    private final Deque<Integer> savedLevels  = new ArrayDeque<Integer>();

    private int level      = 0;
    private int frameLevel = 0;
    private int nextOffset = 0;

    private final List<LoopCtx> loops = new ArrayList<LoopCtx>();

    private static class LoopCtx {
        final List<Integer> exits = new ArrayList<Integer>();
    }

    // -------------------------------------------------------------- errors

    public static class CompileError extends RuntimeException {
        public final int line;
        public CompileError(String msg, int line) {
            super(msg);
            this.line = line;
        }
    }

    private void err(int line, String msg) {
        throw new CompileError(msg, line);
    }

    public List<Instr> getCode() {
        return code;
    }

    // ------------------------------------------------------------- emitter

    private int emit(String op)            { code.add(new Instr(op)); return code.size() - 1; }
    private int emit(String op, int a)     { code.add(new Instr(op, a)); return code.size() - 1; }
    private int emit(String op, int a, int b) { code.add(new Instr(op, a, b)); return code.size() - 1; }
    private int emit(String op, int a, int b, int c) { code.add(new Instr(op, a, b, c)); return code.size() - 1; }
    private int emitText(String op, String s) { code.add(Instr.text(op, s)); return code.size() - 1; }
    private void patch(int idx, int value) { code.get(idx).a[0] = value; }

    // --------------------------------------------------------- symbol table

    private Sym lookup(String name) {
        for (Scope s : scopes) {
            Sym sym = s.map.get(name);
            if (sym != null) {
                return sym;
            }
        }
        return null;
    }

    private Sym declare(String name, int kind, Type type, int line) {
        Scope s = scopes.peek();
        if (s.map.containsKey(name)) {
            err(line, "identifier '" + name + "' is already declared in this scope");
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

    // =============================================================== program

    public List<Instr> compile(Ast.Program p) {
        level = 0;
        frameLevel = 0;
        nextOffset = 0;
        scopes.push(new Scope(0));

        int intAddr = emit("INT", -1);              // R3 (patched below)
        int base = nextOffset;
        compileDecls(p.decls);
        int n = nextOffset - base;
        patch(intAddr, n);                          // R37

        compileStmts(p.stmts);
        emit("HALT");                               // R38
        return code;
    }

    // --------------------------------------------------------- declarations

    private void compileDecls(List<Ast.Decl> decls) {
        for (Ast.Decl d : decls) {
            compileDecl(d);
        }
    }

    private void compileDecl(Ast.Decl d) {
        if (d instanceof Ast.VarDecl) {
            compileVarDecl((Ast.VarDecl) d);
        } else if (d instanceof Ast.FuncDecl) {
            compileFuncDecl((Ast.FuncDecl) d);
        } else if (d instanceof Ast.ProcDecl) {
            compileProcDecl((Ast.ProcDecl) d);
        } else {
            err(d.line, "unknown declaration");
        }
    }

    /** 'var' identifier optArrayBound ':' type  (C3 C4 C18/C19 C5) */
    private void compileVarDecl(Ast.VarDecl d) {
        boolean isArray = false;
        int size = 1;
        if (d.bound != null) {
            int bound = constEval(d.bound);
            if (bound < 0) {
                err(d.line, "array bound must be non-negative (R39)");
            }
            isArray = true;
            size = bound;
        }
        Sym s = declare(d.name, isArray ? Sym.ARRAY : Sym.VAR, d.type, d.line);
        s.isArray = isArray;                        // C18 / C19
        s.size = size;
        nextOffset += size;
    }

    /** type 'func' identifier ... '=' expression  (C3 C26 C22 C23 C5 C36) */
    private void compileFuncDecl(Ast.FuncDecl d) {
        Sym f = declare(d.name, Sym.FUNC, d.retType, d.line);
        int jmp = emit("JMP", -1);                  // R7
        f.codeAddr = code.size();

        enterFuncFrame(f);
        for (Ast.Param p : d.params) {
            Sym ps = declare(p.name, Sym.PARAM, p.type, p.line);   // C25
            ps.isParam = true;
            ps.size = 1;
            nextOffset += 1;
            f.paramTypes.add(p.type);
        }
        f.paramCount = d.params.size();

        Type et = compileExpr(d.body);
        if (et != d.retType) {                      // C36
            err(d.line, "function '" + d.name + "' must return " + d.retType
                    + " but its body has type " + et);
        }
        emit("STRES");
        emit("RETF");                               // R43
        leaveFuncFrame();

        patch(jmp, code.size());
    }

    /** 'proc' identifier ... scope  (C3 C24 C22) */
    private void compileProcDecl(Ast.ProcDecl d) {
        Sym pr = declare(d.name, Sym.PROC, Type.VOID, d.line);
        int jmp = emit("JMP", -1);                  // R7
        pr.codeAddr = code.size();

        enterFuncFrame(pr);
        for (Ast.Param p : d.params) {
            Sym ps = declare(p.name, Sym.PARAM, p.type, p.line);   // C25
            ps.isParam = true;
            ps.size = 1;
            nextOffset += 1;
            pr.paramTypes.add(p.type);
        }
        pr.paramCount = d.params.size();

        compileScopeInFrame(d.body.decls, d.body.stmts);
        emit("RET");                                // R42
        leaveFuncFrame();

        patch(jmp, code.size());
    }

    /** A procedure body scope; the frame was already entered by the caller. */
    private void compileScopeInFrame(List<Ast.Decl> decls, List<Ast.Stmt> stmts) {
        int intAddr = emit("INT", -1);
        int base = nextOffset;
        compileDecls(decls);
        int n = nextOffset - base;
        patch(intAddr, n);
        compileStmts(stmts);
    }

    // ------------------------------------------------------------ statements

    private void compileStmts(List<Ast.Stmt> stmts) {
        for (Ast.Stmt s : stmts) {
            compileStmt(s);
        }
    }

    private void compileStmt(Ast.Stmt s) {
        if (s instanceof Ast.Assign) {
            compileAssign((Ast.Assign) s);
        } else if (s instanceof Ast.ArrayAssign) {
            compileArrayAssign((Ast.ArrayAssign) s);
        } else if (s instanceof Ast.CallStmt) {
            compileCallStmt((Ast.CallStmt) s);
        } else if (s instanceof Ast.If) {
            compileIf((Ast.If) s);
        } else if (s instanceof Ast.Repeat) {
            compileRepeat((Ast.Repeat) s);
        } else if (s instanceof Ast.Loop) {
            compileLoop((Ast.Loop) s);
        } else if (s instanceof Ast.Exit) {
            compileExit((Ast.Exit) s);
        } else if (s instanceof Ast.Put) {
            compilePut((Ast.Put) s);
        } else if (s instanceof Ast.Get) {
            compileGet((Ast.Get) s);
        } else if (s instanceof Ast.Block) {
            compileBlock((Ast.Block) s);
        } else {
            err(s.line, "unknown statement");
        }
    }

    /** identifier ':=' expression  (C6 C20 C16 R31/R33) */
    private void compileAssign(Ast.Assign a) {
        Sym s = lookup(a.name);                     // C6
        if (s == null) {
            err(a.line, "undeclared identifier '" + a.name + "'");
        }
        if (s.kind == Sym.ARRAY) {
            err(a.line, "array '" + a.name + "' must be indexed before assignment"); // C20
        }
        if (s.kind != Sym.VAR && s.kind != Sym.PARAM) {
            err(a.line, "'" + a.name + "' is not a variable");
        }
        Type et = compileExpr(a.rhs);
        checkAssign(s, et, a.name, a.line);         // C16
        emit("STO", frameLevel - s.frameLevel, s.offset);  // R33
    }

    /** identifier '[' subscript ']' ':=' expression  (C21 C12 C16 R40/R41/R33) */
    private void compileArrayAssign(Ast.ArrayAssign a) {
        Sym s = lookup(a.name);
        if (s == null) {
            err(a.line, "undeclared identifier '" + a.name + "'");
        }
        if (s.kind != Sym.ARRAY) {
            err(a.line, "'" + a.name + "' is not an array"); // C21
        }
        emit("LDA", frameLevel - s.frameLevel, s.offset);   // R40
        Type it = compileExpr(a.index);
        if (it != Type.INT) {
            err(a.index.line, "array index must be integer but has type " + it); // C12
        }
        emit("CHK", 0, s.size - 1);                 // R41
        emit("ADD");
        Type et = compileExpr(a.rhs);
        checkAssign(s, et, a.name, a.line);         // C16
        emit("STI");                                // R33
    }

    /** identifier [ '(' arguments ')' ]  (C28 C29 C31 C32) */
    private void compileCallStmt(Ast.CallStmt c) {
        Sym s = lookup(c.name);
        if (s == null) {
            err(c.line, "undeclared identifier '" + c.name + "'");
        }
        if (s.kind != Sym.PROC) {
            err(c.line, "'" + c.name + "' is not a procedure"); // C28
        }
        compileArgs(s, c.name, c.args, c.line);
        emit("CALL", s.codeAddr, c.args.size(), frameLevel - s.frameLevel); // R44
    }

    private void compileIf(Ast.If f) {
        Type c = compileExpr(f.cond);
        if (c != Type.BOOL) {                       // C13
            err(f.cond.line, "'if' condition must be boolean but has type " + c);
        }
        int jz = emit("JZ", -1);                    // R8
        compileStmts(f.thenStmts);
        if (!f.elseStmts.isEmpty()) {
            int jmp = emit("JMP", -1);
            patch(jz, code.size());                 // R10
            compileStmts(f.elseStmts);
            patch(jmp, code.size());
        } else {
            patch(jz, code.size());
        }
    }

    private void compileRepeat(Ast.Repeat r) {
        int start = code.size();                    // R11
        compileStmts(r.body);
        Type c = compileExpr(r.cond);
        if (c != Type.BOOL) {                       // C13
            err(r.cond.line, "'until' condition must be boolean but has type " + c);
        }
        emit("JZ", start);                          // R12
    }

    private void compileLoop(Ast.Loop l) {
        LoopCtx ctx = new LoopCtx();
        loops.add(ctx);
        int start = code.size();
        compileStmts(l.body);
        emit("JMP", start);                         // R51/R53
        int end = code.size();
        for (int e : ctx.exits) {
            patch(e, end);
        }
        loops.remove(loops.size() - 1);
    }

    private void compileExit(Ast.Exit x) {
        if (loops.isEmpty()) {
            err(x.line, "'exit' used outside of a 'loop'");
        }
        int j = emit("JMP", -1);                    // R52
        loops.get(loops.size() - 1).exits.add(j);
    }

    private void compilePut(Ast.Put p) {
        for (Object item : p.items) {
            if (item == null) {
                emit("OUTNL");                      // R30 (skip)
            } else if (item instanceof String) {
                emitText("OUTS", (String) item);    // R29
            } else {
                Type t = compileExpr((Ast.Expr) item);
                if (t != Type.INT && t != Type.BOOL) {
                    err(((Ast.Expr) item).line, "cannot print a value of type " + t);
                }
                emit("OUT");                        // R28
            }
        }
        emit("OUTNL");                              // R30
    }

    private void compileGet(Ast.Get g) {
        for (Ast.GetItem it : g.items) {
            Sym s = lookup(it.name);
            if (s == null) {
                err(it.line, "undeclared identifier '" + it.name + "'");
            }
            if (it.index != null) {
                if (s.kind != Sym.ARRAY) {
                    err(it.line, "'" + it.name + "' is not an array"); // C21
                }
                if (s.type != Type.INT) {
                    err(it.line, "input target must be integer but '" + it.name
                            + "' has type " + s.type);                 // C17
                }
                emit("LDA", frameLevel - s.frameLevel, s.offset);      // R40
                Type ix = compileExpr(it.index);
                if (ix != Type.INT) {
                    err(it.index.line, "array index must be integer but has type " + ix); // C12
                }
                emit("CHK", 0, s.size - 1);                            // R41
                emit("ADD");
                emit("IN");                                            // R27
                emit("STI");
            } else {
                if (s.kind == Sym.FUNC || s.kind == Sym.PROC) {
                    err(it.line, "'" + it.name + "' is not a variable");
                }
                if (s.kind == Sym.ARRAY) {
                    err(it.line, "array '" + it.name + "' must be indexed"); // C20
                }
                if (s.type != Type.INT) {
                    err(it.line, "input target must be integer but '" + it.name
                            + "' has type " + s.type);                 // C17
                }
                emit("IN");                                            // R27
                emit("STO", frameLevel - s.frameLevel, s.offset);
            }
        }
    }

    private void compileBlock(Ast.Block b) {
        enterScope();                               // C0
        int intAddr = emit("INT", -1);
        int base = nextOffset;
        compileDecls(b.decls);
        int n = nextOffset - base;
        patch(intAddr, n);
        compileStmts(b.stmts);
        emit("FREE", n);                            // R5
        leaveScope();                               // C2
    }

    // ----------------------------------------------------------- expressions

    private Type compileExpr(Ast.Expr e) {
        Type t;
        if (e instanceof Ast.IntLit) {
            emit("LIT", ((Ast.IntLit) e).value);    // R36
            t = Type.INT;
        } else if (e instanceof Ast.BoolLit) {
            emit("LIT", ((Ast.BoolLit) e).value ? 1 : 0);  // R34 / R35
            t = Type.BOOL;
        } else if (e instanceof Ast.Var) {
            t = compileVar((Ast.Var) e);
        } else if (e instanceof Ast.ArrayRef) {
            t = compileArrayRef((Ast.ArrayRef) e);
        } else if (e instanceof Ast.Call) {
            t = compileCall((Ast.Call) e);
        } else if (e instanceof Ast.Unary) {
            t = compileUnary((Ast.Unary) e);
        } else if (e instanceof Ast.Binary) {
            t = compileBinary((Ast.Binary) e);
        } else if (e instanceof Ast.BlockExpr) {
            t = compileBlockExpr((Ast.BlockExpr) e);
        } else {
            err(e.line, "unknown expression");
            t = Type.ERROR;
        }
        e.type = t;
        return t;
    }

    /** bare identifier: variable read, or no-argument function call (C37). */
    private Type compileVar(Ast.Var v) {
        Sym s = lookup(v.name);                     // C6
        if (s == null) {
            err(v.line, "undeclared identifier '" + v.name + "'");
        }
        if (s.kind == Sym.FUNC) {
            if (s.paramCount != 0) {                // C29
                err(v.line, "function '" + v.name + "' needs " + s.paramCount + " argument(s)");
            }
            emit("CALF", s.codeAddr, 0, frameLevel - s.frameLevel);  // R46/R47
            return s.type;
        }
        if (s.kind == Sym.VAR || s.kind == Sym.PARAM) {
            if (s.isArray) {                        // C20
                err(v.line, "array '" + v.name + "' must be indexed");
            }
            emit("LOD", frameLevel - s.frameLevel, s.offset);        // R32
            return s.type;
        }
        if (s.kind == Sym.ARRAY) {                  // C20
            err(v.line, "array '" + v.name + "' must be indexed");
        }
        err(v.line, "'" + v.name + "' cannot be used in an expression");
        return Type.ERROR;
    }

    private Type compileArrayRef(Ast.ArrayRef a) {
        Sym s = lookup(a.name);
        if (s == null) {
            err(a.line, "undeclared identifier '" + a.name + "'");
        }
        if (s.kind != Sym.ARRAY) {                  // C21
            err(a.line, "'" + a.name + "' is not an array");
        }
        emit("LDA", frameLevel - s.frameLevel, s.offset);   // R40
        Type it = compileExpr(a.index);
        if (it != Type.INT) {                       // C12
            err(a.index.line, "array index must be integer but has type " + it);
        }
        emit("CHK", 0, s.size - 1);                 // R41
        emit("ADD");
        emit("LDI");                                // R32
        return s.type;
    }

    private Type compileCall(Ast.Call c) {
        Sym s = lookup(c.name);
        if (s == null) {
            err(c.line, "undeclared identifier '" + c.name + "'");
        }
        if (s.kind != Sym.FUNC) {                   // C33
            err(c.line, "'" + c.name + "' is not a function");
        }
        compileArgs(s, c.name, c.args, c.line);
        emit("CALF", s.codeAddr, c.args.size(), frameLevel - s.frameLevel); // R46/R47
        return s.type;
    }

    private Type compileUnary(Ast.Unary u) {
        Type t = compileExpr(u.e);
        if (u.op.equals("+")) {
            if (t != Type.INT) {                    // C12
                err(u.line, "unary '+' requires an integer operand but got " + t);
            }
            return Type.INT;
        }
        if (u.op.equals("-")) {
            if (t != Type.INT) {
                err(u.line, "unary '-' requires an integer operand but got " + t);
            }
            emit("NEG");                            // R13
            return Type.INT;
        }
        if (t != Type.BOOL) {                       // C13
            err(u.line, "unary '~' requires a boolean operand but got " + t);
        }
        emit("NOT");                                // R18
        return Type.BOOL;
    }

    private Type compileBinary(Ast.Binary b) {
        Type l = compileExpr(b.l);
        Type r = compileExpr(b.r);
        String op = b.op;

        if (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/")) {
            if (l != Type.INT || r != Type.INT) {   // C12
                err(b.line, "'" + op + "' requires integer operands but got " + l + " and " + r);
            }
            if (op.equals("+")) emit("ADD");        // R14
            else if (op.equals("-")) emit("SUB");   // R15
            else if (op.equals("*")) emit("MUL");   // R16
            else emit("DIV");                       // R17
            return Type.INT;
        }
        if (op.equals("&") || op.equals("|")) {
            if (l != Type.BOOL || r != Type.BOOL) { // C13
                err(b.line, "'" + op + "' requires boolean operands but got " + l + " and " + r);
            }
            if (op.equals("&")) emit("AND");        // R19
            else emit("OR");                        // R20
            return Type.BOOL;
        }
        if (op.equals("=") || op.equals("#")) {
            if (!((l == Type.INT && r == Type.INT) || (l == Type.BOOL && r == Type.BOOL))) {
                err(b.line, "'" + op + "' requires operands of the same type "
                        + "(int/int or bool/bool) but got " + l + " and " + r); // C14
            }
            if (op.equals("=")) emit("EQ");         // R21
            else emit("NE");                        // R22
            return Type.BOOL;
        }
        // order comparisons
        if (l != Type.INT || r != Type.INT) {        // C15
            err(b.line, "'" + op + "' requires integer operands but got " + l + " and " + r);
        }
        if (op.equals("<")) emit("LT");             // R23
        else if (op.equals("<=")) emit("LE");       // R24
        else if (op.equals(">")) emit("GT");        // R25
        else emit("GE");                            // R26
        return Type.BOOL;
    }

    private Type compileBlockExpr(Ast.BlockExpr be) {
        enterScope();                               // C0
        int intAddr = emit("INT", -1);
        int base = nextOffset;
        compileDecls(be.decls);
        int n = nextOffset - base;
        patch(intAddr, n);                          // R3
        compileStmts(be.stmts);
        Type t = compileExpr(be.result);
        emit("FREE", n);                            // R6
        leaveScope();                               // C2
        return t;
    }

    // -------------------------------------------------------------- helpers

    private void compileArgs(Sym s, String name, List<Ast.Expr> args, int line) {
        if (args.size() != s.paramCount) {          // C32
            err(line, "'" + name + "' expects " + s.paramCount + " argument(s) but got " + args.size());
        }
        for (int i = 0; i < args.size(); i++) {
            Type at = compileExpr(args.get(i));
            if (at != s.paramTypes.get(i)) {        // C31
                err(args.get(i).line, "argument " + (i + 1) + " of '" + name + "' must be "
                        + s.paramTypes.get(i) + " but got " + at);
            }
        }
    }

    private void checkAssign(Sym s, Type et, String name, int line) {
        if (et != s.type) {                         // C16
            err(line, "cannot assign a value of type " + et + " to '" + name
                    + "' of type " + s.type);
        }
    }

    private int constEval(Ast.Expr e) {
        if (e instanceof Ast.IntLit) {
            return ((Ast.IntLit) e).value;
        }
        if (e instanceof Ast.Unary) {
            Ast.Unary u = (Ast.Unary) e;
            if (u.op.equals("+")) {
                return constEval(u.e);
            }
            if (u.op.equals("-")) {
                return -constEval(u.e);
            }
            err(e.line, "array bound must be a constant integer expression");
        }
        if (e instanceof Ast.Binary) {
            Ast.Binary b = (Ast.Binary) e;
            int l = constEval(b.l);
            int r = constEval(b.r);
            if (b.op.equals("+")) return l + r;
            if (b.op.equals("-")) return l - r;
            if (b.op.equals("*")) return l * r;
            if (b.op.equals("/")) {
                if (r == 0) {
                    err(e.line, "division by zero in array bound");
                }
                return l / r;
            }
            err(e.line, "array bound must be a constant integer expression");
        }
        err(e.line, "array bound must be a constant integer expression");
        return 0;
    }
}
