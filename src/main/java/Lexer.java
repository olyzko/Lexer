import java.io.IOException;
import java.io.InputStream;
import java.util.*;

enum StringType {
    NONE,
    SINGLE_QUOTED,
    DOUBLE_QUOTED,
    TRIPLE_QUOTED
}

public class Lexer {
    private static final int MAX_INDENT_LENGTH = 8;
    private final InputStream in;
    private final ArrayList<String> keywords;
    private AutomataState state = AutomataState.INITIAL;
    private final StringBuilder buffer = new StringBuilder();
    private final List<Token> tokens = new ArrayList<>();

    private char currentChar;
    private int currentLine = 0;
    private int currentIndent = 0;
    private int tokenStartRow = 0;
    private boolean blankLine = true;
    private final LinkedList<Integer> indentsList = new LinkedList<>();
    private StringType currentStringType = StringType.NONE;

    public Lexer(InputStream in, ArrayList<String> keywords) {
        this.in = in;
        this.keywords = keywords;
        indentsList.add(0);
    }

    public List<Token> analyze() throws IOException {
        int result;
        while (true) {
            result = in.read();
            if (result < 0) {
                if (currentChar == '\n')
                    return tokens;
                else {
                    if (currentStringType == StringType.TRIPLE_QUOTED) {
                        endToken(TokenType.ERROR, "Missing closing triple quote.");
                        state = AutomataState.INITIAL;
                    }
                    currentChar = '\n';
                }
            } else currentChar = (char) result;

            switch (state) {
                case INITIAL -> setStateByCurrentChar();
                case KEYWORD_OR_IDENTIFIER -> readKeywordOrIdentifier();
                case COMMENT -> readComment();
                case INVALID_SYMBOL -> readInvalidSymbol();

                case PLUS, DOUBLE_SLASH, POWER, PERCENT, AT, LEFT_SHIFT, RIGHT_SHIFT, BITWISE_OR, BITWISE_AND, BITWISE_XOR, ASSIGN, COLON -> readAssignment();
                case MINUS -> readOperatorWithAssignment(TokenType.MINUS, '>',
                        AutomataState.ASSIGNMENT_OPERATOR, AutomataState.ARROW);
                case ASTERISK -> readOperatorWithAssignment(TokenType.ASTERISK, '*',
                        AutomataState.ASSIGNMENT_OPERATOR, AutomataState.POWER);
                case SLASH -> readOperatorWithAssignment(TokenType.SLASH, '/',
                        AutomataState.ASSIGNMENT_OPERATOR, AutomataState.DOUBLE_SLASH);

                case LESS -> readOperatorWithAssignment(TokenType.LESS, '<',
                        AutomataState.LESS_EQUAL, AutomataState.LEFT_SHIFT);
                case GREATER -> readOperatorWithAssignment(TokenType.GREATER, '>',
                        AutomataState.GREATER_EQUAL, AutomataState.RIGHT_SHIFT);

                case ASSIGNMENT_OPERATOR -> endToken(TokenType.ASSIGNMENT_OPERATOR, buffer.toString());
                case DOT -> readDot();
                case EXCLAMATION_MARK -> readExclamationMark();

                case DECIMAL_INTEGER -> readDecimalInteger();
                case STARTING_WITH_ZERO -> readZeroIntegerOrRadix();
                case BINARY_INTEGER -> readNonDecimalInteger(2);
                case OCTAL_INTEGER -> readNonDecimalInteger(8);
                case HEX_INTEGER -> readNonDecimalInteger(16);
                case BINARY_INTEGER_START -> readNonDecimalIntegerStart(2);
                case OCTAL_INTEGER_START -> readNonDecimalIntegerStart(8);
                case HEX_INTEGER_START -> readNonDecimalIntegerStart(16);

                case FLOAT -> readFloat();
                case IMAGINARY -> endToken(TokenType.IMAGINARY_LITERAL, buffer.toString());
                case ZERO_INTEGER -> readZeroInteger();
                case INTEGER_WITH_ZERO_PREFIX -> readIntegerWithZeroPrefix();
                case EXPONENT_FLOAT_ON_INTEGER -> readExponentFloatStart(TokenType.INTEGER_LITERAL);
                case EXPONENT_FLOAT_ON_FLOAT -> readExponentFloatStart(TokenType.FLOATING_POINT_LITERAL);
                case EXPONENT_FLOAT -> readExponentFloat();

                case IDENTIFIER_OR_STRING_LITERAL -> readIdentifierOrStringLiteral();
                case SINGLE_OR_TRIPLE_QUOTED_STRING -> readSingleOrTripleQuotedString();
                case CLOSED_SINGLE_OR_OPENED_TRIPLE_QUOTED_STRING -> readClosedSingleOrOpenedTripleQuotedString();
                case SINGLE_QUOTED_STRING -> readSingleQuotedString();
                case DOUBLE_QUOTED_STRING -> readDoubleQuotedString();
                case TRIPLE_QUOTED_STRING -> readTripleQuotedString();
                case ESCAPE -> readEscaped();
                case TRIPLE_QUOTED_STRING_WITH_QUOTE -> readTripleQuotedStringWithQuote();
                case TRIPLE_QUOTED_STRING_WITH_DOUBLE_QUOTE -> readTripleQuotedStringWithDoubleQuote();

                case FIRST_INDENT -> readFirstIndent();
                case INDENT -> readIndent();
                case BACKSLASH -> readBackslash();

                default -> endTokenUsingTokenValue(state);
            }
        }
    }

