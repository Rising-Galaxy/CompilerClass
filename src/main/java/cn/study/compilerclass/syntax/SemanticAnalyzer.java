package cn.study.compilerclass.syntax;

import cn.study.compilerclass.lexer.Token;
import cn.study.compilerclass.model.FunctionTableEntry;
import cn.study.compilerclass.model.SymbolTableEntry;
import cn.study.compilerclass.model.VariableTableEntry;
import cn.study.compilerclass.parser.TokenTreeView;
import cn.study.compilerclass.utils.OutInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;

/**
 * 语义分析器
 *
 * 主要功能：
 * 1. 符号表管理（变量定义、使用检查）
 * 2. 类型检查（表达式类型推导、类型匹配验证）
 * 3. 常量处理
 * 4. 标识冗余计算（无副作用表达式警告）
 * 5. 作用域管理（嵌套作用域支持）
 * 6. 函数声明与调用分析
 */
public class SemanticAnalyzer {

    private final OutInfo outInfos;
    private final String src = "语义分析";
    private final Stack<Scope> scopeStack;        // 作用域栈
    private final Map<String, FunctionSymbol> functionSymbols; // 函数符号表
    private List<String> errors;                  // 错误信息列表
    private List<String> warnings;                // 警告信息列表

    // 当前是否在函数内部（用于控制作用域）
    private boolean inFunction = false;

    // 类型信息枚举
    private enum TypeInfo {
        INT,        // 整数类型
        FLOAT,      // 浮点类型
        BOOL,       // 布尔类型
        VOID,       // 空类型
        UNKNOWN     // 未知类型
    }

    /**
     * 作用域类，用于管理符号表和作用域层次
     */
    private class Scope {
        private final String name;                 // 作用域名称
        private final Map<String, Symbol> symbols; // 符号表
        private final Scope parent;               // 父作用域

        /**
         * 构造函数
         *
         * @param name 作用域名称
         * @param parent 父作用域
         */
        public Scope(String name, Scope parent) {
            this.name = name;
            this.parent = parent;
            this.symbols = new HashMap<>();
        }

        /**
         * 在当前作用域中添加符号
         *
         * @param symbol 符号
         * @return 如果符号已存在返回false，否则返回true
         */
        public boolean addSymbol(Symbol symbol) {
            if (symbols.containsKey(symbol.getName())) {
                return false;
            }
            symbols.put(symbol.getName(), symbol);
            return true;
        }

        /**
         * 查找符号（只在当前作用域）
         *
         * @param name 符号名称
         * @return 符号对象，如果不存在返回null
         */
        public Symbol getSymbol(String name) {
            return symbols.get(name);
        }

        /**
         * 获取作用域中的所有符号
         *
         * @return 符号表
         */
        public Map<String, Symbol> getSymbols() {
            return symbols;
        }

        /**
         * 获取父作用域
         *
         * @return 父作用域
         */
        public Scope getParent() {
            return parent;
        }

        /**
         * 获取作用域名称
         *
         * @return 作用域名称
         */
        public String getName() {
            return name;
        }
    }

    /**
     * 函数符号类，扩展基本符号
     */
    private class FunctionSymbol extends Symbol {
        private final List<Symbol> parameters;    // 参数列表
        private final TypeInfo returnType;        // 返回类型

        /**
         * 构造函数
         *
         * @param name 函数名
         * @param returnType 返回类型
         */
        public FunctionSymbol(String name, TypeInfo returnType) {
            super(name, returnType, false); // 函数不是常量
            this.parameters = new ArrayList<>();
            this.returnType = returnType;
            // 函数定义后就认为初始化了
            this.setInitialized(true);
        }

        /**
         * 添加参数
         *
         * @param param 参数符号
         */
        public void addParameter(Symbol param) {
            parameters.add(param);
        }

        /**
         * 获取参数列表
         *
         * @return 参数列表
         */
        public List<Symbol> getParameters() {
            return parameters;
        }

