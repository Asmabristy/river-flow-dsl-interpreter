package lox;

enum TokenType {
    // Single-character tokens.
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
    COMMA, DOT, MINUS, PLUS, SEMICOLON, SLASH, STAR,
    // One or two character tokens.
    BANG, BANG_EQUAL,
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,
    // Literals.
    IDENTIFIER, STRING, NUMBER,
    // Keywords.
    AND, CLASS, ELSE, FALSE, FUN, FOR, IF, NIL, OR,
    PRINT, RETURN, SUPER, THIS, TRUE, VAR, WHILE,
    // Tokens for river system. 
    RIVER, DAM, DAYS,
    // New Literals & Operators 
    ARROW_LEFT, ARROW_RIGHT, TILDE, AT, END_FLOW, RAIN, // <-, ->, ~, @, \ 
    // Punctuation
    LEFT_BRACKET, RIGHT_BRACKET, // [ and ] 
    PLOT,
    // End of file token.
    EOF 
}