    private void startToken(AutomataState state) {
        this.state = state;
        buffer.append(currentChar);
        tokenStartRow = currentLine;
        blankLine = false;
    }

    private void endToken(TokenType type, String value) {
        tokens.add(new Token(type, value, tokenStartRow));
        buffer.setLength(0);
        setStateByCurrentChar();
    }

    private void endTokenUsingTokenValue(AutomataState state) {
        String stateName = state.name();
        TokenType type = TokenType.valueOf(stateName);
        endToken(type, type.getValue());
    }

    private void readWholeErrorToken() throws IOException {
        while (currentChar != ' ' && currentChar != '\n' && currentChar != '#') {
            buffer.append(currentChar);
            int next = in.read();
            currentChar = (char) next;
        }
    }

    private void moveNCharsLeft() throws IOException {
        in.reset();
        currentChar = buffer.charAt(buffer.length() - 1);
        buffer.delete(buffer.length() - 1, buffer.length());
    }

    private void readInvalidSymbol() throws IOException {
        int next = in.read();
        currentChar = (char) next;
        setStateByCurrentChar();
    }

    private void setStateByCurrentChar() {
        if (Utils.isValidIdentifierStart(currentChar)) {
            if (currentChar == 'U' || currentChar == 'u')
                startToken(AutomataState.IDENTIFIER_OR_STRING_LITERAL);
            else
                startToken(AutomataState.KEYWORD_OR_IDENTIFIER);
        }
        else if (currentChar == '0')
            startToken(AutomataState.STARTING_WITH_ZERO);
        else if (Character.isDigit(currentChar))
            startToken(AutomataState.DECIMAL_INTEGER);
         else if (currentChar == '\'') {
            startToken(AutomataState.SINGLE_OR_TRIPLE_QUOTED_STRING);
            buffer.setLength(0);
        } else if (currentChar == '\"') {
            startToken(AutomataState.DOUBLE_QUOTED_STRING);
            buffer.setLength(0);
            currentStringType = StringType.DOUBLE_QUOTED;
        } else if (currentChar == '\n') {
            readLineFeed();
        } else if (currentChar == '\\') {
            in.mark(Integer.MAX_VALUE);
            startToken(AutomataState.BACKSLASH);
        } else if (Character.isWhitespace(currentChar)) {
            if (blankLine && (currentChar == ' ' || currentChar == '\t'))
                state = AutomataState.FIRST_INDENT;
            else
                state = AutomataState.INITIAL;
        } else if (currentChar == '#')
            state = AutomataState.COMMENT;
        else {
            String currCharStr = String.valueOf(currentChar);
            TokenType type = null;
            for (TokenType t : TokenType.values())
                if (currCharStr.equals(t.getValue()))
                    type = t;
            if (type == null) {
                tokens.add(new Token(TokenType.ERROR, "Invalid symbol.", currentLine));
                state = AutomataState.INVALID_SYMBOL;
            } else {
                String typeName = type.name();
                AutomataState state = AutomataState.valueOf(typeName);
                startToken(state);
            }
        }
    }

