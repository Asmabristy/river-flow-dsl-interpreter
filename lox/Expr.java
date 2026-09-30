package lox;

import java.util.List;

abstract class Expr {
  interface Visitor<R> {
    R visitBinaryExpr(Binary expr);
    R visitGroupingExpr(Grouping expr);
    R visitLiteralExpr(Literal expr);
    R visitUnaryExpr(Unary expr);
    R visitRainfallPatternExpr(RainfallPattern expr);
    R visitVariableExpr(Variable expr);
    R visitRiverDeclExpr(RiverDecl expr);
    R visitAssignExpr(Assign expr);
    R visitLogicalExpr(Logical expr);
    R visitCallExpr(Call expr);
    R visitDamApplyExpr(DamApply expr);
    R visitFunctionExpr(Function expr);
    R visitIfExpr(If expr);
    R visitBlockExpr(Block expr);
    R visitReturnExpr(Return expr);
    R visitDamDeclExpr(DamDecl expr);
  }

  static class Binary extends Expr {
    Binary(Expr left, Token operator, Expr right) {
      this.left = left;
      this.operator = operator;
      this.right = right;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitBinaryExpr(this);
    }

    final Expr left;
    final Token operator;
    final Expr right;
  }

  static class Grouping extends Expr {
    Grouping(Expr expression) {
      this.expression = expression;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitGroupingExpr(this);
    }

    final Expr expression;
  }

  static class Literal extends Expr {
    Literal(Object value) {
      this.value = value;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitLiteralExpr(this);
    }

    final Object value;
  }

  static class Unary extends Expr {
    Unary(Token operator, Expr right) {
      this.operator = operator;
      this.right = right;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitUnaryExpr(this);
    }

    final Token operator;
    final Expr right;
  }

  static class RainfallPattern extends Expr {
    RainfallPattern(Expr peak, Expr duration, Expr total) {
      this.peak = peak;
      this.duration = duration;
      this.total = total;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitRainfallPatternExpr(this);
    }

    final Expr peak;
    final Expr duration;
    final Expr total;
  }

  static class Variable extends Expr {
    Variable(Token name) {
      this.name = name;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitVariableExpr(this);
    }

    final Token name;
  }

  static class RiverDecl extends Expr {
    RiverDecl(Token name, Expr initializer) {
      this.name = name;
      this.initializer = initializer;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitRiverDeclExpr(this);
    }

    final Token name;
    final Expr initializer;
    
    // Helper method to get the river name as string
    public String getRiverName() {
      return name.lexeme;
    }
    
    // Helper method to get dependencies from the initializer expression
    public java.util.List<String> getDependencies() {
      java.util.List<String> dependencies = new java.util.ArrayList<>();
      findDependencies(initializer, dependencies);
      return dependencies;
    }
    
    private void findDependencies(Expr expr, java.util.List<String> dependencies) {
      if (expr instanceof Variable) {
        String varName = ((Variable) expr).name.lexeme;
        if (!dependencies.contains(varName)) {
          dependencies.add(varName);
        }
      } else if (expr instanceof Binary) {
        Binary binary = (Binary) expr;
        findDependencies(binary.left, dependencies);
        findDependencies(binary.right, dependencies);
      } else if (expr instanceof Grouping) {
        Grouping grouping = (Grouping) expr;
        findDependencies(grouping.expression, dependencies);
      }
      // Rainfall patterns and literals have no dependencies
    }
  }

  static class Assign extends Expr {
    Assign(Token name, Expr value) {
      this.name = name;
      this.value = value;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitAssignExpr(this);
    }

    final Token name;
    final Expr value;
    
    public String getVarName() {
      return name.lexeme;
    }
    
    public java.util.List<String> getDependencies() {
      java.util.List<String> dependencies = new java.util.ArrayList<>();
      findDependencies(value, dependencies);
      return dependencies;
    }
    
    private void findDependencies(Expr expr, java.util.List<String> dependencies) {
      if (expr instanceof Variable) {
        String varName = ((Variable) expr).name.lexeme;
        if (!dependencies.contains(varName)) {
          dependencies.add(varName);
        }
      } else if (expr instanceof Binary) {
        Binary binary = (Binary) expr;
        findDependencies(binary.left, dependencies);
        findDependencies(binary.right, dependencies);
      } else if (expr instanceof Grouping) {
        Grouping grouping = (Grouping) expr;
        findDependencies(grouping.expression, dependencies);
      }
    }
  }

  // New expression types for functions and dams

  static class Logical extends Expr {
    Logical(Expr left, Token operator, Expr right) {
      this.left = left;
      this.operator = operator;
      this.right = right;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitLogicalExpr(this);
    }

    final Expr left;
    final Token operator;
    final Expr right;
  }

  static class Call extends Expr {
    Call(Expr callee, Token paren, List<Expr> arguments) {
      this.callee = callee;
      this.paren = paren;
      this.arguments = arguments;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitCallExpr(this);
    }

    final Expr callee;
    final Token paren;
    final List<Expr> arguments;
  }

  static class DamApply extends Expr {
    DamApply(Expr dam, Token operator, Expr river) {
      this.dam = dam;
      this.operator = operator;
      this.river = river;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitDamApplyExpr(this);
    }

    final Expr dam;
    final Token operator;
    final Expr river;
  }

  static class Function extends Expr {
    Function(Token name, List<Token> params, Expr body) {
      this.name = name;
      this.params = params;
      this.body = body;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitFunctionExpr(this);
    }

    final Token name;
    final List<Token> params;
    final Expr body;
  }

  static class If extends Expr {
    If(Expr condition, Expr thenBranch, Expr elseBranch) {
      this.condition = condition;
      this.thenBranch = thenBranch;
      this.elseBranch = elseBranch;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitIfExpr(this);
    }

    final Expr condition;
    final Expr thenBranch;
    final Expr elseBranch;
  }

  static class Block extends Expr {
    Block(List<Expr> statements) {
      this.statements = statements;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitBlockExpr(this);
    }

    final List<Expr> statements;
  }

  static class Return extends Expr {
    Return(Token keyword, Expr value) {
      this.keyword = keyword;
      this.value = value;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitReturnExpr(this);
    }

    final Token keyword;
    final Expr value;
  }

  static class DamDecl extends Expr {
    DamDecl(Token name, Expr initializer) {
      this.name = name;
      this.initializer = initializer;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitDamDeclExpr(this);
    }

    final Token name;
    final Expr initializer;
    
    public String getDamName() {
      return name.lexeme;
    }
  }

  abstract <R> R accept(Visitor<R> visitor);
}