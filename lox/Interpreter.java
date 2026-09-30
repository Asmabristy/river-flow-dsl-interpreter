package lox;

import java.util.*;

public class Interpreter implements Expr.Visitor<Object> {

    final Environment globals = new Environment();
    private Environment environment = globals;

    private final Map<String, double[]> rivers = new LinkedHashMap<>();
    private final Map<String, Expr.RiverDecl> riverDeclarations = new HashMap<>();
    private final Map<String, Expr.DamDecl> damDeclarations = new HashMap<>();
    private final List<String> declarationOrder = new ArrayList<>();

    private final LinkedHashMap<String, double[]> damOutflows = new LinkedHashMap<>();
    private final Map<String, Double> damLevels = new LinkedHashMap<>();
    private final Map<String, LoxFunction> functions = new HashMap<>();

    // Rainfall array (mm/day)
    private double[] rainfall = {5.0, 0.0, 0.0, 2.0, 1.0, 8.0, 0.0, 0.0, 0.0, 0.0};
    private boolean insideDamFunction = false;

    public Interpreter() {
        environment = globals;

        // Built-in: clock()
        globals.define("clock", new LoxCallable() {
            @Override public int arity() { return 0; }
            @Override public Object call(Interpreter i, List<Object> args) {
                return (double) System.currentTimeMillis() / 1000.0;
            }
            @Override public String toString() { return "<native fn clock>"; }
        });

        // Built-in: level(dam)
        globals.define("level", new LoxCallable() {
            @Override public int arity() { return 1; }
            @Override public Object call(Interpreter i, List<Object> args) {
                Object arg = args.get(0);
                String damName;
                if (arg instanceof LoxFunction)
                    damName = ((LoxFunction) arg).declaration.name.lexeme;
                else damName = String.valueOf(arg);
                return i.getDamLevel(damName);
            }
            @Override public String toString() { return "<native fn level>"; }
        });
    }

    public void interpret(Expr expr) {
        try {
            if (expr instanceof Expr.RiverDecl) {
                Expr.RiverDecl riverDecl = (Expr.RiverDecl) expr;
                riverDeclarations.put(riverDecl.name.lexeme, riverDecl);
                declarationOrder.add(riverDecl.name.lexeme);
            } else if (expr instanceof Expr.DamDecl) {
                Expr.DamDecl damDecl = (Expr.DamDecl) expr;
                damDeclarations.put(damDecl.name.lexeme, damDecl);
                declarationOrder.add(damDecl.name.lexeme);
                damLevels.put(damDecl.name.lexeme, 0.0);
            } else if (expr instanceof Expr.Function) {
                Expr.Function func = (Expr.Function) expr;
                LoxFunction f = new LoxFunction(func, environment);
                functions.put(func.name.lexeme, f);
                environment.define(func.name.lexeme, f);
            }
            evaluate(expr);
        } catch (RuntimeError e) {
            Lox.runtimeError(e);
        } catch (Return ignored) {}
    }

    public Object evaluate(Expr expr) { return expr.accept(this); }

