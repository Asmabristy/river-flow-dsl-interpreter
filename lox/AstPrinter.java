package lox;

import java.util.List;

class AstPrinter implements Expr.Visitor<String> {
  String print(Expr expr) {
    return expr.accept(this);
  }

  @Override
  public String visitBinaryExpr(Expr.Binary expr) {
    if (expr.operator.type == TokenType.PLUS) {
      return expr.left.accept(this) + " + " + expr.right.accept(this);
    }
    return parenthesize(expr.operator.lexeme, expr.left, expr.right);
  }

  @Override
  public String visitGroupingExpr(Expr.Grouping expr) {
    return parenthesize("group", expr.expression);
  }

  @Override
  public String visitLiteralExpr(Expr.Literal expr) {
    if (expr.value == null) return "nil";
    return expr.value.toString();
  }

  @Override
  public String visitUnaryExpr(Expr.Unary expr) {
    return parenthesize(expr.operator.lexeme, expr.right);
  }

  @Override
  public String visitRainfallPatternExpr(Expr.RainfallPattern expr) {
    return "[" + expr.peak.accept(this) + " ~ " + expr.duration.accept(this) + "] @ " + expr.total.accept(this);
  }

  @Override
  public String visitVariableExpr(Expr.Variable expr) {
    return expr.name.lexeme;
  }

  @Override
  public String visitRiverDeclExpr(Expr.RiverDecl expr) {
    return "river " + expr.name.lexeme + " = " + expr.initializer.accept(this);
  }

  @Override
  public String visitAssignExpr(Expr.Assign expr) {
    return expr.name.lexeme + " = " + expr.value.accept(this);
  }

  @Override
  public String visitLogicalExpr(Expr.Logical expr) {
    return parenthesize(expr.operator.lexeme, expr.left, expr.right);
  }

  @Override
  public String visitCallExpr(Expr.Call expr) {
    StringBuilder builder = new StringBuilder();
    builder.append(expr.callee.accept(this));
    builder.append("(");
    for (int i = 0; i < expr.arguments.size(); i++) {
      builder.append(expr.arguments.get(i).accept(this));
      if (i < expr.arguments.size() - 1) builder.append(", ");
    }
    builder.append(")");
    return builder.toString();
  }

  @Override
  public String visitDamApplyExpr(Expr.DamApply expr) {
    return expr.dam.accept(this) + " <- " + expr.river.accept(this);
  }

  @Override
  public String visitFunctionExpr(Expr.Function expr) {
    StringBuilder builder = new StringBuilder();
    builder.append("fun ").append(expr.name.lexeme).append("(");
    for (int i = 0; i < expr.params.size(); i++) {
      builder.append(expr.params.get(i).lexeme);
      if (i < expr.params.size() - 1) builder.append(", ");
    }
    builder.append(") ");
    builder.append(expr.body.accept(this));
    return builder.toString();
  }

  @Override
  public String visitIfExpr(Expr.If expr) {
    String result = "if (" + expr.condition.accept(this) + ") " + expr.thenBranch.accept(this);
    if (expr.elseBranch != null) {
      result += " else " + expr.elseBranch.accept(this);
    }
    return result;
  }

  @Override
  public String visitBlockExpr(Expr.Block expr) {
    StringBuilder builder = new StringBuilder();
    builder.append("{\n");
    for (Expr statement : expr.statements) {
      builder.append("  ").append(statement.accept(this)).append("\n");
    }
    builder.append("}");
    return builder.toString();
  }

  @Override
  public String visitReturnExpr(Expr.Return expr) {
    if (expr.value == null) return "return;";
    return "return " + expr.value.accept(this) + ";";
  }

  @Override
  public String visitDamDeclExpr(Expr.DamDecl expr) {
    return "dam " + expr.name.lexeme + " = " + expr.initializer.accept(this);
  }

  private String parenthesize(String name, Expr... exprs) {
    StringBuilder builder = new StringBuilder();

    builder.append("(").append(name);
    for (Expr expr : exprs) {
      builder.append(" ");
      builder.append(expr.accept(this));
    }
    builder.append(")");

    return builder.toString();
  }

  
  public static void testParser() {
    
    Expr pattern1 = new Expr.RainfallPattern(
        new Expr.Literal(2.0),
        new Expr.Literal(4.0), 
        new Expr.Literal(3.5)
    );
    System.out.println("Rainfall pattern: " + new AstPrinter().print(pattern1));
    
    Expr varA = new Expr.Variable(new Token(TokenType.IDENTIFIER, "A", null, 1));
    Expr varB = new Expr.Variable(new Token(TokenType.IDENTIFIER, "B", null, 1));
    Expr combination = new Expr.Binary(varA, new Token(TokenType.PLUS, "+", null, 1), varB);
    System.out.println("River combination: " + new AstPrinter().print(combination));

    Expr riverDecl = new Expr.RiverDecl(
        new Token(TokenType.IDENTIFIER, "main_river", null, 1),
        combination
    );
    System.out.println("River declaration: " + new AstPrinter().print(riverDecl));
    
    // Test dam declaration
    Expr damDecl = new Expr.DamDecl(
        new Token(TokenType.IDENTIFIER, "smart_dam", null, 1),
        new Expr.Variable(new Token(TokenType.IDENTIFIER, "devlins_trivial_dam", null, 1))
    );
    System.out.println("Dam declaration: " + new AstPrinter().print(damDecl));
    
    // Test dam application
    Expr damApply = new Expr.DamApply(
        new Expr.Variable(new Token(TokenType.IDENTIFIER, "smart_dam", null, 1)),
        new Token(TokenType.ARROW_LEFT, "<-", null, 1),
        new Expr.Variable(new Token(TokenType.IDENTIFIER, "devlins_creek", null, 1))
    );
    System.out.println("Dam application: " + new AstPrinter().print(damApply));
  }
}