    private void readKeywordOrIdentifier() {
        if (Utils.isValidIdentifierPart(currentChar))
            buffer.append(currentChar);
        else {
            String value = buffer.toString();
            if (keywords.contains(value))
                tokens.add(new Token(TokenType.KEYWORD, value, tokenStartRow));
            else
            tokens.add(new Token(TokenType.IDENTIFIER, value, tokenStartRow));
            buffer.setLength(0);
            setStateByCurrentChar();
        }
    }

    private void readComment() {
        if (currentChar == '\n')
            setStateByCurrentChar();
    }

    private void readAssignment() {
        String stateName = state.name();
        TokenType type = TokenType.valueOf(stateName);
        AutomataState assignmentState = AutomataState.ASSIGNMENT_OPERATOR;
        if (currentChar == '=') {
            buffer.append(currentChar);
            state = assignmentState;
        } else {
            endToken(type, type.getValue());
        }
    }

    private void readOperatorWithAssignment(TokenType type, char operator, AutomataState state1, AutomataState state2) {
        if (currentChar == '=') {
            buffer.append(currentChar);
            state = state1;
        } else if (currentChar == operator) {
            buffer.append(currentChar);
            state = state2;
        } else
            endToken(type, type.getValue());
    }

    private void readExclamationMark() {
        if (currentChar == '=') {
            buffer.append(currentChar);
            state = AutomataState.NOT_EQUAL;
        } else if (currentChar == '(')
            endToken(TokenType.EXCLAMATION_MARK, "!");
        else
            endToken(TokenType.ERROR, "Error. '!=' operator expected.");
    }

    private void readDot() {
        if (Character.isDigit(currentChar)) {
            buffer.append(currentChar);
            state = AutomataState.FLOAT;
        } else
            endToken(TokenType.DOT, TokenType.DOT.getValue());
    }

    private void readZeroIntegerOrRadix() throws IOException {
        if (currentChar == '.') {
            buffer.append(currentChar);
            state = AutomataState.FLOAT;
        }
        else if (currentChar == 'B' || currentChar == 'b') {
            in.mark(2);
            buffer.append(currentChar);
            state = AutomataState.BINARY_INTEGER_START;
        } else if (currentChar == 'O' || currentChar == 'o') {
            in.mark(2);
            buffer.append(currentChar);
            state = AutomataState.OCTAL_INTEGER_START;
        } else if (currentChar == 'X' || currentChar == 'x') {
            in.mark(2);
            buffer.append(currentChar);
            state = AutomataState.HEX_INTEGER_START;
        } else if (currentChar == 'E' || currentChar == 'e') {
            in.mark(3);
            buffer.append(currentChar);
            state = AutomataState.EXPONENT_FLOAT_ON_INTEGER;
        } else if (currentChar == 'J' || currentChar == 'j') {
            buffer.append(currentChar);
            state = AutomataState.IMAGINARY;
        } else if (currentChar == '0') {
            buffer.append(currentChar);
            state = AutomataState.ZERO_INTEGER;
        } else if (Character.isDigit(currentChar)) {
            buffer.append(currentChar);
            state = AutomataState.INTEGER_WITH_ZERO_PREFIX;
        } else if (Character.isLetter(currentChar)) {
            readWholeErrorToken();
            endToken(TokenType.ERROR, "The identifier cannot start with a digit");
        } else
            endToken(TokenType.INTEGER_LITERAL, buffer.toString());
    }

