package cn.study.compilerclass.syntax;

import cn.study.compilerclass.model.ConstTableEntry;
import cn.study.compilerclass.model.FunctionTableEntry;
import cn.study.compilerclass.model.NodeType;
import cn.study.compilerclass.model.VariableTableEntry;
import cn.study.compilerclass.parser.TokenTreeView;
import cn.study.compilerclass.utils.OutInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * 语义分析器
 * <p>
 * 主要功能：<br/> 1. 符号表管理（变量定义、使用检查）<br/>2. 类型检查（表达式类型推导、类型匹配验证）<br/>3. 常量处理<br/>4. 标识冗余计算（无副作用表达式警告）<br/>5.
 * 作用域管理（嵌套作用域支持）<br/>6. 函数声明与调用分析
 */
@Getter
public class SemanticAnalyzer {

  private final OutInfo outInfos;
  private final String src = "语义分析";
  private final List<String> errors;
  private final List<String> warnings;

  private final List<VariableTableEntry> variableTable; // 变量表
  private final List<ConstTableEntry> constTable;     // 常量表
  private final List<FunctionTableEntry> functionTable; // 函数表

  private final Stack<Integer> scopeStack;            // 作用域栈
  // 用于跟踪变量使用情况
  private final Set<VariableTableEntry> usedVariables;
  private final Set<String> declaredVariablesInScope; // 用于在当前作用域内快速检查重复声明
  private int nextScopeId;                            // 下一个作用域的ID
  private boolean mainFunctionFound;                  // 是否找到主函数

  /**
   * 构造函数
   *
   * @param outInfos 输出信息接口
   */
  public SemanticAnalyzer(OutInfo outInfos) {
    this.outInfos = outInfos;
    this.errors = new ArrayList<>();
    this.warnings = new ArrayList<>();
    this.variableTable = new ArrayList<>();
    this.constTable = new ArrayList<>();
    this.functionTable = new ArrayList<>();
    this.scopeStack = new Stack<>();
    this.usedVariables = new HashSet<>();
    this.declaredVariablesInScope = new HashSet<>(); // 初始化
    this.nextScopeId = 0; // 初始化，全局作用域为0
    this.mainFunctionFound = false;
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
    // 初始化状态
    errors.clear();
    warnings.clear();
    variableTable.clear();
    constTable.clear();
    functionTable.clear();
    scopeStack.clear();
    usedVariables.clear();
    nextScopeId = 1; // 全局作用域为0，子作用域从1开始
    mainFunctionFound = false;

    scopeStack.push(0); // 进入全局作用域

    try {
      // 从语法树根节点开始分析
      analyzeProgram(root);

      if (!mainFunctionFound) {
        error("程序中没有主函数");
      }

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
    } finally {
      scopeStack.pop(); // 退出全局作用域
    }
  }

  // 分析整体程序结构
  private void analyzeProgram(TokenTreeView node) {
    for (TokenTreeView child : node.getChildren()) {
      NodeType nodeType = child.getNodeType();
      switch (nodeType) {
        case DEFINITION -> analyzeDefinition(child);
        // case DECLARATION -> analyzeDeclaration(child);
        case FUNCTION -> {
          if ("主函数".equals(child.getValue())) {
            if (mainFunctionFound) {
              error("程序中不能有多个主函数");
            }
            mainFunctionFound = true;
            analyzeMainFunction(child); // 主函数内部也可能有赋值语句
          } else {
            warn("发现非主函数定义：" + child.getValue() + "，暂不处理函数声明。");
          }
        }
      }
    }
  }

