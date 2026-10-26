package dev.isira.jaspring.template.expr;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Every operator Angular's parser produces as a `Binary` node, assignments included.
public enum BinaryOp {
    ASSIGN("="), ADD_ASSIGN("+="), SUB_ASSIGN("-="), MUL_ASSIGN("*="), DIV_ASSIGN("/="), MOD_ASSIGN("%="),
    EXP_ASSIGN("**="), AND_ASSIGN("&&="), OR_ASSIGN("||="), NULLISH_ASSIGN("??="),
    AND("&&"), OR("||"), NULLISH("??"),
    EQ("=="), NEQ("!="), STRICT_EQ("==="), STRICT_NEQ("!=="),
    LT("<"), GT(">"), LTE("<="), GTE(">="), IN("in"), INSTANCEOF("instanceof"),
    ADD("+"), SUB("-"), MUL("*"), MOD("%"), DIV("/"), EXP("**");

    private static final Map<String, BinaryOp> BY_SYMBOL =
            Stream.of(values()).collect(Collectors.toUnmodifiableMap(op -> op.symbol, op -> op));

    private final String symbol;

    BinaryOp(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public boolean isAssignment() {
        return symbol.endsWith("=") && this != EQ && this != NEQ && this != STRICT_EQ && this != STRICT_NEQ
                && this != LTE && this != GTE;
    }

    public static BinaryOp fromSymbol(String symbol) {
        final var op = BY_SYMBOL.get(symbol);
        if (op == null) throw new IllegalArgumentException("Unknown binary operator " + symbol);
        return op;
    }
}