    private void readDecimalInteger() throws IOException {
        if (Character.isDigit(currentChar) || currentChar == '_') {
            buffer.append(currentChar);
        } else if (currentChar == '.') {
            buffer.append(currentChar);
            state = AutomataState.FLOAT;
        } else if (currentChar == 'E' || currentChar == 'e') {
            in.mark(3);
            buffer.append(currentChar);
            state = AutomataState.EXPONENT_FLOAT_ON_INTEGER;
        } else if (currentChar == 'J' || currentChar == 'j') {
            buffer.append(currentChar);
            state = AutomataState.IMAGINARY;
        } else if (Character.isLetter(currentChar)) {
            readWholeErrorToken();
            endToken(TokenType.ERROR, "The identifier cannot start with a digit");
        } else
            endToken(TokenType.INTEGER_LITERAL, buffer.toString());
    }

    private void readFloat() throws IOException {
        if (Character.isDigit(currentChar))
            buffer.append(currentChar);
        else if (currentChar == 'E' || currentChar == 'e') {
            in.mark(3);
            buffer.append(currentChar);
            state = AutomataState.EXPONENT_FLOAT_ON_FLOAT;
        } else if (currentChar == 'J' || currentChar == 'j') {
            buffer.append(currentChar);
            state = AutomataState.IMAGINARY;
        } else if (Character.isLetter(currentChar)) {
            readWholeErrorToken();
            endToken(TokenType.ERROR, "The identifier cannot start with a digit");
        } else
            endToken(TokenType.FLOATING_POINT_LITERAL, buffer.toString());
    }

    private void readNonDecimalInteger(int radix) throws IOException {
        if (Utils.isCorrectDigit(currentChar, radix))
            buffer.append(currentChar);
        else if (currentChar == ' ' || currentChar == '\n' || currentChar == '#') {
            switch (radix) {
                case 2 -> endToken(TokenType.BINARY_INTEGER_LITERAL, buffer.toString());
                case 8 -> endToken(TokenType.OCTAL_INTEGER_LITERAL, buffer.toString());
                case 16 -> endToken(TokenType.HEX_INTEGER_LITERAL, buffer.toString());
            }
        }
        else {
            readWholeErrorToken();
            switch (radix) {
                case 2 -> endToken(TokenType.ERROR, "The binary number must consist only 0-1 digits.");
                case 8 -> endToken(TokenType.ERROR, "The octal number must consist only 0-7 digits.");
                case 16 -> endToken(TokenType.ERROR, "The hex number must consist only digits and a-f letters.");
            }
        }
    }

    private void readNonDecimalIntegerStart(int radix) throws IOException {
        if (Utils.isCorrectDigit(currentChar, radix)) {
            buffer.append(currentChar);
            switch (radix) {
                case 2 -> state = AutomataState.BINARY_INTEGER;
                case 8 -> state = AutomataState.OCTAL_INTEGER;
                case 16 -> state = AutomataState.HEX_INTEGER;
            }
        } else {
            readWholeErrorToken();
            switch (radix) {
                case 2 -> endToken(TokenType.ERROR, "The binary number must consist only 0-1 digits.");
                case 8 -> endToken(TokenType.ERROR, "The octal number must consist only 0-7 digits.");
                case 16 -> endToken(TokenType.ERROR, "The hex number must consist only digits and a-f letters.");
            }
        }
    }

    private void readZeroInteger() throws IOException {
        if (currentChar == '0') {
            buffer.append(currentChar);
        } else if (Character.isDigit(currentChar)) {
            buffer.append(currentChar);
            state = AutomataState.INTEGER_WITH_ZERO_PREFIX;
        } else if (currentChar == '.') {
            buffer.append(currentChar);
            state = AutomataState.FLOAT;
        } else if (currentChar == 'E' || currentChar == 'e') {
            in.mark(3);
            buffer.append(currentChar);
            state = AutomataState.EXPONENT_FLOAT_ON_INTEGER;
        } else if (currentChar == 'J' || currentChar == 'j') {
            buffer.append(currentChar);
            state = AutomataState.IMAGINARY;
        } else if (Character.isLetter(currentChar)) {
            readWholeErrorToken();
            endToken(TokenType.ERROR, "The identifier cannot start with a digit");
        } else
            endToken(TokenType.INTEGER_LITERAL, buffer.toString());
    }