  // 分析变量定义和常量定义
  private void analyzeDefinition(TokenTreeView definitionNode) {
    ArrayList<TokenTreeView> children = definitionNode.getChildren();
    // 判断是否为常量定义
    boolean isConst = "const".equals(children.getFirst().getValue());
    int typeNodeIndex = isConst ? 1 : 0; // 常量定义从索引1开始，变量定义从索引0开始

    TokenTreeView typeNode = children.get(typeNodeIndex);
    String commonType = typeNode.getValue(); // 类型节点

    // 从类型节点之后开始遍历每个定义
    for (int i = typeNodeIndex + 1; i < children.size(); i++) {
      TokenTreeView varNode = children.get(i);
      ArrayList<TokenTreeView> nodes = varNode.getChildren();
      String name = nodes.getFirst().getValue(); // 变量名
      // 检查是否重复定义
      if (isConst) {
        if (findConst(name, getCurrentScopePath()) != null) {
          error("常量 '" + name + "' 重复定义");
        }
      } else {
        if (findVariable(name, getCurrentScopePath()) != null) {
          error("变量 '" + name + "' 重复定义");
        }
      }

      String value = "未初始化";
      if (isConst || varNode.getDescription().equals("init")) {
        TokenTreeView valueNode = varNode.getChildren().getLast();
        if (valueNode.getNodeType() == NodeType.VALUE) {
          value = valueNode.getChildren().getFirst().getValue(); // 直接取值
        } else if (valueNode.getNodeType() == NodeType.EXPRESSION) {
          value = "表达式";
        }
      }

      if (isConst) {
        constTable.add(new ConstTableEntry(name, commonType, value, getCurrentScopePath()));
        // info("常量 '" + name + "' 定义成功，类型为 '" + commonType + "'，值为 '" + value + "'");
      } else {
        variableTable.add(new VariableTableEntry(name, commonType, getCurrentScopePath(), value));
        // info("变量 '" + name + "' 定义成功，类型为 '" + commonType + "'，初始值为 '" + value + "'");
      }
    }
  }

  // 分析主函数
  private void analyzeMainFunction(TokenTreeView functionNode) {
    // info("开始分析主函数...");
    enterScope();
    // 遍历主函数体内的语句
    if (functionNode.getChildren()!= null) {
      for (TokenTreeView statementNode : functionNode.getChildren()) {
        analyzeStatement(statementNode); // 调用通用的语句分析方法
      }
    }
    exitScope();
    // info("主函数分析结束。");
  }

  // 通用语句分析方法，可以被函数体、代码块等调用
  private void analyzeStatement(TokenTreeView statementNode) {
    switch (statementNode.getNodeType()) {
      case DEFINITION -> analyzeDefinition(statementNode);
      case ASSIGNMENT_STMT -> analyzeAssignmentStatement(statementNode);
      // case IF_STMT -> analyzeIfStatement(statementNode);
      // case WHILE_STMT -> analyzeWhileStatement(statementNode);
      default ->
          warn("在语句级别发现未处理的节点类型: " + statementNode.getNodeType() + " (Value: " + statementNode.getValue() + ")");
    }
  }

  // 分析赋值语句
  private void analyzeAssignmentStatement(TokenTreeView assignmentNode) {
    if (assignmentNode.getChildren().size() < 3) {
      error("赋值语句 \"" + assignmentNode.getValue() + "\" 结构不完整，至少需要左操作数、赋值操作符和右操作数。");
      return;
    }

    TokenTreeView leftOperandNode = assignmentNode.getChildren().getFirst();
    TokenTreeView rightOperandNode = assignmentNode.getChildren().getLast();

    String variableName = leftOperandNode.getValue();
    if (leftOperandNode.getNodeType() != NodeType.IDENTIFIER) {
      error("赋值语句左侧必须是标识符，实际为: " + leftOperandNode.getNodeType());
      return;
    }

    String currentScopePath = getCurrentScopePath(); // 获取当前作用域用于查找

    // 1. 检查左侧是否为常量
    ConstTableEntry constEntry = findConst(variableName, currentScopePath);
    if (constEntry != null) {
      error("不能给常量 '" + variableName + "' 赋值");
      return; // 常量不能被赋值
    }

    // 2. 检查变量是否已声明 (先声明后使用)
    VariableTableEntry varEntry = findVariable(variableName, currentScopePath);
    if (varEntry == null) {
      error("变量 '" + variableName + "' 在赋值前未声明");
      return;
    }

    // 标记变量为已使用
    usedVariables.add(varEntry);
    info("变量 '" + variableName + "' 在作用域 '" + varEntry.getScope() + "' 中被赋值 (使用)");

    // 3. (后续) 分析右侧表达式并进行类型检查
    // String rightValueType = analyzeExpression(rightOperandNode);
    // if (!varEntry.getType().equals(rightValueType)) {
    //   error("类型不匹配：无法将类型 '" + rightValueType + "' 赋值给类型为 '" + varEntry.getType() + "' 的变量 '" + variableName + "'");
    // }
    // 暂时只记录赋值操作
    // varEntry.setValue(analyzeExpression(rightOperandNode)); // 假设 analyzeExpression 返回值的字符串形式

    // 简单地将右操作数的值（如果是字面量）或标识符记录下来，实际中需要表达式求值
    String assignedValue = rightOperandNode.getValue(); // 这只是一个占位符，实际需要更复杂的表达式分析
    if (rightOperandNode.getNodeType() == NodeType.IDENTIFIER) {
      // 如果右边也是一个变量，也需要检查它是否已声明，并标记为使用
      VariableTableEntry rightVarEntry = findVariable(assignedValue, currentScopePath);
      if (rightVarEntry == null) {
        error("赋值语句右侧变量 '" + assignedValue + "' 未声明");
      } else {
        usedVariables.add(rightVarEntry);
        info("变量 '" + assignedValue + "' 在作用域 '" + rightVarEntry.getScope() + "' 中作为右值被使用");
      }
    }
    // 更新变量表中的值 (可选，取决于是否追踪运行时值)
    // varEntry.setValue(assignedValue); // VariableTableEntry 没有直接的 setValue 方法，需要修改或通过属性
    info("变量 '" + variableName + "' 被赋予新值 (右侧: " + assignedValue + ")");
  }