        /**
         * 获取返回类型
         *
         * @return 返回类型
         */
        public TypeInfo getReturnType() {
            return returnType;
        }
    }

    /**
     * 符号类
     */
    private class Symbol {
        private final String name;       // 符号名称
        private final TypeInfo type;     // 符号类型
        private final boolean isConstant; // 是否是常量
        private boolean initialized;     // 是否已初始化
        private boolean used;           // 是否已使用

        /**
         * 构造函数
         *
         * @param name 符号名称
         * @param type 符号类型
         * @param isConstant 是否是常量
         */
        public Symbol(String name, TypeInfo type, boolean isConstant) {
            this.name = name;
            this.type = type;
            this.isConstant = isConstant;
            this.initialized = false;
            this.used = false;
        }

        /**
         * 获取符号名称
         *
         * @return 符号名称
         */
        public String getName() {
            return name;
        }

        /**
         * 获取符号类型
         *
         * @return 符号类型
         */
        public TypeInfo getType() {
            return type;
        }

        /**
         * 是否是常量
         *
         * @return 是否是常量
         */
        public boolean isConstant() {
            return isConstant;
        }

        /**
         * 是否已初始化
         *
         * @return 是否已初始化
         */
        public boolean isInitialized() {
            return initialized;
        }

        /**
         * 设置初始化状态
         *
         * @param initialized 初始化状态
         */
        public void setInitialized(boolean initialized) {
            this.initialized = initialized;
        }

        /**
         * 是否已使用
         *
         * @return 是否已使用
         */
        public boolean isUsed() {
            return used;
        }

        /**
         * 设置使用状态
         *
         * @param used 使用状态
         */
        public void setUsed(boolean used) {
            this.used = used;
        }
    }

    /**
     * 构造函数
     *
     * @param outInfos 输出信息接口
     */
    public SemanticAnalyzer(OutInfo outInfos) {
        this.outInfos = outInfos;
        this.scopeStack = new Stack<>();
        this.functionSymbols = new HashMap<>();
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();

        // 初始化全局作用域
        pushScope("全局");
    }

    /**
     * 添加新的作用域到作用域栈
     *
     * @param name 作用域名称
     */
    private void pushScope(String name) {
        Scope parent = scopeStack.isEmpty() ? null : scopeStack.peek();
        Scope newScope = new Scope(name, parent);
        scopeStack.push(newScope);
    }

    /**
     * 离开当前作用域
     */
    private void popScope() {
        if (!scopeStack.isEmpty()) {
            scopeStack.pop();
        }
    }

    /**
     * 获取当前作用域
     *
     * @return 当前作用域
     */
    private Scope currentScope() {
        return scopeStack.isEmpty() ? null : scopeStack.peek();
    }

    /**
     * 进行语义分析
     *
     * @param root 语法树根节点
     */
    public void analyze(TokenTreeView root) {
        if (root == null) {
            error("语法树为空，无法进行语义分析，请先进行语法分析");
            return;
        }

        info("开始语义分析...");
        try {
            // 从语法树根节点开始分析
            analyzeNode(root);

            // 检查是否有未使用的变量
            checkUnusedVariables();

            // 输出分析结果
            if (errors.isEmpty() && warnings.isEmpty()) {
                info("语义分析完成，未发现问题");
            } else {
                if (!errors.isEmpty()) {
                    info("语义分析完成，发现 " + errors.size() + " 个错误");
                }
                if (!warnings.isEmpty()) {
                    info("语义分析完成，发现 " + warnings.size() + " 个警告");
                }
            }
        } catch (Exception e) {
            error("语义分析过程中出现异常", e);
        }
    }