    private void readIntegerWithZeroPrefix() throws IOException {
        if (Character.isDigit(currentChar)) {
            buffer.append(currentChar);
        } else if (currentChar == '.') {
            buffer.append(currentChar);
            state = AutomataState.FLOAT;
        } else if (currentChar == 'E' || currentChar == 'e') {
            in.mark(3);
            buffer.append(currentChar);
            state = AutomataState.EXPONENT_FLOAT;
        } else if (currentChar == 'J' || currentChar == 'j') {
            buffer.append(currentChar);
            state = AutomataState.IMAGINARY;
        } else {
            readWholeErrorToken();
            endToken(TokenType.ERROR, "Integer literal cannot start with 0");
        }
    }

    private void readExponentFloat() throws IOException {
        if (Character.isDigit(currentChar))
            buffer.append(currentChar);
        else if (Character.isLetter(currentChar)) {
            readWholeErrorToken();
            endToken(TokenType.ERROR, "The identifier cannot start with a digit");
        } else
            endToken(TokenType.FLOATING_POINT_LITERAL, buffer.toString());
    }

    private void readExponentFloatStart(TokenType t) throws IOException {
        if (Character.isDigit(currentChar) || currentChar == '+' || currentChar == '-') {
            buffer.append(currentChar);
            state = AutomataState.EXPONENT_FLOAT;
        } else if (Character.isLetter(currentChar)) {
            readWholeErrorToken();
            endToken(TokenType.ERROR, "The identifier cannot start with a digit");
        } else {
            moveNCharsLeft();
            endToken(t, buffer.toString());
        }
    }

    private void readIdentifierOrStringLiteral() {
        if (currentChar == '\'') {
            buffer.setLength(0);
            state = AutomataState.SINGLE_OR_TRIPLE_QUOTED_STRING;
        } else if (currentChar == '\"') {
            buffer.setLength(0);
            state = AutomataState.DOUBLE_QUOTED_STRING;
            currentStringType = StringType.DOUBLE_QUOTED;
        } else {
            state = AutomataState.KEYWORD_OR_IDENTIFIER;
            readKeywordOrIdentifier();
        }
    }

    private void readSingleOrTripleQuotedString() {
        if (currentChar == '\'') {
            state = AutomataState.CLOSED_SINGLE_OR_OPENED_TRIPLE_QUOTED_STRING;
        } else {
            state = AutomataState.SINGLE_QUOTED_STRING;
            buffer.setLength(0);
            currentStringType = StringType.SINGLE_QUOTED;
            readSingleQuotedString();
        }
    }

    private void readSingleQuotedString() {
        if (currentChar == '\'')
            quitString();
        else if (currentChar == '\\')
            state = AutomataState.ESCAPE;
        else if (currentChar == '\n') {
            endToken(TokenType.ERROR, "Missing closing single quote.");
            currentStringType = StringType.NONE;
        } else
            buffer.append(currentChar);
    }

    private void readDoubleQuotedString() {
        if (currentChar == '\"')
            quitString();
        else if (currentChar == '\\')
            state = AutomataState.ESCAPE;
        else if (currentChar == '\n') {
            endToken(TokenType.ERROR, "Missing closing double quote.");
            currentStringType = StringType.NONE;
        } else
            buffer.append(currentChar);
    }

    private void readTripleQuotedString() {
        if (currentChar == '\'') {
            buffer.append(currentChar);
            state = AutomataState.TRIPLE_QUOTED_STRING_WITH_QUOTE;
        } else if (currentChar == '\\') {
            state = AutomataState.ESCAPE;
        } else {
            if (currentChar == '\n')
                currentLine++;
            buffer.append(currentChar);
        }
    }

    private void readTripleQuotedStringWithQuote() {
        if (currentChar == '\'') {
            buffer.append(currentChar);
            state = AutomataState.TRIPLE_QUOTED_STRING_WITH_DOUBLE_QUOTE;
        } else {
            state = AutomataState.TRIPLE_QUOTED_STRING;
            readSingleQuotedString();
        }
    }

