import java.util.ArrayList;
import java.util.List;

/**
 * Abstract syntax tree for the language of CourseGrammar2.
 *
 * The JFlex+CUP parser builds this tree; {@link TreeCompiler} walks it,
 * performs the C-rules (context/type checking) and the R-rules (code
 * generation) and produces the same machine code as the hand-written
 * recursive-descent compiler.
 */
public class Ast {

    /** A lexer token value (identifier, number or text) with position. */
    public static class Tok {
        public final String text;
        public final int line, col;
        public Tok(String text, int line, int col) {
            this.text = text;
            this.line = line;
            this.col = col;
        }
    }

    // ===================================================================
    //  Nodes
    // ===================================================================

    /** Whole program: a top level scope. */
    public static class Program {
        public List<Decl> decls = new ArrayList<Decl>();
        public List<Stmt> stmts = new ArrayList<Stmt>();
        public int line;
    }

    public static abstract class Decl {
        public String name;
        public int line;
    }

    public static class VarDecl extends Decl {
        public Expr bound;           // null -> scalar
        public Type type;
    }

    public static class Param {
        public String name;
        public Type type;
        public int line;
    }

    public static class FuncDecl extends Decl {
        public Type retType;
        public List<Param> params = new ArrayList<Param>();
        public Expr body;
    }

    public static class ProcDecl extends Decl {
        public List<Param> params = new ArrayList<Param>();
        public Program body;         // the procedure scope
    }

    public static abstract class Stmt {
        public int line;
    }

    public static class Assign extends Stmt {
        public String name;
        public Expr rhs;
    }

    public static class ArrayAssign extends Stmt {
        public String name;
        public Expr index, rhs;
    }

    public static class CallStmt extends Stmt {
        public String name;
        public List<Expr> args = new ArrayList<Expr>();
    }

    public static class If extends Stmt {
        public Expr cond;
        public List<Stmt> thenStmts = new ArrayList<Stmt>();
        public List<Stmt> elseStmts = new ArrayList<Stmt>();
    }

    public static class Repeat extends Stmt {
        public List<Stmt> body = new ArrayList<Stmt>();
        public Expr cond;
    }

    public static class Loop extends Stmt {
        public List<Stmt> body = new ArrayList<Stmt>();
    }

    public static class Exit extends Stmt {}

    /** put item: an Expr, a String (text) or null (skip). */
    public static class Put extends Stmt {
        public List<Object> items = new ArrayList<Object>();
    }

    public static class GetItem {
        public String name;
        public Expr index;           // null -> scalar
        public int line;
    }

    public static class Get extends Stmt {
        public List<GetItem> items = new ArrayList<GetItem>();
    }

    public static class Block extends Stmt {
        public List<Decl> decls = new ArrayList<Decl>();
        public List<Stmt> stmts = new ArrayList<Stmt>();
    }

    public static abstract class Expr {
        public int line;
        public Type type;            // filled in by the TreeCompiler
    }

    public static class IntLit extends Expr   { public int value; }
    public static class BoolLit extends Expr  { public boolean value; }
    public static class Var extends Expr      { public String name; }
    public static class ArrayRef extends Expr { public String name; public Expr index; }
    public static class Call extends Expr     { public String name; public List<Expr> args = new ArrayList<Expr>(); }
    public static class Unary extends Expr    { public String op; public Expr e; }
    public static class Binary extends Expr   { public String op; public Expr l, r; }
    public static class BlockExpr extends Expr {
        public List<Decl> decls = new ArrayList<Decl>();
        public List<Stmt> stmts = new ArrayList<Stmt>();
        public Expr result;
    }

    // ===================================================================
    //  Factory helpers (keep the CUP actions short)
    // ===================================================================

    public static Expr intLit(String s, int line) {
        IntLit e = new IntLit();
        e.value = Integer.parseInt(s);
        e.line = line;
        return e;
    }

    public static Expr boolLit(boolean v, int line) {
        BoolLit e = new BoolLit();
        e.value = v;
        e.line = line;
        return e;
    }

    public static Expr var(String name, int line) {
        Var e = new Var();
        e.name = name;
        e.line = line;
        return e;
    }

