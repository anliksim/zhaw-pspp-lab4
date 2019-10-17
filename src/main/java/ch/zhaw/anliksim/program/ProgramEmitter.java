package ch.zhaw.anliksim.program;

import ch.zhaw.anliksim.Scanner;
import ch.zhaw.anliksim.Token;
import de.inetsoftware.jwebassembly.JWebAssembly;
import de.inetsoftware.jwebassembly.module.*;

class ProgramEmitter implements Emitter {

    static final String VALUE = "value";

    @Override
    public void emit() {
        try {
            program();
            JWebAssembly.il.add(new WasmLoadStoreInstruction(true, JWebAssembly.local(ValueType.f64, VALUE), 0));
            JWebAssembly.il.add(new WasmBlockInstruction(WasmBlockOperator.RETURN, null, 0));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void expr() throws Exception {
        term();
        while (Scanner.la == Token.PLUS || Scanner.la == Token.MINUS) {
            Scanner.scan();
            int op = Scanner.token.kind;
            term();

            if (op == Token.PLUS) {
                JWebAssembly.il.add(new WasmNumericInstruction(NumericOperator.add, ValueType.f64, 0));
            } else if (op == Token.MINUS) {
                JWebAssembly.il.add(new WasmNumericInstruction(NumericOperator.sub, ValueType.f64, 0));
            }
        }
    }

    static void term() throws Exception {
        factor();
        while (Scanner.la == Token.TIMES || Scanner.la == Token.SLASH) {
            Scanner.scan();
            int op = Scanner.token.kind;
            factor();

            if (op == Token.TIMES) {
                JWebAssembly.il.add(new WasmNumericInstruction(NumericOperator.mul, ValueType.f64, 0));
            } else if (op == Token.SLASH) {
                JWebAssembly.il.add(new WasmNumericInstruction(NumericOperator.div, ValueType.f64, 0));
            }
        }
    }

    static void factor() throws Exception {
        if (Scanner.la == Token.LBRACK) {
            Scanner.scan();
            expr();
            Scanner.check(Token.RBRACK);
        } else if (Scanner.la == Token.NUMBER) {
            Scanner.scan();
            JWebAssembly.il.add(new WasmConstInstruction(Scanner.token.val, 0));
        } else if (Scanner.la == Token.IDENT) {
            Scanner.scan();
            JWebAssembly.il.add(new WasmLoadStoreInstruction(true, JWebAssembly.local(ValueType.f64, Scanner.token.str), 0));
        }
    }

    static void assignment() throws Exception {
        Scanner.check(Token.IDENT);
        int slot = JWebAssembly.local(ValueType.f64, Scanner.token.str);
        Scanner.check(Token.EQUAL);
        expr();
        Scanner.check(Token.SCOLON);
        JWebAssembly.il.add(new WasmLoadStoreInstruction(false, slot, 0));
    }

    static void statement() throws Exception {
        switch (Scanner.la) {
            case Token.RETURN:
                returnStatement();
                break;

            case Token.IF:
                ifClause();
                break;

            case Token.WHILE:
                whileClause();
                break;

            case Token.LCBRACK:
                block();
                break;

            default:
                assignment();
        }
    }

    static void statementSequence() throws Exception {
        do {
            statement();
        } while (Scanner.la != Token.EOF && Scanner.la != Token.RCBRACK);
    }

    static void program() throws Exception {
        statementSequence();
    }

    static void block() throws Exception {
        Scanner.check(Token.LCBRACK);
        statementSequence();
        Scanner.check(Token.RCBRACK);
    }

    static void returnStatement() throws Exception {
        Scanner.check(Token.RETURN);
        expr();
        Scanner.check(Token.SCOLON);
        JWebAssembly.il.add(new WasmBlockInstruction(WasmBlockOperator.RETURN, null, 0));
    }

    static void condition() throws Exception {
        Scanner.check(Token.LBRACK);

        boolean negate = false;
        if (Scanner.la == Token.NOT) {
            Scanner.scan();
            negate = true;
        }

        expr();

        JWebAssembly.il.add(new WasmNumericInstruction(NumericOperator.nearest, ValueType.f64, 0));
        JWebAssembly.il.add(new WasmConvertInstruction(ValueTypeConvertion.d2i, 0));

        if (negate) {
            JWebAssembly.il.add(new WasmNumericInstruction(NumericOperator.eqz, ValueType.i32, 0));
        }

        Scanner.check(Token.RBRACK);
    }

    static void ifClause() throws Exception {
        Scanner.check(Token.IF);
        condition();
        JWebAssembly.il.add(new WasmBlockInstruction(WasmBlockOperator.IF, null, 0));
        statement();

        if (Scanner.la == Token.ELSE) {
            Scanner.scan();
            JWebAssembly.il.add(new WasmBlockInstruction(WasmBlockOperator.ELSE, null, 0));
            statement();
        }

        JWebAssembly.il.add(new WasmBlockInstruction(WasmBlockOperator.END, null, 0));
    }

    static void whileClause() throws Exception {
        Scanner.check(Token.WHILE);
        JWebAssembly.il.add(new WasmBlockInstruction(WasmBlockOperator.BLOCK, null, 0));
        JWebAssembly.il.add(new WasmBlockInstruction(WasmBlockOperator.LOOP, null, 0));
        condition();
        JWebAssembly.il.add(new WasmNumericInstruction(NumericOperator.eqz, ValueType.i32, 0));
        JWebAssembly.il.add(new WasmBlockInstruction(WasmBlockOperator.BR_IF, 1, 0));
        statement();
        JWebAssembly.il.add(new WasmBlockInstruction(WasmBlockOperator.BR, 0, 0));
        JWebAssembly.il.add(new WasmBlockInstruction(WasmBlockOperator.END, null, 0));
        JWebAssembly.il.add(new WasmBlockInstruction(WasmBlockOperator.END, null, 0));
    }

    public static void main(String[] args) throws Exception {
        // Aufgabe 1
        String program =
                "m = $arg0 - 42;\n" +
                "if (!m) {\n" +
                "   return 7;\n" +
                "} else {\n" +
                "   return 666;\n" +
                "}\n";
        // Aufgabe 2
//        String program =
//                "m = $arg0;\n" +
//                        "s = 1;\n" +
//                        "while (m) {\n" +
//                        "   s = s * m;\n" +
//                        "   m = m - 1; " +
//                        "}\n" +
//                        "return s;";
        Scanner.init(program);
        Scanner.scan();
        JWebAssembly.emitCode(Program.class, new ProgramEmitter());
    }
}