  // 作用域管理方法
  private void enterScope() {
    scopeStack.push(nextScopeId);
    declaredVariablesInScope.clear(); // 进入新作用域时，清空当前作用域的声明记录
    info("进入作用域: " + getCurrentScopePath() + " (ID: " + nextScopeId + ")");
    nextScopeId++;
  }

  private void exitScope() {
    if (!scopeStack.isEmpty()) {
      Integer exitedScopeId = scopeStack.pop();
      declaredVariablesInScope.clear(); // 退出作用域时，清空，尽管通常在enter时处理
      info("退出作用域: " + getCurrentScopePath() + " (原ID: " + exitedScopeId + ")");
    } else {
      error("尝试退出作用域失败：作用域栈为空。");
    }
  }

  private String getCurrentScopePath() {
    if (scopeStack.isEmpty()) {
      return "/"; // 或者抛出错误，表示不应在无作用域时调用
    }
    // 如果是为新声明获取路径，并且栈顶不是0（全局），则使用栈顶ID
    // 否则，使用栈内所有ID连接的路径
    // 对于查找，总是使用完整路径
    // 对于声明，通常是在当前最内层作用域
    return scopeStack.stream().map(String::valueOf).collect(Collectors.joining("/")) + "/";
  }

  // 检查变量或常量是否已在当前作用域或父作用域声明
  private boolean isVariableAlreadyDeclared(String name, String scopePath, boolean isConstant) {
    // 检查当前作用域是否有同名变量/常量
    // 简化：暂时只检查当前作用域，后续可以扩展到检查父作用域以支持隐藏
    // 但通常不允许在同一作用域重复定义

    String currentScopeOnly = scopeStack.peek() + "/"; // 仅当前最内层作用域
    if (scopeStack.peek() == 0) { // 全局作用域特殊处理
      currentScopeOnly = "0/";
    }

    if (isConstant) {
      for (ConstTableEntry entry : constTable) {
        if (entry.getName().equals(name) && entry.getScope().startsWith(currentScopeOnly)) {
          return true;
        }
      }
    } else {
      for (VariableTableEntry entry : variableTable) {
        if (entry.getName().equals(name) && entry.getScope().startsWith(currentScopeOnly)) {
          return true;
        }
      }
    }
    // 向上查找，直到全局作用域
    Stack<Integer> tempStack = (Stack<Integer>) scopeStack.clone();
    while (!tempStack.isEmpty()) {
      String pathToCheck = tempStack.stream().map(String::valueOf).collect(Collectors.joining("/")) + "/";
      if (isConstant) {
        for (ConstTableEntry entry : constTable) {
          if (entry.getName().equals(name) && entry.getScope().equals(pathToCheck)) {
            return true; // 在父作用域找到同名常量
          }
        }
      } else {
        for (VariableTableEntry entry : variableTable) {
          if (entry.getName().equals(name) && entry.getScope().equals(pathToCheck)) {
            return true; // 在父作用域找到同名变量
          }
        }
      }
      tempStack.pop();
    }

    return false;
  }

