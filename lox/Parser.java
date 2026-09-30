package lox;

import java.util.List;
import java.util.ArrayList;

import static lox.TokenType.*;

class Parser {
  private static class ParseError extends RuntimeException {}

  private final List<Token> tokens;
  private int current = 0;

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  List<Expr> parseProgram() {
    List<Expr> statements = new ArrayList<>();
    while (!isAtEnd()) {
      try {
        if (match(RIVER)) {
          statements.add(riverDeclaration());
        } else if (match(DAM)) {
          statements.add(damDeclaration());
        } else if (match(FUN)) {
          statements.add(functionDeclaration());
        } else if (match(PLOT)) {
          statements.add(plotDeclaration());
        } else {
          Expr expr = expression();
          statements.add(expr);
          if (!check(EOF)) {
            consume(SEMICOLON, "Expect ';' after expression.");
          }
        }
      } catch (ParseError error) {
        synchronize();
      }
    }
    return statements;
  }

  // River declaration
  private Expr riverDeclaration() {
    Token name = consume(IDENTIFIER, "Expect river name after 'river'.");
    consume(EQUAL, "Expect '=' after river name.");
    Expr initializer = expression();
    
    // Semicolon is optional for river declarations at end of program
    if (!check(EOF)) {
      consume(SEMICOLON, "Expect ';' after river value.");
    }
    
    return new Expr.RiverDecl(name, initializer);
  }

  // Dam declaration
  private Expr damDeclaration() {
    Token name = consume(IDENTIFIER, "Expect dam name after 'dam'.");
    consume(EQUAL, "Expect '=' after dam name.");
    Expr initializer = expression();
    
    if (!check(EOF)) {
      consume(SEMICOLON, "Expect ';' after dam value.");
    }
    
    return new Expr.DamDecl(name, initializer);
  }

  // Function declaration
  private Expr functionDeclaration() {
    Token name = consume(IDENTIFIER, "Expect function name after 'fun'.");
    consume(LEFT_PAREN, "Expect '(' after function name.");
    
    List<Token> parameters = new ArrayList<>();
    if (!check(RIGHT_PAREN)) {
      do {
        if (parameters.size() >= 255) {
          error(peek(), "Can't have more than 255 parameters.");
        }
        parameters.add(consume(IDENTIFIER, "Expect parameter name."));
      } while (match(COMMA));
    }
    consume(RIGHT_PAREN, "Expect ')' after parameters.");
    
    // Function body can be a block or single expression
    Expr body;
    if (match(LEFT_BRACE)) {
      body = block();
    } else {
      body = expression();
      if (!check(EOF)) {
        consume(SEMICOLON, "Expect ';' after function expression.");
      }
    }
    
    return new Expr.Function(name, parameters, body);
  }

  private Expr plotDeclaration() {
    Expr value = expression();
    consume(SEMICOLON, "Expect ';' after plot value.");
    return value;
  }

  private Expr block() {
    List<Expr> statements = new ArrayList<>();
    
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      if (match(RETURN)) {
        statements.add(returnStatement());
      } else if (match(IF)) {
        statements.add(ifStatement());
      } else if (match(LEFT_BRACE)) {
        statements.add(block());
      } else {
        Expr expr = expression();
        statements.add(expr);
        if (!check(RIGHT_BRACE)) {
          consume(SEMICOLON, "Expect ';' after expression.");
        }
      }
    }
    