    /**
     * 分析节点及其子节点
     *
     * @param node 当前节点
     * @return 节点的类型信息
     */
    private TypeInfo analyzeNode(TokenTreeView node) {
        if (node == null) return TypeInfo.UNKNOWN;

        String nodeType = node.getNodeType();
        if (nodeType == null) return TypeInfo.UNKNOWN;

        switch (nodeType) {
            case "PROGRAM":
                return analyzeProgram(node);
            case "FUNCTION":
                return analyzeFunction(node);
            case "DECLARATION":
                return analyzeDeclaration(node);
            case "STATEMENT":
                return analyzeStatement(node);
            case "EXPRESSION":
                return analyzeExpression(node);
            case "BLOCK":
                return analyzeBlock(node);
            case "IDENTIFIER":
                return analyzeIdentifier(node);
            case "VALUE":
                return analyzeValue(node);
            default:
                // 对于其他类型节点，分析其子节点
                for (TokenTreeView child : node.getChildren()) {
                    analyzeNode(child);
                }
                return TypeInfo.UNKNOWN;
        }
    }

    /**
     * 分析程序节点
     *
     * @param node 程序节点
     * @return 节点的类型信息
     */
    private TypeInfo analyzeProgram(TokenTreeView node) {
        // 先分析全局声明
        for (TokenTreeView child : node.getChildren()) {
            if ("DECLARATION".equals(child.getNodeType())) {
                analyzeNode(child);
            }
        }

        // 再分析函数
        for (TokenTreeView child : node.getChildren()) {
            if ("FUNCTION".equals(child.getNodeType())) {
                analyzeNode(child);
            }
        }

        return TypeInfo.VOID;
    }

    /**
     * 分析函数节点
     *
     * @param node 函数节点
     * @return 节点的类型信息
     */
    private TypeInfo analyzeFunction(TokenTreeView node) {
        inFunction = true;
        // 创建函数作用域
        pushScope("函数");

        TypeInfo returnType = TypeInfo.VOID; // 默认返回类型
        String functionName = "main"; // 默认函数名

        // 解析返回类型和函数名
        for (TokenTreeView child : node.getChildren()) {
            if ("TYPE".equals(child.getNodeType())) {
                String typeName = child.getValue();
                returnType = getTypeFromString(typeName);
            } else if ("IDENTIFIER".equals(child.getNodeType())) {
                functionName = child.getValue();
            }
        }

        // 创建函数符号并添加到函数符号表
        FunctionSymbol funcSymbol = new FunctionSymbol(functionName, returnType);
        functionSymbols.put(functionName, funcSymbol);

        // 分析函数体
        for (TokenTreeView child : node.getChildren()) {
            if ("BLOCK".equals(child.getNodeType())) {
                analyzeNode(child);
                break;
            }
        }

        // 离开函数作用域
        popScope();
        inFunction = false;
        return returnType;
    }

    /**
     * 分析声明节点
     *
     * @param node 声明节点
     * @return 节点的类型信息
     */
    private TypeInfo analyzeDeclaration(TokenTreeView node) {
        boolean isConst = false;
        TypeInfo type = TypeInfo.UNKNOWN;
        String varName = null;
        boolean hasInitializer = false;
        TypeInfo initializerType = TypeInfo.UNKNOWN;

        // 检查是否是常量声明
        if (node.getValue().startsWith("常量")) {
            isConst = true;
        }

        // 分析声明的各个部分
        for (TokenTreeView child : node.getChildren()) {
            if ("KEYWORD".equals(child.getNodeType()) && "const".equals(child.getValue())) {
                isConst = true;
            } else if ("TYPE".equals(child.getNodeType())) {
                type = getTypeFromString(child.getValue());
            } else if ("IDENTIFIER".equals(child.getNodeType())) {
                varName = child.getValue();
            } else if ("OPERATOR".equals(child.getNodeType()) && "=".equals(child.getValue())) {
                hasInitializer = true;
            } else if ("EXPRESSION".equals(child.getNodeType()) ||
                     "VALUE".equals(child.getNodeType()) ||
                     "IDENTIFIER".equals(child.getNodeType())) {
                initializerType = analyzeNode(child);
            }
        }

        // 如果标识符已经找到
        if (varName != null) {
            // 检查变量是否已声明
            if (isDeclared(varName)) {
                error("变量 '" + varName + "' 重复声明");
            } else {
                // 如果是常量，必须有初始值
                if (isConst && !hasInitializer) {
                    error("常量 '" + varName + "' 必须赋初值");
                }

                // 如果有初始值，检查类型是否匹配
                if (hasInitializer && !isTypeCompatible(type, initializerType)) {
                    error("变量 '" + varName + "' 初始化类型不匹配: 期望 " + type + ", 得到 " + initializerType);
                }

                // 添加到符号表
                Symbol symbol = new Symbol(varName, type, isConst);
                symbol.setInitialized(hasInitializer);
                symbol.setUsed(false);

                // 将符号添加到当前作用域
                currentScope().addSymbol(symbol);
            }
        }

        return type;
    }