    public static Expr arrayRef(String name, Expr index, int line) {
        ArrayRef e = new ArrayRef();
        e.name = name;
        e.index = index;
        e.line = line;
        return e;
    }

    public static Expr call(String name, List<Expr> args, int line) {
        Call e = new Call();
        e.name = name;
        e.args = args;
        e.line = line;
        return e;
    }

    public static Expr unary(String op, Expr e, int line) {
        Unary u = new Unary();
        u.op = op;
        u.e = e;
        u.line = line;
        return u;
    }

    public static Expr binary(String op, Expr l, Expr r, int line) {
        Binary b = new Binary();
        b.op = op;
        b.l = l;
        b.r = r;
        b.line = line;
        return b;
    }

    public static Expr blockExpr(List<Decl> d, List<Stmt> s, Expr r, int line) {
        BlockExpr b = new BlockExpr();
        b.decls = d;
        b.stmts = s;
        b.result = r;
        b.line = line;
        return b;
    }

    public static Decl varDecl(String name, Expr bound, Type t, int line) {
        VarDecl d = new VarDecl();
        d.name = name;
        d.bound = bound;
        d.type = t;
        d.line = line;
        return d;
    }

    public static Decl funcDecl(String name, Type ret, List<Param> ps, Expr body, int line) {
        FuncDecl d = new FuncDecl();
        d.name = name;
        d.retType = ret;
        d.params = ps;
        d.body = body;
        d.line = line;
        return d;
    }

    public static Decl procDecl(String name, List<Param> ps, Program body, int line) {
        ProcDecl d = new ProcDecl();
        d.name = name;
        d.params = ps;
        d.body = body;
        d.line = line;
        return d;
    }

    public static Param param(String name, Type t, int line) {
        Param p = new Param();
        p.name = name;
        p.type = t;
        p.line = line;
        return p;
    }

    public static Stmt assign(String name, Expr rhs, int line) {
        Assign a = new Assign();
        a.name = name;
        a.rhs = rhs;
        a.line = line;
        return a;
    }

    public static Stmt arrayAssign(String name, Expr index, Expr rhs, int line) {
        ArrayAssign a = new ArrayAssign();
        a.name = name;
        a.index = index;
        a.rhs = rhs;
        a.line = line;
        return a;
    }

    public static Stmt callStmt(String name, List<Expr> args, int line) {
        CallStmt c = new CallStmt();
        c.name = name;
        c.args = args;
        c.line = line;
        return c;
    }

    public static Stmt ifStmt(Expr cond, List<Stmt> thenS, List<Stmt> elseS, int line) {
        If i = new If();
        i.cond = cond;
        i.thenStmts = thenS;
        i.elseStmts = elseS;
        i.line = line;
        return i;
    }

    public static Stmt repeat(List<Stmt> body, Expr cond, int line) {
        Repeat r = new Repeat();
        r.body = body;
        r.cond = cond;
        r.line = line;
        return r;
    }

    public static Stmt loop(List<Stmt> body, int line) {
        Loop l = new Loop();
        l.body = body;
        l.line = line;
        return l;
    }

    public static Stmt exit(int line) {
        Exit e = new Exit();
        e.line = line;
        return e;
    }

    public static Stmt put(List<Object> items, int line) {
        Put p = new Put();
        p.items = items;
        p.line = line;
        return p;
    }

    public static Stmt get(List<GetItem> items, int line) {
        Get g = new Get();
        g.items = items;
        g.line = line;
        return g;
    }

    public static Stmt block(List<Decl> d, List<Stmt> s, int line) {
        Block b = new Block();
        b.decls = d;
        b.stmts = s;
        b.line = line;
        return b;
    }

    public static Program program(List<Decl> d, List<Stmt> s, int line) {
        Program p = new Program();
        p.decls = d;
        p.stmts = s;
        p.line = line;
        return p;
    }

    public static GetItem getItem(String name, Expr index, int line) {
        GetItem g = new GetItem();
        g.name = name;
        g.index = index;
        g.line = line;
        return g;
    }
}
