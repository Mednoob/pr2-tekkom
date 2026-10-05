/*
 * JFlex lexer for the JFlex + CUP front end of the CourseGrammar2 compiler.
 * It implements java_cup.runtime.Scanner and returns java_cup.runtime.Symbol
 * objects whose value carries the token text and position when relevant.
 *
 * Generated with JFlex 1.9.1:  java -jar jflex-full-1.9.1.jar Yylex.flex
 */

import java_cup.runtime.*;

%%

%class Yylex
%public
%unicode
%cup
%line
%column

%{
    public int num_error = 0;

    public String getText() { return yytext(); }
    public int getLine()    { return yyline + 1; }
    public int getCol()     { return yycolumn + 1; }

    /** A token with a text value (identifier / number / string). */
    private Symbol val(int t) {
        return new Symbol(t, yyline + 1, yycolumn + 1,
                          new Ast.Tok(yytext(), yyline + 1, yycolumn + 1));
    }

    /** A token without a text value. */
    private Symbol tok(int t) {
        return new Symbol(t, yyline + 1, yycolumn + 1);
    }

    private String unescape(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n':  b.append('\n'); break;
                    case 't':  b.append('\t'); break;
                    case 'r':  b.append('\r'); break;
                    case '"':  b.append('"');  break;
                    case '\\': b.append('\\'); break;
                    default:   b.append(n);    break;
                }
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
%}

%eofval{
    return new Symbol(CupSym.EOF, yyline + 1, yycolumn + 1);
%eofval}

LineTerminator = \r|\n|\r\n
WhiteSpace     = {LineTerminator} | [ \t\f]
Digit          = [0-9]
Letter         = [a-zA-Z_]
Identifier     = {Letter}({Letter}|{Digit})*
StringLiteral  = \"([^\"\\\n\r]|\\.)*\"

%%

<YYINITIAL> {
    {WhiteSpace}     { /* skip */ }

    "if"             { return tok(CupSym.IF); }
    "then"           { return tok(CupSym.THEN); }
    "else"           { return tok(CupSym.ELSE); }
    "end"            { return tok(CupSym.END); }
    "repeat"         { return tok(CupSym.REPEAT); }
    "until"          { return tok(CupSym.UNTIL); }
    "loop"           { return tok(CupSym.LOOP); }
    "exit"           { return tok(CupSym.EXIT); }
    "put"            { return tok(CupSym.PUT); }
    "get"            { return tok(CupSym.GET); }
    "var"            { return tok(CupSym.VAR); }
    "func"           { return tok(CupSym.FUNC); }
    "proc"           { return tok(CupSym.PROC); }
    "integer"        { return tok(CupSym.INT); }
    "boolean"        { return tok(CupSym.BOOL); }
    "true"           { return tok(CupSym.TRUE); }
    "false"          { return tok(CupSym.FALSE); }
    "skip"           { return tok(CupSym.SKIP); }

    {Identifier}     { return val(CupSym.IDENT); }
    {Digit}+         { return val(CupSym.NUMCONST); }

    {StringLiteral}  {
                        String s = yytext();
                        s = s.substring(1, s.length() - 1);
                        s = unescape(s);
                        return new Symbol(CupSym.STRCONST, yyline + 1, yycolumn + 1,
                                          new Ast.Tok(s, yyline + 1, yycolumn + 1));
                     }

    ":="             { return tok(CupSym.ASSGN); }
    "<="             { return tok(CupSym.LTE); }
    ">="             { return tok(CupSym.GTE); }
    "="              { return tok(CupSym.EQ); }
    "#"              { return tok(CupSym.NE); }
    "<"              { return tok(CupSym.LT); }
    ">"              { return tok(CupSym.GT); }
    "+"              { return tok(CupSym.ADD); }
    "-"              { return tok(CupSym.SUB); }
    "*"              { return tok(CupSym.MUL); }
    "/"              { return tok(CupSym.DIV); }
    "&"              { return tok(CupSym.AND); }
    "|"              { return tok(CupSym.OR); }
    "~"              { return tok(CupSym.NOT); }
    "("              { return tok(CupSym.LPAREN); }
    ")"              { return tok(CupSym.RPAREN); }
    "["              { return tok(CupSym.LBRACKET); }
    "]"              { return tok(CupSym.RBRACKET); }
    "{"              { return tok(CupSym.OPEN); }
    "}"              { return tok(CupSym.CLOSE); }
    ";"              { return tok(CupSym.ENDSTMT); }
    ":"              { return tok(CupSym.AS); }
    ","              { return tok(CupSym.LISTSEP); }

    "%" [^\r\n]*     { /* line comment */ }

    [^]              {
                        num_error++;
                        throw new RuntimeException("(" + (yyline + 1) + ":" + (yycolumn + 1)
                                + ") illegal character: '" + yytext() + "'");
                     }
}