    /**
     * 分析语句节点
     *
     * @param node 语句节点
     * @return 节点的类型信息
     */
    private TypeInfo analyzeStatement(TokenTreeView node) {
        if ("赋值语句".equals(node.getValue())) {
            return analyzeAssignment(node);
        } else if ("条件语句".equals(node.getValue())) {
            return analyzeIfStatement(node);
        } else {
            // 处理其他语句类型，递归分析子节点
            for (TokenTreeView child : node.getChildren()) {
                analyzeNode(child);
            }
            return TypeInfo.VOID;
        }
    }

    /**
     * 分析赋值语句
     *
     * @param node 赋值语句节点
     * @return 节点的类型信息
     */
    private TypeInfo analyzeAssignment(TokenTreeView node) {
        String varName = null;
        TypeInfo exprType = TypeInfo.UNKNOWN;

        // 找出被赋值的变量和表达式
        for (TokenTreeView child : node.getChildren()) {
            if ("IDENTIFIER".equals(child.getNodeType())) {
                varName = child.getValue();
            } else if ("EXPRESSION".equals(child.getNodeType()) ||
                     "VALUE".equals(child.getNodeType()) ||
                     "IDENTIFIER".equals(child.getNodeType())) {
                exprType = analyzeNode(child);
            }
        }

        // 如果标识符已经找到
        if (varName != null) {
            // 检查变量是否已声明
            Symbol symbol = getSymbol(varName);
            if (symbol == null) {
                error("变量 '" + varName + "' 未声明就使用");
            } else {
                // 检查是否是常量
                if (symbol.isConstant()) {
                    error("常量 '" + varName + "' 不能被赋值");
                }

                // 检查类型是否匹配
                if (!isTypeCompatible(symbol.getType(), exprType)) {
                    error("赋值类型不匹配: 变量 '" + varName + "' 的类型是 " + symbol.getType() + ", 但赋值表达式的类型是 " + exprType);
                }

                // 标记变量已初始化和使用
                symbol.setInitialized(true);
                symbol.setUsed(true);
            }
        }

        return exprType;
    }

    /**
     * 分析条件语句
     *
     * @param node 条件语句节点
     * @return 节点的类型信息
     */
    private TypeInfo analyzeIfStatement(TokenTreeView node) {
        // 找到条件表达式
        for (TokenTreeView child : node.getChildren()) {
            if ("EXPRESSION".equals(child.getNodeType())) {
                TypeInfo conditionType = analyzeNode(child);
                // 检查条件表达式是否是布尔类型
                if (conditionType != TypeInfo.BOOL && conditionType != TypeInfo.UNKNOWN) {
                    error("if语句的条件表达式必须是布尔类型, 得到 " + conditionType);
                }
                break;
            }
        }

        // 分析if和else的语句块
        for (TokenTreeView child : node.getChildren()) {
            if ("STATEMENT".equals(child.getNodeType()) || "BLOCK".equals(child.getNodeType())) {
                analyzeNode(child);
            }
        }

        return TypeInfo.VOID;
    }