  // 辅助方法：查找变量 (考虑作用域)
  private VariableTableEntry findVariable(String name, String currentScopePath) {
    // 从当前作用域向上查找
    Stack<Integer> tempScopeStack = (Stack<Integer>) scopeStack.clone();
    while (!tempScopeStack.isEmpty()) {
      String pathToCheck = tempScopeStack.stream().map(String::valueOf).collect(Collectors.joining("/")) + "/";
      for (VariableTableEntry entry : variableTable) {
        if (entry.getName().equals(name) && entry.getScope().equals(pathToCheck)) {
          return entry;
        }
      }
      tempScopeStack.pop();
      if (!tempScopeStack.isEmpty()) { // 避免移除最后一个元素后还尝试生成路径
        // No need to do anything here, pathToCheck will be shorter in next iteration
      } else if (scopeStack.size() > 1) { // 如果栈空了，但原始栈不止全局，则最后检查全局
        pathToCheck = "0/";
        for (VariableTableEntry entry : variableTable) {
          if (entry.getName().equals(name) && entry.getScope().equals(pathToCheck)) {
            return entry;
          }
        }
      }
    }
    return null; // 未找到
  }

  // 辅助方法：查找常量 (考虑作用域)
  private ConstTableEntry findConst(String name, String currentScopePath) {
    // 从当前作用域向上查找
    Stack<Integer> tempScopeStack = (Stack<Integer>) scopeStack.clone();
    while (!tempScopeStack.isEmpty()) {
      String pathToCheck = tempScopeStack.stream().map(String::valueOf).collect(Collectors.joining("/")) + "/";
      for (ConstTableEntry entry : constTable) {
        if (entry.getName().equals(name) && entry.getScope().equals(pathToCheck)) {
          return entry;
        }
      }
      tempScopeStack.pop();
      if (!tempScopeStack.isEmpty()) {
        // No need to do anything here, pathToCheck will be shorter in next iteration
      } else if (scopeStack.size() > 1) { // 如果栈空了，但原始栈不止全局，则最后检查全局
        pathToCheck = "0/";
        for (ConstTableEntry entry : constTable) {
          if (entry.getName().equals(name) && entry.getScope().equals(pathToCheck)) {
            return entry;
          }
        }
      }
    }
    return null; // 未找到
  }

  // 分析条件语句
  private void analyzeIfStatement(TokenTreeView conditionNode) {
    // info("分析条件语句: " + ifNode.getValue());
    ArrayList<TokenTreeView> children = conditionNode.getChildren();
    TokenTreeView ifNode = children.getFirst(); // EXPR

    TokenTreeView thenStatementNode = conditionNode.getChildren().get(4); // STMT_THEN
    info("分析IF语句的THEN分支");
    enterScope();
    analyzeStatement(thenStatementNode);
    exitScope();

    if (conditionNode.getChildren().size() > 5 && "else".equals(conditionNode.getChildren().get(5).getValue())) {
      if (conditionNode.getChildren().size() < 7) {
        error("IF语句的ELSE分支结构不完整: " + conditionNode.getValue());
        return;
      }
      TokenTreeView elseStatementNode = conditionNode.getChildren().get(6); // STMT_ELSE
      info("分析IF语句的ELSE分支");
      enterScope();
      analyzeStatement(elseStatementNode);
      exitScope();
    }
    // warn("analyzeIfStatement 尚未完全实现条件表达式分析");
  }

  private void analyzeWhileStatement(TokenTreeView whileNode) {
    info("分析WHILE语句: " + whileNode.getValue());
    // WHILE_STMT -> while ( EXPR ) STMT
    // 假设子节点结构: 0: 'while', 1: '(', 2: EXPR, 3: ')', 4: STMT_BODY

    if (whileNode.getChildren().size() < 5) {
      error("WHILE语句结构不完整: " + whileNode.getValue());
      return;
    }

    TokenTreeView conditionNode = whileNode.getChildren().get(2); // EXPR
    // analyzeExpression(conditionNode); // TODO: 实现表达式分析和类型检查，确保其为布尔类型
    info("WHILE 条件: " + conditionNode.getValue());

    TokenTreeView bodyStatementNode = whileNode.getChildren().get(4); // STMT_BODY
    info("分析WHILE语句的循环体");
    enterScope(); // 循环体有自己的作用域
    analyzeStatement(bodyStatementNode);
    exitScope();
    // warn("analyzeWhileStatement 尚未完全实现条件表达式分析");
  }