    //  Expression visitors 

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case PLUS: {
                if (left instanceof double[] && right instanceof double[])
                    return addRiverFlows((double[]) left, (double[]) right);
                Double l = asScalar(left), r = asScalar(right);
                if (l != null && r != null) return l + r;
                if (left instanceof double[] && r != null) return addScalar((double[]) left, r);
                if (right instanceof double[] && l != null) return addScalar((double[]) right, l);
                break;
            }
            case MINUS: {
                Double l = asScalar(left), r = asScalar(right);
                if (l != null && r != null) return l - r;
                break;
            }
            case STAR: {
                Double l = asScalar(left), r = asScalar(right);
                if (l != null && r != null) return l * r;
                break;
            }
            case SLASH: {
                Double l = asScalar(left), r = asScalar(right);
                if (l != null && r != null) return l / r;
                break;
            }
            case GREATER: {
                Double l = asScalar(left), r = asScalar(right);
                if (l != null && r != null) return l > r;
                break;
            }
            case GREATER_EQUAL: {
                Double l = asScalar(left), r = asScalar(right);
                if (l != null && r != null) return l >= r;
                break;
            }
            case LESS: {
                Double l = asScalar(left), r = asScalar(right);
                if (l != null && r != null) return l < r;
                break;
            }
            case LESS_EQUAL: {
                Double l = asScalar(left), r = asScalar(right);
                if (l != null && r != null) return l <= r;
                break;
            }
            case EQUAL_EQUAL:
                return isEqual(left, right);
            case BANG_EQUAL:
                return !isEqual(left, right);
        }

        throw new RuntimeError(expr.operator, "Operands must be numbers or river flows.");
    }

    // Converts number-like or array to scalar Double
    private Double asScalar(Object o) {
        if (o instanceof Double) return (Double) o;
        if (o instanceof Integer) return ((Integer) o).doubleValue();
        if (o instanceof double[]) {
            double[] arr = (double[]) o;
            return arr.length > 0 ? arr[0] : 0.0;
        }
        return null;
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        if (expr.value instanceof Double) {
            double v = (Double) expr.value;
            double[] arr = new double[rainfall.length];
            Arrays.fill(arr, v);
            return arr;
        }
        return expr.value;
    }

    @Override
    public Object visitRainfallPatternExpr(Expr.RainfallPattern expr) {
        double peak = asScalar(evaluate(expr.peak));
        double duration = asScalar(evaluate(expr.duration));
        double total = asScalar(evaluate(expr.total));

        if (insideDamFunction) {
            double[] constant = new double[rainfall.length];
            Arrays.fill(constant, total);
            return constant;
        }
        return calculateFlowDistribution(peak, duration, total);
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        String name = expr.name.lexeme;
        if (rivers.containsKey(name)) return rivers.get(name);
        return environment.get(expr.name);
    }

    @Override
    public Object visitRiverDeclExpr(Expr.RiverDecl expr) {
        String name = expr.name.lexeme;
        Object val = evaluate(expr.initializer);
        if (val instanceof double[]) {
            rivers.put(name, (double[]) val);
            return val;
        }
        throw new RuntimeError(expr.name, "River must evaluate to flow array.");
    }

    @Override
    public Object visitDamDeclExpr(Expr.DamDecl expr) {
        String name = expr.name.lexeme;
        Object val = evaluate(expr.initializer);
        if (val instanceof LoxCallable) {
            environment.define(name, val);
            return val;
        }
        throw new RuntimeError(expr.name, "Dam must be a function.");
    }

    @Override public Object visitGroupingExpr(Expr.Grouping e) { return evaluate(e.expression); }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);
        if (expr.operator.type == TokenType.BANG) return !isTruthy(right);
        if (expr.operator.type == TokenType.MINUS) {
            Double r = asScalar(right);
            if (r != null) return -r;
            throw new RuntimeError(expr.operator, "Operand must be a number.");
        }
        return null;
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object v = evaluate(expr.value);
        environment.assign(expr.name, v);
        return v;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);
        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (!isTruthy(left)) return left;
        }
        return evaluate(expr.right);
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);
        List<Object> args = new ArrayList<>();
        for (Expr a : expr.arguments) args.add(evaluate(a));

        if (!(callee instanceof LoxCallable))
            throw new RuntimeError(expr.paren, "Can only call functions.");

        LoxCallable fn = (LoxCallable) callee;
        if (args.size() != fn.arity())
            throw new RuntimeError(expr.paren, "Expected " + fn.arity() + " args but got " + args.size() + ".");

        return fn.call(this, args);
    }

    @Override
    public Object visitDamApplyExpr(Expr.DamApply expr) {
        Object damObj = evaluate(expr.dam);
        Object riverObj = evaluate(expr.river);

        if (!(damObj instanceof LoxCallable))
            throw new RuntimeError(expr.operator, "Left side of <- must be a dam function.");
        if (!(riverObj instanceof double[]))
            throw new RuntimeError(expr.operator, "Right side of <- must be a river flow.");

        String damName = (expr.dam instanceof Expr.Variable)
                ? ((Expr.Variable) expr.dam).name.lexeme
                : "dam";

        double[] inflow = (double[]) riverObj;
        updateDamLevel(damName, inflow);

        LoxCallable damFn = (LoxCallable) damObj;

        boolean prev = insideDamFunction;
        insideDamFunction = true;
        try {
            double[] outflow = (double[]) damFn.call(this, Collections.singletonList(inflow));
            damOutflows.put(damName, outflow);
            return outflow;
        } finally {
            insideDamFunction = prev;
        }
    }

    @Override public Object visitFunctionExpr(Expr.Function e) { return new LoxFunction(e, environment); }

    @Override
    public Object visitIfExpr(Expr.If expr) {
        Object cond = evaluate(expr.condition);
        if (isTruthy(cond)) return evaluate(expr.thenBranch);
        if (expr.elseBranch != null) return evaluate(expr.elseBranch);
        return null;
    }

    @Override
    public Object visitBlockExpr(Expr.Block expr) {
        Object res = null;
        for (Expr s : expr.statements) res = evaluate(s);
        return res;
    }

    @Override
    public Object visitReturnExpr(Expr.Return expr) {
        Object v = (expr.value != null) ? evaluate(expr.value) : null;
        throw new Return(v);
    }

    //  Helpers 

    private double[] addRiverFlows(double[] a, double[] b) {
        double[] r = new double[rainfall.length];
        for (int i = 0; i < r.length; i++) r[i] = a[i] + b[i];
        return r;
    }

    private double[] addScalar(double[] a, double s) {
        double[] r = new double[a.length];
        for (int i = 0; i < a.length; i++) r[i] = a[i] + s;
        return r;
    }

    private double[] calculateFlowDistribution(double peak, double duration, double total) {
        int days = rainfall.length;
        double[] flows = new double[days];
        double[] pattern = new double[(int) duration];
        double sum = 0;
        for (int i = 0; i < duration; i++) {
            double d = Math.abs(i - peak + 1);
            pattern[i] = Math.max(0, 1 - d / (duration / 2));
            sum += pattern[i];
        }
        for (int i = 0; i < duration && i < days; i++) flows[i] = pattern[i] * total / sum;
        for (int i = 0; i < days; i++) flows[i] *= rainfall[i];
        return flows;
    }

    private boolean isTruthy(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Double) return (Double) o != 0.0;
        if (o instanceof double[])
            for (double v : (double[]) o) if (v != 0.0) return true;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;
        if (a instanceof double[] && b instanceof double[]) {
            double[] A = (double[]) a, B = (double[]) b;
            if (A.length != B.length) return false;
            for (int i = 0; i < A.length; i++) if (A[i] != B[i]) return false;
            return true;
        }
        return a.equals(b);
    }

    public void updateDamLevel(String name, double[] inflow) {
        double total = 0;
        for (double f : inflow) total += f;
        damLevels.put(name, damLevels.getOrDefault(name, 0.0) + total);
    }

    public double getDamLevel(String name) {
        return damLevels.getOrDefault(name, 0.0);
    }

    //  Final Output 
    public void printAllRivers() {
        int days = rainfall.length;

        System.out.println("Rainfall (mm) per day:");
        System.out.print("Day:      ");
        for (int i = 0; i < days; i++) System.out.printf("%-8d", i);
        System.out.println();
        System.out.print("Rainfall: ");
        for (int i = 0; i < days; i++) System.out.printf("%-8.1f", rainfall[i]);
        System.out.println("\n");

        System.out.println("Flows (L/second)");
        System.out.print("Day:              ");
        for (int i = 0; i < days; i++) System.out.printf("%-8d", i);
        System.out.println();

        List<String> preferred = Arrays.asList("A", "B", "C");
        for (String name : preferred)
            if (rivers.containsKey(name)) printRiver(name, rivers.get(name), days);

        for (String name : damOutflows.keySet())
            printRiver(name, damOutflows.get(name), days);

        for (String name : declarationOrder)
            if (rivers.containsKey(name) && !preferred.contains(name))
                printRiver(name, rivers.get(name), days);

        System.out.println();

        if (!damLevels.isEmpty()) {
            System.out.println("Dam Levels:");
            for (Map.Entry<String, Double> e : damLevels.entrySet()) {
                System.out.printf("%-18s: %.2f\n", e.getKey(), e.getValue());
            }
        }
    }

    private void printRiver(String name, double[] flows, int days) {
        System.out.printf("%-18s", name);
        for (int i = 0; i < days; i++) System.out.printf("%-8.2f", flows[i]);
        System.out.println();
    }

    // Runtime classes 

    private static class LoxFunction implements LoxCallable {
        private final Expr.Function declaration;
        private final Environment closure;
        LoxFunction(Expr.Function d, Environment c) { this.declaration = d; this.closure = c; }
        @Override public int arity() { return declaration.params.size(); }
        @Override public Object call(Interpreter i, List<Object> args) {
            Environment local = new Environment(closure);
            for (int j = 0; j < declaration.params.size(); j++)
                local.define(declaration.params.get(j).lexeme, args.get(j));
            Environment prev = i.environment;
            i.environment = local;
            try { return i.evaluate(declaration.body); }
            catch (Return r) { return r.value; }
            finally { i.environment = prev; }
        }
        @Override public String toString() { return "<fn " + declaration.name.lexeme + ">"; }
    }

    private static class Return extends RuntimeException {
        final Object value;
        Return(Object v) { super(null, null, false, false); value = v; }
    }

    private static class Environment {
        final Environment enclosing;
        private final Map<String, Object> values = new HashMap<>();
        Environment() { enclosing = null; }
        Environment(Environment e) { enclosing = e; }
        void define(String n, Object v) { values.put(n, v); }
        Object get(Token n) {
            if (values.containsKey(n.lexeme)) return values.get(n.lexeme);
            if (enclosing != null) return enclosing.get(n);
            throw new RuntimeError(n, "Undefined variable '" + n.lexeme + "'.");
        }
        void assign(Token n, Object v) {
            if (values.containsKey(n.lexeme)) { values.put(n.lexeme, v); return; }
            if (enclosing != null) { enclosing.assign(n, v); return; }
            throw new RuntimeError(n, "Undefined variable '" + n.lexeme + "'.");
        }
    }

    private interface LoxCallable {
        int arity();
        Object call(Interpreter i, List<Object> args);
    }
}