    /**
     * 分析块节点
     *
     * @param node 块节点
     * @return 节点的类型信息
     */
    private TypeInfo analyzeBlock(TokenTreeView node) {
        for (TokenTreeView child : node.getChildren()) {
            analyzeNode(child);
        }
        return TypeInfo.VOID;
    }

    /**
     * 分析表达式节点
     *
     * @param node 表达式节点
     * @return 节点的类型信息
     */
    private TypeInfo analyzeExpression(TokenTreeView node) {
        // 检查是否为独立表达式语句（无副作用警告）
        if (node.getParent() != null && "PROGRAM".equals(node.getParent().getNodeType()) ||
            (node.getParent() != null && "BLOCK".equals(node.getParent().getNodeType()))) {
            // 仅在表达式语句层面检查
            if (!hasEffect(node)) {
                int lineInfo = -1;
                int colInfo = -1;

                // 尝试从子节点获取位置信息
                for (TokenTreeView child : node.getChildren()) {
                    if (child.getDescription() != null && child.getDescription().contains("第")) {
                        String[] parts = child.getDescription().split("第");
                        if (parts.length > 2) {
                            try {
                                String linePart = parts[1];
                                String colPart = parts[2];
                                lineInfo = Integer.parseInt(linePart.substring(0, linePart.indexOf("行")));
                                colInfo = Integer.parseInt(colPart.substring(0, colPart.indexOf("列")));
                            } catch (Exception e) {
                                // 解析失败，继续使用默认值
                            }
                        }
                    }
                }

                String posInfo = lineInfo > 0 ? String.format("[r: %d, c: %d]", lineInfo, colInfo) : "";
                warn(posInfo + "表达式没有任何副作用（仅计算但未使用结果）");
            }
        }

        String exprType = node.getValue();
        List<TypeInfo> operandTypes = new ArrayList<>();
        String operator = null;

        for (TokenTreeView child : node.getChildren()) {
            if ("OPERATOR".equals(child.getNodeType())) {
                operator = child.getValue();
            } else if (!"SYMBOL".equals(child.getNodeType())) {
                // 收集非符号节点的类型
                operandTypes.add(analyzeNode(child));
            }
        }

        // 表达式类型推导
        if (exprType.contains("逻辑") || (operator != null && (operator.equals("&&") || operator.equals("||")))) {
            // 逻辑表达式
            for (TypeInfo type : operandTypes) {
                if (type != TypeInfo.BOOL && type != TypeInfo.UNKNOWN) {
                    error("逻辑表达式的操作数必须是布尔类型, 得到 " + type);
                }
            }
            return TypeInfo.BOOL;
        } else if (exprType.contains("相等性") || (operator != null && (operator.equals("==") || operator.equals("!=")))) {
            // 相等性表达式
            if (operandTypes.size() >= 2) {
                if (!isTypeCompatible(operandTypes.get(0), operandTypes.get(1))) {
                    error("相等性比较的操作数类型不兼容: " + operandTypes.get(0) + " 和 " + operandTypes.get(1));
                }
            }
            return TypeInfo.BOOL;
        } else if (exprType.contains("关系") || (operator != null && (operator.equals("<") || operator.equals(">") ||
                                               operator.equals("<=") || operator.equals(">=")))) {
            // 关系表达式
            if (operandTypes.size() >= 2) {
                if (!isNumericType(operandTypes.get(0)) || !isNumericType(operandTypes.get(1))) {
                    error("关系比较的操作数必须是数值类型");
                }
            }
            return TypeInfo.BOOL;
        } else if (exprType.contains("加减") || exprType.contains("乘除") ||
                 (operator != null && (operator.equals("+") || operator.equals("-") ||
                                     operator.equals("*") || operator.equals("/") || operator.equals("%")))) {
            // 算术表达式
            for (TypeInfo type : operandTypes) {
                if (!isNumericType(type) && type != TypeInfo.UNKNOWN) {
                    error("算术表达式的操作数必须是数值类型, 得到 " + type);
                }
            }

            // 类型升级规则: 如果有任何一个操作数是float，结果就是float，否则是int
            boolean hasFloat = false;
            for (TypeInfo type : operandTypes) {
                if (type == TypeInfo.FLOAT) {
                    hasFloat = true;
                    break;
                }
            }
            return hasFloat ? TypeInfo.FLOAT : TypeInfo.INT;
        } else if (exprType.contains("一元") && operator != null && (operator.equals("+") || operator.equals("-"))) {
            // 一元正负号表达式
            if (!operandTypes.isEmpty() && !isNumericType(operandTypes.get(0))) {
                error("一元" + operator + "操作符的操作数必须是数值类型, 得到 " + operandTypes.get(0));
            }
            // 保持操作数的类型
            return !operandTypes.isEmpty() ? operandTypes.get(0) : TypeInfo.INT;
        } else if (exprType.contains("括号")) {
            // 括号表达式，直接返回内部表达式的类型
            return !operandTypes.isEmpty() ? operandTypes.get(0) : TypeInfo.UNKNOWN;
        }

        // 默认遍历所有子节点
        for (TokenTreeView child : node.getChildren()) {
            analyzeNode(child);
        }

        // 如果无法确定类型，返回未知类型
        return TypeInfo.UNKNOWN;
    }