  private void analyzeFunctionDeclaration(TokenTreeView functionNode) {
    // 分析函数声明
    ArrayList<TokenTreeView> children = functionNode.getChildren();
    String functionName = children.get(1).getValue();
    String returnType = children.get(0).getValue();

    // 检查重复函数声明
    if (functionTable.stream().anyMatch(f -> f.getName().equals(functionName))) {
      error("函数 '" + functionName + "' 重复声明");
      return;
    }

    ArrayList<String> paramTypeList = new ArrayList<>();
    // 分析参数列表
    if (children.size() > 2) {
      for (TokenTreeView paramNode : children.get(2).getChildren()) {
        if (paramNode.getNodeType() == NodeType.PARAM) {
          String paramType = paramNode.getChildren().getFirst().getValue();
          paramTypeList.add(paramType);
        }
      }
    }
    FunctionTableEntry entry = new FunctionTableEntry(functionName, returnType, paramTypeList);
    functionTable.add(entry);
    info("声明函数: " + entry);
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
   * 输出信息
   *
   * @param message 信息
   */
  private void info(String message) {
    outInfos.info(src, message);
  }

  private void analyzeFunctionCall(TokenTreeView callNode) {
    String functionName = callNode.getChildren().getFirst().getValue();
    FunctionTableEntry function = functionTable.stream()
                                               .filter(f -> f.getName().equals(functionName))
                                               .findFirst()
                                               .orElse(null);

    if (function == null) {
      error("未声明的函数调用: " + functionName);
      return;
    }

    List<TokenTreeView> actualParams = callNode.getChildren().subList(1, callNode.getChildren().size());
    if (actualParams.size() != function.getParamCount()) {
      error("函数 '" + functionName + "' 参数数量不匹配，期望 " + function.getParamCount() + " 实际 " + actualParams.size());
    } else {
      // 参数类型检查
      for (int i = 0; i < actualParams.size(); i++) {
        String actualType = analyzeExpression(actualParams.get(i));
        String expectedType = function.getParamTypes().get(i);
        if (!actualType.equals(expectedType)) {
          error("参数类型不匹配，位置 " + (i + 1) + " 期望 " + expectedType + " 实际 " + actualType);
        }
      }
    }
  }

  private String analyzeExpression(TokenTreeView exprNode) {
    return "";
  }

  private void analyzeDoWhileLoop(TokenTreeView doWhileNode) {
    enterScope();
    // 分析循环体
    analyzeStatement(doWhileNode.getChildren().get(1));
    exitScope();

    // 分析条件表达式
    String conditionType = analyzeExpression(doWhileNode.getChildren().get(3));
    if (!"bool".equals(conditionType)) {
      error("do-while条件表达式必须为布尔类型，实际类型: " + conditionType);
    }
  }

  private String analyzeRelationalExpression(TokenTreeView exprNode) {
    String leftType = analyzeExpression(exprNode.getChildren().get(0));
    String rightType = analyzeExpression(exprNode.getChildren().get(2));

    if (!leftType.equals(rightType)) {
      error("关系表达式类型不匹配: " + leftType + " 和 " + rightType);
    }
    return "bool";
  }

  /**
   * 输出错误信息
   *
   * @param message 错误信息
   * @param e       异常
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

  // Getter 方法，供外部（如Controller）调用以显示符号表
  public List<VariableTableEntry> getVariableTableEntries() {
    return new ArrayList<>(variableTable); // 返回副本以防外部修改
  }

  public List<ConstTableEntry> getConstTableEntries() {
    return new ArrayList<>(constTable); // 返回副本以防外部修改
  }

  public List<FunctionTableEntry> getFunctionTableEntries() {
    return new ArrayList<>(functionTable);
  }
}
