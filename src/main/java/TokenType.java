public enum TokenType {

    // OPERATORS
    PLUS("+"),
    MINUS("-"),
    ASTERISK("*"),
    POWER("**"),
    SLASH("/"),
    DOUBLE_SLASH("//"),
    PERCENT("%"),
    AT("@"),
    LEFT_SHIFT("<<"),
    RIGHT_SHIFT(">>"),
    BITWISE_AND("&"),
    BITWISE_OR("|"),
    BITWISE_XOR("^"),
    BITWISE_NOT("~"),
    COLON_ASSIGN(":="),
    LESS("<"),
    GREATER(">"),
    LESS_EQUAL("<="),
    GREATER_EQUAL(">="),
    EQUAL("=="),
    NOT_EQUAL("!="),

    // DELIMITERS
    LEFT_PARENTHESIS("("),
    RIGHT_PARENTHESIS(")"),
    LEFT_SQUARE_BRACKET("["),
    RIGHT_SQUARE_BRACKET("]"),
    LEFT_CURLY_BRACKET("{"),
    RIGHT_CURLY_BRACKET("}"),
    COMMA(","),
    COLON(":"),
    DOT("."),
    SEMICOLON(";"),
    ASSIGN("="),
    ARROW("->"),
    ASSIGNMENT_OPERATOR(""),
    EXCLAMATION_MARK("!"),

    // LITERALS
    INTEGER_LITERAL(""),
    BINARY_INTEGER_LITERAL(""),
    OCTAL_INTEGER_LITERAL(""),
    HEX_INTEGER_LITERAL(""),
    FLOATING_POINT_LITERAL(""),
    IMAGINARY_LITERAL(""),
    STRING_LITERAL(""),

    // LINE STRUCTURE
    NEWLINE(""),
    INDENT(""),
    DEDENT(""),

    ERROR(""),
    IDENTIFIER(""),
    KEYWORD("");

    private final String value;

    TokenType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