    /**
     * 分析标识符节点
     *
     * @param node 标识符节点
     * @return 节点的类型信息
     */
    private TypeInfo analyzeIdentifier(TokenTreeView node) {
        String varName = node.getValue();
        Symbol symbol = getSymbol(varName);

        if (symbol == null) {
            // 只有在子节点不包含自己的情况下报错（避免重复报错）
            boolean isSelf = false;
            for (TokenTreeView child : node.getChildren()) {
                if (child.getValue().equals(varName)) {
                    isSelf = true;
                    break;
                }
            }

            if (!isSelf) {
                error("变量 '" + varName + "' 未声明就使用");
            }
            return TypeInfo.UNKNOWN;
        } else {
            // 标记变量已使用
            symbol.setUsed(true);

            // 检查变量是否初始化
            if (!symbol.isInitialized()) {
                error("变量 '" + varName + "' 在初始化前被使用");
            }

            return symbol.getType();
        }
    }

    /**
     * 分析值节点
     *
     * @param node 值节点
     * @return 节点的类型信息
     */
    private TypeInfo analyzeValue(TokenTreeView node) {
        String value = node.getValue();
        if (value == null) return TypeInfo.UNKNOWN;

        // 检查子节点中的具体值
        for (TokenTreeView child : node.getChildren()) {
            String childValue = child.getValue();
            if (childValue != null) {
                if (childValue.equals("true") || childValue.equals("false") ||
                    childValue.equals("True") || childValue.equals("False")) {
                    return TypeInfo.BOOL;
                } else if (childValue.contains(".")) {
                    return TypeInfo.FLOAT;
                } else if (childValue.matches("\\d+")) {
                    return TypeInfo.INT;
                }
            }
        }

        // 尝试从当前节点的值判断类型
        if (value.equals("true") || value.equals("false") ||
            value.equals("True") || value.equals("False")) {
            return TypeInfo.BOOL;
        } else if (value.contains(".")) {
            return TypeInfo.FLOAT;
        } else if (value.matches("\\d+")) {
            return TypeInfo.INT;
        }

        return TypeInfo.UNKNOWN;
    }