    consume(RIGHT_BRACE, "Expect '}' after block.");
    return new Expr.Block(statements);
  }

  private Expr returnStatement() {
    Token keyword = previous();
    Expr value = null;
    if (!check(SEMICOLON)) {
      value = expression();
    }
    consume(SEMICOLON, "Expect ';' after return value.");
    return new Expr.Return(keyword, value);
  }

  private Expr ifStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'if'.");
    Expr condition = expression();
    consume(RIGHT_PAREN, "Expect ')' after if condition.");

    Expr thenBranch;
    if (match(LEFT_BRACE)) {
      thenBranch = block();
    } else {
      thenBranch = expression();
      if (!check(EOF) && !check(RIGHT_BRACE)) {
        consume(SEMICOLON, "Expect ';' after if expression.");
      }
    }

    Expr elseBranch = null;
    if (match(ELSE)) {
      if (match(LEFT_BRACE)) {
        elseBranch = block();
      } else {
        elseBranch = expression();
        if (!check(EOF) && !check(RIGHT_BRACE)) {
          consume(SEMICOLON, "Expect ';' after else expression.");
        }
      }
    }

    return new Expr.If(condition, thenBranch, elseBranch);
  }

  private Expr expression() {
    return assignment();
  }

  private Expr assignment() {
    Expr expr = or();

    if (match(EQUAL)) {
      Token equals = previous();
      Expr value = assignment();

      if (expr instanceof Expr.Variable) {
        Token name = ((Expr.Variable)expr).name;
        return new Expr.Assign(name, value);
      }

      error(equals, "Invalid assignment target.");
    }

    return expr;
  }

  private Expr or() {
    Expr expr = and();

    while (match(OR)) {
      Token operator = previous();
      Expr right = and();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr and() {
    Expr expr = equality();

    while (match(AND)) {
      Token operator = previous();
      Expr right = equality();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr equality() {
    Expr expr = comparison();

    while (match(BANG_EQUAL, EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr comparison() {
    Expr expr = term();

    while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
      Token operator = previous();
      Expr right = term();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr term() {
    Expr expr = factor();

    while (match(MINUS, PLUS)) {
      Token operator = previous();
      Expr right = factor();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr factor() {
    Expr expr = unary();

    while (match(SLASH, STAR)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr unary() {
    if (match(BANG, MINUS)) {
      Token operator = previous();
      Expr right = unary();
      return new Expr.Unary(operator, right);
    }

    return call();
  }

  private Expr call() {
    Expr expr = primary();

    while (true) {
      if (match(LEFT_PAREN)) {
        expr = finishCall(expr);
      } else if (match(ARROW_LEFT)) {
        Token operator = previous();
        Expr river = primary();
        expr = new Expr.DamApply(expr, operator, river);
      } else {
        break;
      }
    }

    return expr;
  }

  private Expr finishCall(Expr callee) {
    List<Expr> arguments = new ArrayList<>();
    if (!check(RIGHT_PAREN)) {
      do {
        if (arguments.size() >= 255) {
          error(peek(), "Can't have more than 255 arguments.");
        }
        arguments.add(expression());
      } while (match(COMMA));
    }

    Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

    return new Expr.Call(callee, paren, arguments);
  }

  private Expr primary() {
    if (match(FALSE)) return new Expr.Literal(false);
    if (match(TRUE)) return new Expr.Literal(true);
    if (match(NIL)) return new Expr.Literal(null);

    if (match(NUMBER, STRING)) {
      return new Expr.Literal(previous().literal);
    }

    // Rainfall pattern: [peak ~ duration] @ total
    if (match(LEFT_BRACKET)) {
      return rainfallPattern();
    }

    if (match(IDENTIFIER)) {
      return new Expr.Variable(previous());
    }

    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      consume(RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(expr);
    }

    throw error(peek(), "Expect expression.");
  }

  private Expr rainfallPattern() {
    Token peakToken = consume(NUMBER, "Expect peak day value.");
    consume(TILDE, "Expect '~' after peak.");
    Token durationToken = consume(NUMBER, "Expect duration value.");
    consume(RIGHT_BRACKET, "Expect ']' after duration.");
    consume(AT, "Expect '@' after rainfall pattern.");
    Token totalToken = consume(NUMBER, "Expect total flow value.");

    return new Expr.RainfallPattern(
        new Expr.Literal(peakToken.literal),
        new Expr.Literal(durationToken.literal),
        new Expr.Literal(totalToken.literal)
    );
  }

  // Helpers
  
  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return true;
      }
    }
    return false;
  }

  private Token consume(TokenType type, String message) {
    if (check(type)) return advance();
  
    if (type == SEMICOLON && isAtEnd()) {
      return new Token(SEMICOLON, ";", null, previous().line);
    }
    
    throw error(peek(), message);
  }

  private boolean check(TokenType type) {
    if (isAtEnd()) return false;
    return peek().type == type;
  }

  private Token advance() {
    if (!isAtEnd()) current++;
    return previous();
  }

  private boolean isAtEnd() {
    return peek().type == EOF;
  }

  private Token peek() {
    return tokens.get(current);
  }

  private Token previous() {
    return tokens.get(current - 1);
  }

  private ParseError error(Token token, String message) {
    Lox.error(token, message);
    return new ParseError();
  }

  private void synchronize() {
    advance();

    while (!isAtEnd()) {
      if (previous().type == SEMICOLON) return;

      switch (peek().type) {
        case RIVER:
        case DAM:
        case FUN:
        case VAR:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN:
        case PLOT:
          return;
      }

      advance();
    }
  }
}