    private void readTripleQuotedStringWithDoubleQuote() {
        if (currentChar == '\'') {
            buffer.delete(buffer.length() - 2, buffer.length());
            quitString();
        } else {
            state = AutomataState.TRIPLE_QUOTED_STRING;
            readSingleQuotedString();
        }
    }

    private void readClosedSingleOrOpenedTripleQuotedString() {
        buffer.setLength(0);
        if (currentChar == '\'') {
            state = AutomataState.TRIPLE_QUOTED_STRING;
            currentStringType = StringType.TRIPLE_QUOTED;
        } else {
            currentStringType = StringType.NONE;
            endToken(TokenType.STRING_LITERAL, buffer.toString());
        }
    }

    private void quitString() {
        tokens.add(new Token(TokenType.STRING_LITERAL, buffer.toString(), tokenStartRow));
        buffer.setLength(0);
        state = AutomataState.INITIAL;
        currentStringType = StringType.NONE;
    }

    private void readEscaped() {
        Character escaped = Utils.escapeChar(currentChar);
        if (escaped != null)
            buffer.append(escaped);
        else {
            buffer.append('\\');
            buffer.append(currentChar);
        }
        switch (currentStringType) {
            case SINGLE_QUOTED -> state = AutomataState.SINGLE_QUOTED_STRING;
            case DOUBLE_QUOTED -> state = AutomataState.DOUBLE_QUOTED_STRING;
            case TRIPLE_QUOTED -> state = AutomataState.TRIPLE_QUOTED_STRING;
        }
    }

    private void readLineFeed() {
        if (!blankLine) {
            tokens.add(new Token(TokenType.NEWLINE, TokenType.NEWLINE.getValue(), currentLine));
            currentIndent = 0;
            blankLine = true;
            state = AutomataState.INDENT;
        } else if (tokens.isEmpty())
            state = AutomataState.INITIAL;
        else
            state = AutomataState.INDENT;
        currentLine++;
    }

    private void readFirstIndent() {
        if (!Character.isWhitespace(currentChar)) {
            if (currentChar != '#') {
                tokens.add(new Token(TokenType.ERROR, "Unexpected indent.", currentLine));
                blankLine = false;
            }
            setStateByCurrentChar();
        } else if (currentChar == '\n') {
            currentLine++;
            setStateByCurrentChar();
        }
    }

    private void readIndent() {
        if (Character.isWhitespace(currentChar)) {
            if (currentChar == ' ') {
                currentIndent++;
            } else if (currentChar == '\t') {
                currentIndent += (MAX_INDENT_LENGTH - (currentIndent % MAX_INDENT_LENGTH)) % MAX_INDENT_LENGTH;
            } else if (currentChar == '\n') {
                currentIndent = 0;
                setStateByCurrentChar();
            }
        } else if (currentChar == '\\') {
            blankLine = false;
            setStateByCurrentChar();
        } else if (currentChar == '#') {
            setStateByCurrentChar();
        } else {
            if (currentIndent > indentsList.get(indentsList.size() - 1)) {
                indentsList.add(currentIndent);
                tokens.add(new Token(TokenType.INDENT, TokenType.INDENT.getValue(), currentLine));
            } else if (currentIndent < indentsList.getLast()) {
                if (indentsList.contains(currentIndent))
                    while (indentsList.getLast() > currentIndent) {
                        indentsList.removeLast();
                        tokens.add(new Token(TokenType.DEDENT, TokenType.DEDENT.getValue(), currentLine));
                    }
                else
                    tokens.add(new Token(TokenType.ERROR, "Dedent does not match to any indentation level.", currentLine));
            }
            blankLine = false;
            setStateByCurrentChar();
        }
    }

    private void readBackslash() throws IOException {
        if (!Character.isWhitespace(currentChar)) {
            buffer.setLength(0);
            tokens.add(new Token(TokenType.ERROR, "Backslash does not continue a line.", tokenStartRow));
            state = AutomataState.INITIAL;
            in.reset();
        } else if (currentChar == '\n') {
            buffer.setLength(0);
            currentLine++;
            state = AutomataState.INITIAL;
        }
    }
}