    /**
     * 检查表达式是否有副作用（用于警告无副作用的表达式语句）
     *
     * @param node 表达式节点
     * @return 是否有副作用
     */
    private boolean hasEffect(TokenTreeView node) {
        if (node == null) return false;

        // 赋值表达式有副作用
        if (node.getValue() != null && node.getValue().contains("赋值")) {
            return true;
        }

        // 检查是否包含赋值运算符
        for (TokenTreeView child : node.getChildren()) {
            if ("OPERATOR".equals(child.getNodeType())) {
                String op = child.getValue();
                if (op.equals("=") || op.equals("+=") || op.equals("-=") ||
                    op.equals("*=") || op.equals("/=") || op.equals("%=") ||
                    op.equals("++") || op.equals("--")) {
                    return true;
                }
            }

            // 递归检查子节点
            if (hasEffect(child)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查是否有未使用的变量
     */
    private void checkUnusedVariables() {
        // 遍历作用域栈，检查所有作用域中的未使用变量
        for (Scope scope : scopeStack) {
            for (Map.Entry<String, Symbol> entry : scope.getSymbols().entrySet()) {
                Symbol symbol = entry.getValue();
                if (!symbol.isUsed()) {
                    String scopePrefix = scope.getName().equals("全局") ? "全局" : "局部";
                    warn(scopePrefix + "变量 '" + entry.getKey() + "' 已声明但从未使用");
                }
            }
        }
    }

    /**
     * 检查变量是否已声明
     *
     * @param name 变量名
     * @return 是否已声明
     */
    private boolean isDeclared(String name) {
        return getSymbol(name) != null;
    }

    /**
     * 获取变量符号，从当前作用域开始查找，然后逐级向上
     *
     * @param name 变量名
     * @return 变量符号，如果不存在返回null
     */
    private Symbol getSymbol(String name) {
        // 从当前作用域开始查找
        Scope current = currentScope();
        while (current != null) {
            Symbol symbol = current.getSymbol(name);
            if (symbol != null) {
                return symbol;
            }
            // 向上一级作用域查找
            current = current.getParent();
        }

        // 查找是否是函数
        if (functionSymbols.containsKey(name)) {
            return functionSymbols.get(name);
        }

        return null;
    }

    /**
     * 从字符串获取类型信息
     *
     * @param typeStr 类型字符串
     * @return 类型信息
     */
    private TypeInfo getTypeFromString(String typeStr) {
        switch (typeStr) {
            case "int": return TypeInfo.INT;
            case "float": return TypeInfo.FLOAT;
            case "bool": return TypeInfo.BOOL;
            case "void": return TypeInfo.VOID;
            default: return TypeInfo.UNKNOWN;
        }
    }

    /**
     * 检查类型是否兼容
     *
     * @param target 目标类型
     * @param source 源类型
     * @return 是否兼容
     */
    private boolean isTypeCompatible(TypeInfo target, TypeInfo source) {
        if (target == TypeInfo.UNKNOWN || source == TypeInfo.UNKNOWN) return true;
        if (target == source) return true;

        // int可以赋值给float
        if (target == TypeInfo.FLOAT && source == TypeInfo.INT) return true;

        return false;
    }

    /**
     * 检查类型是否是数值类型
     *
     * @param type 类型
     * @return 是否是数值类型
     */
    private boolean isNumericType(TypeInfo type) {
        return type == TypeInfo.INT || type == TypeInfo.FLOAT;
    }

    /**
     * 输出错误信息
     *
     * @param message 错误信息
     */
    private void error(String message) {
        errors.add(message);
        outInfos.error(src, message);
    }

    /**
     * 输出错误信息
     *
     * @param message 错误信息
     * @param e 异常
     */
    private void error(String message, Exception e) {
        errors.add(message);
        outInfos.error(src, message, e);
    }

    /**
     * 输出警告信息
     *
     * @param message 警告信息
     */
    private void warn(String message) {
        warnings.add(message);
        outInfos.warn(src, message);
    }

    /**
     * 输出信息
     *
     * @param message 信息
     */
    private void info(String message) {
        outInfos.info(src, message);
    }

    /**
     * 获取错误信息列表
     *
     * @return 错误信息列表
     */
    public List<String> getErrors() {
        return errors;
    }

    /**
     * 获取警告信息列表
     *
     * @return 警告信息列表
     */
    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * 获取符号表数据，用于UI显示
     * 
     * @return 符号表数据列表
     */
    public List<SymbolTableEntry> getSymbolTableEntries() {
        List<SymbolTableEntry> entries = new ArrayList<>();
        
        // 遍历所有作用域和符号
        Stack<Scope> tempStack = new Stack<>();
        tempStack.addAll(scopeStack);
        
        while (!tempStack.isEmpty()) {
            Scope scope = tempStack.pop();
            for (Map.Entry<String, Symbol> entry : scope.getSymbols().entrySet()) {
                Symbol symbol = entry.getValue();
                String typeStr = symbol.getType().toString();
                String info = symbol.isConstant() ? "常量" : "变量";
                info += symbol.isInitialized() ? "，已初始化" : "，未初始化";
                info += symbol.isUsed() ? "，已使用" : "，未使用";
                
                // 创建符号表条目
                entries.add(new SymbolTableEntry(
                    symbol.getName(),
                    typeStr,
                    scope.getName(),
                    0, // 行号信息需要在分析时记录
                    info
                ));
            }
            
            // 如果有父作用域，也加入遍历
            if (scope.getParent() != null) {
                tempStack.push(scope.getParent());
            }
        }
        
        return entries;
    }
    
    /**
     * 获取变量表数据，用于UI显示
     * 
     * @return 变量表数据列表
     */
    public List<VariableTableEntry> getVariableTableEntries() {
        List<VariableTableEntry> entries = new ArrayList<>();
        
        // 遍历所有作用域和符号
        Stack<Scope> tempStack = new Stack<>();
        tempStack.addAll(scopeStack);
        
        while (!tempStack.isEmpty()) {
            Scope scope = tempStack.pop();
            for (Map.Entry<String, Symbol> entry : scope.getSymbols().entrySet()) {
                Symbol symbol = entry.getValue();
                
                // 只处理非函数符号
                if (!(symbol instanceof FunctionSymbol)) {
                    String initialValue = symbol.isInitialized() ? "已初始化" : "未初始化";
                    
                    // 创建变量表条目
                    entries.add(new VariableTableEntry(
                        symbol.getName(),
                        symbol.getType().toString(),
                        scope.getName(),
                        initialValue,
                        symbol.isUsed()
                    ));
                }
            }
            
            // 如果有父作用域，也加入遍历
            if (scope.getParent() != null) {
                tempStack.push(scope.getParent());
            }
        }
        
        return entries;
    }
    
    /**
     * 获取函数表数据，用于UI显示
     * 
     * @return 函数表数据列表
     */
    public List<FunctionTableEntry> getFunctionTableEntries() {
        List<FunctionTableEntry> entries = new ArrayList<>();
        
        // 遍历函数符号表
        for (Map.Entry<String, FunctionSymbol> entry : functionSymbols.entrySet()) {
            FunctionSymbol function = entry.getValue();
            
            // 构建参数字符串
            StringBuilder paramsBuilder = new StringBuilder();
            List<Symbol> params = function.getParameters();
            for (int i = 0; i < params.size(); i++) {
                Symbol param = params.get(i);
                paramsBuilder.append(param.getType().toString())
                             .append(" ")
                             .append(param.getName());
                if (i < params.size() - 1) {
                    paramsBuilder.append(", ");
                }
            }
            String paramsStr = paramsBuilder.toString();
            if (paramsStr.isEmpty()) {
                paramsStr = "void";
            }
            
            // 创建函数表条目
            entries.add(new FunctionTableEntry(
                function.getName(),
                function.getReturnType().toString(),
                paramsStr,
                0, // 行号信息需要在分析时记录
                0  // 调用次数需要在分析时记录
            ));
        }
        
        return entries;
    }
}
