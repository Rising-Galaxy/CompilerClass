package cn.study.compilerclass.syntax;

import cn.study.compilerclass.model.ConstTableEntry;
import cn.study.compilerclass.model.FunctionTableEntry;
import cn.study.compilerclass.model.MiddleTableEntry;
import cn.study.compilerclass.model.NodeType;
import cn.study.compilerclass.model.VariableTableEntry;
import cn.study.compilerclass.parser.TokenTreeView;
import cn.study.compilerclass.utils.OutInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

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
  private boolean hasError;

  public final ArrayList<VariableTableEntry> variableTable; // 变量表
  public final ArrayList<ConstTableEntry> constTable;     // 常量表
  public final ArrayList<FunctionTableEntry> functionTable; // 函数表
  public final ArrayList<MiddleTableEntry> middleTableList; // 四元式表

  private final Stack<Integer> scopeStack;            // 作用域栈
  // 用于跟踪变量使用情况
  private final Set<VariableTableEntry> usedVariables;
  // 用于跟踪常量使用情况
  private final Set<ConstTableEntry> usedConstants;
  private final Set<String> declaredVariablesInScope; // 用于在当前作用域内快速检查重复声明
  private int nextScopeId;                            // 下一个作用域的ID
  private boolean mainFunctionFound;                  // 是否找到主函数

  private final Result errorResult = new Result("", "error");

  // 用于记录中间代码序号
  private int midId = 0;
  // 用于记录临时变量序号
  private int tempId = 0;
  // 延迟执行的任务列表
  private final List<MiddleTableEntry> delayedTasks;

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
    this.middleTableList = new ArrayList<>();
    this.delayedTasks = new ArrayList<>();
    this.scopeStack = new Stack<>();
    this.usedVariables = new HashSet<>();
    this.usedConstants = new HashSet<>();
    this.declaredVariablesInScope = new HashSet<>(); // 初始化
    this.nextScopeId = 0; // 初始化，全局作用域为0
    this.mainFunctionFound = false;
    this.hasError = false;
  }

  public boolean hasError() {
    return hasError;
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
    addBuiltInFunctions(); // 添加内置函数

    scopeStack.push(0); // 进入全局作用域

    try {
      // 从语法树根节点开始分析
      analyzeProgram(root);

      if (!mainFunctionFound) {
        error("程序中没有主函数");
      }

      // 检查未使用的变量和常量
      checkUnusedVariables();

      // 输出分析结果
      if (errors.isEmpty() && warnings.isEmpty()) {
        hasError = false;
        info("语义分析完成，未发现问题");
      } else {
        if (!errors.isEmpty()) {
          hasError = true;
          info("语义分析完成，发现 " + errors.size() + " 个错误");
        }
        if (!warnings.isEmpty()) {
          hasError = false;
          info("语义分析完成，发现 " + warnings.size() + " 个警告");
        }
      }

      // 如果有错误，不展示四元式
      if (hasError) {
        middleTableList.clear();
        error("由于语义分析存在错误，四元式表不展示");
      }
    } catch (Exception e) {
      error("语义分析过程中出现异常", e);
    } finally {
      scopeStack.pop(); // 退出全局作用域
    }
  }

  // 添加内置函数
  private void addBuiltInFunctions() {
    // 添加内置函数定义
    functionTable.add(new FunctionTableEntry("input", "int", new ArrayList<>()));
    functionTable.add(new FunctionTableEntry("put", "void", new ArrayList<>(Collections.singleton("int"))));
    functionTable.add(new FunctionTableEntry("putc", "void", new ArrayList<>(Collections.singleton("char"))));
    functionTable.add(new FunctionTableEntry("putb", "void", new ArrayList<>(Collections.singleton("bool"))));
  }

  // 检查是否存在未使用的变量/常量
  public void checkUnusedVariables() {
    for (VariableTableEntry var : variableTable) {
      if (!usedVariables.contains(var)) {
        warn(String.format("[r: %d, c: %d]-变量 '%s' 从未被使用", var.getInitRow(), var.getInitCol(), var.getName()));
      }
    }
    for (ConstTableEntry constEntry : constTable) {
      if (!usedConstants.contains(constEntry)) {
        warn(String.format("[r: %d, c: %d]-常量 '%s' 从未被使用", constEntry.getInitRow(), constEntry.getInitCol(), constEntry.getName()));
      }
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
              error(String.format("[r: %d, c: %d]-程序中不能有多个主函数", child.getRow(), child.getCol()));
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
      SymbolType type = checkSymbolType(name, getCurrentScopePath());
      SymbolType expectedType = isConst ? SymbolType.CONST : SymbolType.VAR;
      if (type != SymbolType.NONE) {
        if (type != expectedType) {
          error(String.format("[r: %d, c: %d]-%s '%s' 已被声明为 %s", varNode.getRow(), varNode.getCol(), expectedType, name, type));
        } else {
          error(String.format("[r: %d, c: %d]-%s '%s' 重复定义", varNode.getRow(), varNode.getCol(), expectedType, name));
        }
      }

      String value = "null";
      if (isConst || varNode.getDescription().equals("init")) {
        TokenTreeView valueNode = varNode.getChildren().getLast();
        if (isValueNode(valueNode)) {
          // 如果是值节点，直接取值
          value = valueNode.getValue();
        } else if (isExpressionNode(valueNode)) {
          // 如果是常量，报错
          if (isConst) {
            error(String.format("[r: %d, c: %d]-常量 '%s' 定义不允许为表达式", valueNode.getRow(), valueNode.getCol(), name));
            continue; // 跳过当前常量定义
          }
          // 分析表达式并获取类型
          Result result = analyzeExpression(valueNode);
          if (!result.getType().equals(commonType)) {
            error(String.format("[r: %d, c: %d]-变量初始化表达式类型不匹配：期望 '%s'，实际为 '%s'", valueNode.getRow(), valueNode.getCol(), commonType, result.getType()));
          } else {
            // 生成中间代码
            emit("=", result.getValue(), "", name);
          }
        }
      }

      if (isConst) {
        constTable.add(new ConstTableEntry(name, commonType, value, varNode.getCol(), varNode.getRow()));
      } else {
        variableTable.add(new VariableTableEntry(name, commonType, getCurrentScopePath(), value, varNode.getCol(), varNode.getRow()));
      }
    }
  }

  // 分析主函数
  private void analyzeMainFunction(TokenTreeView functionNode) {
    emit("main", "", "", "");
    enterScope();
    // 遍历主函数体内的语句
    if (functionNode.getChildren() != null) {
      for (TokenTreeView statementNode : functionNode.getChildren()) {
        analyzeStatement(statementNode); // 调用通用的语句分析方法
      }
    }
    exitScope();
    emit("quit", "", "", "");
  }

  // 通用语句分析方法，可以被函数体、代码块等调用
  private void analyzeStatement(TokenTreeView statementNode) {
    switch (statementNode.getNodeType()) {
      case DEFINITION -> analyzeDefinition(statementNode);
      case ASSIGNMENT_STMT -> analyzeAssignmentStatement(statementNode);
      case FUNCTION_CALL -> analyzeFunctionCall(statementNode);
      case UNARY_EXPR -> {
        String value = statementNode.getValue();
        if (value.contains("后缀")) {
          analyzeSuffixStatement(statementNode);
        } else if (value.contains("前缀")) {
          analyzePrefixStatement(statementNode);
        } else {
          error(String.format("[r: %d, c: %d]-不是语句", statementNode.getRow(), statementNode.getCol()));
        }
      }
      case IF_STMT -> analyzeIfStatement(statementNode);
      // case WHILE_STMT -> analyzeWhileStatement(statementNode);
      default ->
          error(String.format("[r: %d, c: %d]-未识别的类型 {%s}, 可能不是语句", statementNode.getRow(), statementNode.getCol(), statementNode.getNodeType()));
    }
    processDelayedTasks();
  }

  // 分析条件语句
  private void analyzeIfStatement(TokenTreeView node) {
    ArrayList<TokenTreeView> children = node.getChildren();
    int finalOut = 0;
    for (int i = 0; i < children.size(); i++) {
      TokenTreeView child = children.get(i);
      ArrayList<TokenTreeView> condition = child.getChildren();
      if (condition.getFirst().getValue().equals("else")) {
        TokenTreeView conditionBody = condition.getLast();
        if (conditionBody.getNodeType() == NodeType.BLOCK) {
          ArrayList<TokenTreeView> bodys = conditionBody.getChildren();
          for (TokenTreeView body : bodys) {
            analyzeStatement(body);
          }
        } else {
          analyzeStatement(conditionBody);
        }
      } else {
        // 分析条件表达式
        Result conditionResult = analyzeExpression(condition.get(1));
        if (conditionResult.getType().equals("error")) {
          return;
        }
        // 出口
        int trueOut = emit("jnz", conditionResult.getValue(), "", "0");
        int falseOut = emit("jz", conditionResult.getValue(), "", "0");
        // 语句体
        TokenTreeView conditionBody = condition.getLast();
        if (conditionBody.getNodeType() == NodeType.BLOCK) {
          ArrayList<TokenTreeView> body = conditionBody.getChildren();
          // 该分支的真出口为该语句体下的第一个语句
          backSet(trueOut, midId);
          analyzeStatement(body.getFirst());
          for (int j = 1; j < body.size(); j++) {
            analyzeStatement(body.get(j));
          }
        } else {
          backSet(trueOut, midId);
          analyzeStatement(conditionBody);
        }
        if (i != children.size() - 1) {
          finalOut = emit("j", "", "", String.valueOf(finalOut));
        }
        // 假出口为该分支结束后的下一个语句
        backSet(falseOut, midId);
      }
    }
    // 回填全部的最终出口
    backPatch(finalOut, midId);
    processDelayedTasks();
  }

  // 分析函数调用
  private Result analyzeFunctionCall(TokenTreeView functionCallNode) {
    ArrayList<TokenTreeView> children = functionCallNode.getChildren();
    String functionName = children.getFirst().getValue();
    FunctionTableEntry functionEntry = findFunction(functionName);
    if (functionEntry == null) {
      error(String.format("[r: %d, c: %d]-函数 '%s' 未定义", functionCallNode.getRow(), functionCallNode.getCol(), functionName));
      return errorResult;
    }
    // 检查参数数量
    List<TokenTreeView> paramNodes = children.getLast().getChildren();
    if (paramNodes.size() != functionEntry.getParamCount()) {
      error(String.format("[r: %d, c: %d]-函数 '%s' 调用参数数量不匹配，期望 %d 个，实际 %d 个", functionCallNode.getRow(), functionCallNode.getCol(), functionName, functionEntry.getParamCount(), paramNodes.size()));
      return errorResult;
    }
    // 检查参数类型
    for (int i = 0; i < paramNodes.size(); i++) {
      TokenTreeView paramNode = paramNodes.get(i).getChildren().getFirst();
      Result param = analyzeExpression(paramNode);
      String expectedType = functionEntry.getParamTypes().get(i);
      if (!param.getType().equals(expectedType)) {
        error(String.format("[r: %d, c: %d]-函数 '%s' 参数类型不匹配，第 %d 个参数应为 '%s'，实际为 '%s'", functionCallNode.getRow(), functionCallNode.getCol(), functionName, i + 1, expectedType, param));
      }
      // 生成中间代码
      emit("para", param.getValue(), "", "");
    }
    // 生成中间代码
    String midVar = newTmp();
    emit("call", functionName, "", midVar);
    processDelayedTasks();
    return new Result(midVar, functionEntry.getReturnType());
  }

  // 分析赋值语句
  private void analyzeAssignmentStatement(TokenTreeView assignmentNode) {
    if (assignmentNode.getChildren().size() < 3) {
      error(String.format("[r: %d, c: %d]-赋值语句结构不完整", assignmentNode.getRow(), assignmentNode.getCol()));
      return;
    }

    TokenTreeView leftOperandNode = assignmentNode.getChildren().getFirst();
    TokenTreeView rightOperandNode = assignmentNode.getChildren().getLast();

    String variableName = leftOperandNode.getValue();
    if (leftOperandNode.getNodeType() != NodeType.IDENTIFIER) {
      error(String.format("[r: %d, c: %d]-赋值语句左侧必须是标识符", leftOperandNode.getRow(), leftOperandNode.getCol()));
      return;
    }

    String currentScopePath = getCurrentScopePath(); // 获取当前作用域用于查找

    // 检查左侧是否为常量
    ConstTableEntry constEntry = findConst(variableName);
    if (constEntry != null) {
      error(String.format("[r: %d, c: %d]-不能给常量 '%s' 赋值", rightOperandNode.getRow(), rightOperandNode.getCol(), variableName));
      return; // 常量不能被赋值
    }

    // 检查变量是否已声明 (先声明后使用)
    VariableTableEntry varEntry = findVariable(variableName, currentScopePath);
    if (varEntry == null) {
      error(String.format("[r: %d, c: %d]-变量 '%s' 在赋值前未声明", rightOperandNode.getRow(), rightOperandNode.getCol(), variableName));
      return;
    }

    // 标记变量/常量为已使用
    usedVariables.add(varEntry);

    // 分析右侧表达式并进行类型检查
    Result result = analyzeExpression(rightOperandNode);
    if (!varEntry.getType().equals(result.getType())) {
      error(String.format("[r: %d, c: %d]-类型不匹配：无法将类型 '%s' 赋值给类型为 '%s' 的变量 '%s'", rightOperandNode.getRow(), rightOperandNode.getCol(), result, varEntry.getType(), variableName));
    }

    // 生成四元式
    // 复合赋值语句
    String operator = assignmentNode.getChildren().get(1).getValue();
    String midVar = newTmp();
    switch (operator) {
      case "+=" -> {
        emit("+", varEntry.getName(), result.getValue(), midVar);
      }
      case "-=" -> {
        emit("-", varEntry.getName(), result.getValue(), midVar);
      }
      case "*=" -> {
        emit("*", varEntry.getName(), result.getValue(), midVar);
      }
      case "/=" -> {
        emit("/", varEntry.getName(), result.getValue(), midVar);
      }
      case "%=" -> {
        emit("%", varEntry.getName(), result.getValue(), midVar);
      }
      default -> {
        if (!operator.equals("=")) {
          error(String.format("[r: %d, c: %d]-不支持的赋值操作符 '%s'", assignmentNode.getRow(), assignmentNode.getCol(), operator));
          return;
        }
        midVar = result.getValue();
      }
    }
    emit("=", midVar, "", varEntry.getName());
    processDelayedTasks();
  }

  // 分析表达式并返回表达式的类型
  private Result analyzeExpression(TokenTreeView expressionNode) {
    return switch (expressionNode.getNodeType()) {
      case LOGIC_EXPR -> analyzeLogicExpression(expressionNode);
      case RELATIONAL_EXPR -> analyzeRelationalExpression(expressionNode);
      case ADDITION_EXPR -> analyzeAdditionExpression(expressionNode);
      case MULTIPLICATION_EXPR -> analyzeMultiplicationExpression(expressionNode);
      case UNARY_EXPR -> analyzeUnaryExpression(expressionNode);
      case PAREN_EXPR -> analyzeParenthesesExpression(expressionNode);
      case FUNCTION_CALL -> analyzeFunctionCall(expressionNode);
      case LITERAL_INT -> new Result(expressionNode.getValue(), "int");
      case LITERAL_FLOAT -> new Result(expressionNode.getValue(), "float");
      case LITERAL_CHAR -> new Result(expressionNode.getValue(), "char");
      case LITERAL_BOOL -> new Result(expressionNode.getValue(), "bool");
      case IDENTIFIER, PARAM -> {
        String identifierName = expressionNode.getValue();
        int col = expressionNode.getCol();
        int row = expressionNode.getRow();
        SymbolType symbolType = checkSymbolType(identifierName, getCurrentScopePath());
        if (symbolType == SymbolType.NONE) {
          error(String.format("[r: %d, c: %d]-变量 '%s' 未声明", row, col, identifierName));
          yield errorResult;
        } else if (symbolType == SymbolType.FUNCTION) {
          error(String.format("[r: %d, c: %d]-非法调用函数 '%s'", row, col, identifierName));
          yield errorResult;
        } else if (symbolType == SymbolType.CONST) {
          ConstTableEntry constEntry = findConst(identifierName);
          if (constEntry != null) {
            usedConstants.add(constEntry);
            yield new Result(constEntry.getName(), constEntry.getType());
          }
          yield errorResult;
        } else {
          VariableTableEntry varEntry = findVariable(identifierName, getCurrentScopePath());
          if (varEntry != null) {
            usedVariables.add(varEntry);
            yield new Result(varEntry.getName(), varEntry.getType());
          }
          yield errorResult;
        }
      }
      default -> {
        error(String.format("[r: %d, c: %d]-未知表达式类型 '%s'", expressionNode.getRow(), expressionNode.getCol(), expressionNode.getNodeType()));
        yield errorResult;
      }
    };
  }

  // 分析后缀语句
  private Result analyzeSuffixStatement(TokenTreeView suffixNode) {
    TokenTreeView operandNode = suffixNode.getChildren().getFirst();
    Result operandType = analyzeExpression(operandNode);
    if (!operandType.getType().equals("int")) {
      error(String.format("[r: %d, c: %d]-后缀表达式类型不正确，期望为 int，实际为 %s", suffixNode.getRow(), suffixNode.getCol(), operandType.getType()));
      return errorResult;
    }
    // 生成中间代码
    String midVar = newTmp();
    emitDelayed(opTrans(suffixNode.getChildren().getLast().getValue()), operandType.getValue(), "1", midVar);
    emitDelayed("=", midVar, "", operandType.getValue());
    return new Result(operandType.getValue(), operandType.getType());
  }

  // 分析前缀语句
  private Result analyzePrefixStatement(TokenTreeView prefixNode) {
    TokenTreeView operandNode = prefixNode.getChildren().getLast();
    Result operandType = analyzeExpression(operandNode);
    if (!operandType.getType().equals("int")) {
      error(String.format("[r: %d, c: %d]-前缀表达式类型不正确，期望为 int，实际为 %s", prefixNode.getRow(), prefixNode.getCol(), operandType.getType()));
      return errorResult;
    }
    // 生成中间代码
    String midVar = newTmp();
    emit(opTrans(prefixNode.getChildren().getFirst().getValue()), operandType.getValue(), "1", midVar);
    emit("=", midVar, "", operandType.getValue());
    return new Result(operandType.getValue(), operandType.getType());
  }

  // 自增自减转换操作符
  private String opTrans(String op) {
    return switch (op) {
      case "++" -> "+";
      case "--" -> "-";
      default -> op;
    };
  }

  // 分析逻辑表达式
  private Result analyzeLogicExpression(TokenTreeView logicNode) {
    Result result;
    if (logicNode.getValue().equals("!")) { // 逻辑取反 - !E
      Result tmpRes = analyzeExpression(logicNode.getChildren().getLast());
      if (!tmpRes.getType().equals("bool")) {
        error(String.format("[r: %d, c: %d]-逻辑表达式类型不正确，期望为 bool，实际为 %s", logicNode.getRow(), logicNode.getCol(), tmpRes.getType()));
        return errorResult;
      }
      result = new Result(newTmp(), "bool");
      emit("!", tmpRes.getValue(), "", result.getValue());
    } else { // 逻辑运算 && || - E1 && E2
      Result leftRes, rightRes;
      leftRes = analyzeExpression(logicNode.getChildren().getFirst());
      rightRes = analyzeExpression(logicNode.getChildren().getLast());
      if (!checkTypeMatch(leftRes, rightRes, logicNode, "bool")) {
        return errorResult;
      }
      result = new Result(newTmp(), "bool");
      emit(logicNode.getChildren().get(1).getValue(), leftRes.getValue(), rightRes.getValue(), result.getValue());
    }
    processDelayedTasks();
    return result;
  }

  // 通用检测两侧类型匹配，并且是否为指定类型 - 严格匹配
  private boolean checkTypeMatch(Result leftRes, Result rightRes, TokenTreeView node, String... types) {
    if (!(leftRes.getType().equals(rightRes.getType()))) {
      error(String.format("[r: %d, c: %d]-表达式类型不匹配，左侧为 %s，右侧为 %s", node.getRow(), node.getCol(), leftRes.getType(), rightRes.getType()));
      return false;
    }
    boolean match = false;
    for (String type : types) {
      if (leftRes.getType().equals(type)) {
        match = true;
        break;
      }
    }
    // 期望类型转列表
    List<String> expectedTypes = List.of(types);
    if (!match) {
      error(String.format("[r: %d, c: %d]-表达式类型不匹配，期望为 %s，实际为 %s", node.getRow(), node.getCol(), expectedTypes, leftRes.getType()));
      return false;
    }
    return true;
  }

  // 通用检测两侧类型匹配，并且是否为指定类型
  private boolean checkTypeMatchEase(Result leftRes, Result rightRes, TokenTreeView node, String... types) {
    boolean leftMatch = false;
    boolean rightMatch = false;
    boolean resultMatch = true;
    for (String type : types) {
      if (!leftMatch && leftRes.getType().equals(type)) {
        leftMatch = true;
      }
      if (!rightMatch && rightRes.getType().equals(type)) {
        rightMatch = true;
      }
    }
    // 期望类型转列表
    List<String> expectedTypes = List.of(types);
    if (!leftMatch) {
      error(String.format("[r: %d, c: %d]-左表达式类型不匹配，期望为 %s，实际为 %s", node.getRow(), node.getCol(), expectedTypes, leftRes.getType()));
      resultMatch = false;
    }
    if (!rightMatch) {
      error(String.format("[r: %d, c: %d]-右表达式类型不匹配，期望为 %s，实际为 %s", node.getRow(), node.getCol(), expectedTypes, rightRes.getType()));
      resultMatch = false;
    }
    return resultMatch;
  }

  // 分析关系表达式
  private Result analyzeRelationalExpression(TokenTreeView relationalNode) {
    // 只能是整数或浮点数与整数或浮点数的关系表达式
    Result leftRes = analyzeExpression(relationalNode.getChildren().getFirst());
    Result rightRes = analyzeExpression(relationalNode.getChildren().getLast());
    // 检查类型是否匹配
    String op = relationalNode.getChildren().get(1).getValue();
    if (op.equals("==") || op.equals("!=")) {
      // 等于和不等于关系运算可以是整数、浮点数、字符或布尔类型
      if (!checkTypeMatch(leftRes, rightRes, relationalNode, "int", "float", "char", "bool")) {
        return errorResult;
      }
    } else {
      // 其他关系运算只能是整数或浮点数
      if (!checkTypeMatchEase(leftRes, rightRes, relationalNode, "int", "float")) {
        return errorResult;
      }
    }
    Result result = new Result(newTmp(), "bool");
    emit(relationalNode.getChildren().get(1).getValue(), leftRes.getValue(), rightRes.getValue(), result.getValue());
    processDelayedTasks();
    return result;
  }

  // 分析加减表达式
  private Result analyzeAdditionExpression(TokenTreeView additionNode) {
    // 只能是整数或浮点数与整数或浮点数的加法表达式
    Result leftRes = analyzeExpression(additionNode.getChildren().getFirst());
    Result rightRes = analyzeExpression(additionNode.getChildren().getLast());
    if (!(leftRes.getType().equals(rightRes.getType()) && (leftRes.getType().equals("int") || leftRes.getType()
                                                                                                     .equals("float")))) {
      error(String.format("[r: %d, c: %d]-加减表达式类型不匹配，左侧为 %s，右侧为 %s", additionNode.getRow(), additionNode.getCol(), leftRes, rightRes));
      return errorResult;
    }
    // 生成四元式
    Result result = new Result(newTmp(), leftRes.getType());
    emit(additionNode.getChildren().get(1).getValue(), leftRes.getValue(), rightRes.getValue(), result.getValue());
    processDelayedTasks();
    return result;
  }

  // 分析乘除表达式
  private Result analyzeMultiplicationExpression(TokenTreeView multiplicationNode) {
    // 只能是整数或浮点数与整数或浮点数的乘法表达式
    Result leftRes = analyzeExpression(multiplicationNode.getChildren().getFirst());
    Result rightRes = analyzeExpression(multiplicationNode.getChildren().getLast());
    if (!(leftRes.getType().equals(rightRes.getType()) && (leftRes.getType().equals("int") || leftRes.getType()
                                                                                                     .equals("float")))) {
      error(String.format("[r: %d, c: %d]-乘除表达式类型不匹配，左侧为 %s，右侧为 %s", multiplicationNode.getRow(), multiplicationNode.getCol(), leftRes, rightRes));
      return errorResult;
    }
    // 生成四元式
    Result result = new Result(newTmp(), leftRes.getType());
    emit(multiplicationNode.getChildren()
                           .get(1)
                           .getValue(), leftRes.getValue(), rightRes.getValue(), result.getValue());
    processDelayedTasks();
    return result;
  }

  // 分析一元表达式
  private Result analyzeUnaryExpression(TokenTreeView unaryNode) {
    // 只能是整数或浮点数的一元表达式
    Result operandType;
    if (unaryNode.getValue().contains("后缀")) {
      operandType = analyzeSuffixStatement(unaryNode);
    } else if (unaryNode.getValue().contains("前缀")) {
      operandType = analyzePrefixStatement(unaryNode);
    } else {
      operandType = analyzeExpression(unaryNode.getChildren().getLast());
      if (!(operandType.getType().equals("int") || operandType.getType().equals("float"))) {
        error(String.format("[r: %d, c: %d]-一元表达式类型不匹配，操作数为 %s", unaryNode.getRow(), unaryNode.getCol(), operandType));
        return errorResult;
      } else {
        // 生成中间代码
        if (unaryNode.getChildren().getFirst().getValue().equals("-")) {
          String tmp = newTmp();
          emit("-", "0", operandType.getValue(), tmp);
          operandType.setValue(tmp);
        }
      }
      processDelayedTasks();
    }
    return operandType;
  }

  // 分析括号表达式
  private Result analyzeParenthesesExpression(TokenTreeView parenthesesNode) {
    processDelayedTasks();
    // 括号表达式的类型取决于其内部表达式的类型
    return analyzeExpression(parenthesesNode.getChildren().get(1));
  }

  // 提交一条中间代码，返回生成的中间代码的索引
  private int emit(String op, String arg1, String arg2, String result) {
    middleTableList.add(new MiddleTableEntry(midId, op, arg1, arg2, result));
    return midId++;
  }

  private void emit(MiddleTableEntry entry) {
    entry.setId(midId++);
    middleTableList.add(entry);
  }

  // 合并两个四元式链
  private int merge(int P1, int P2) {
    if (P2 == 0) {
      return P1;
    } else {
      int P = P2;
      while (Integer.parseInt(middleTableList.get(P).getResult()) != 0) {
        P = Integer.parseInt(middleTableList.get(P).getResult());
      }
      middleTableList.get(P).setResult(String.valueOf(P1));
      return P2;
    }
  }

  // 提交一条延迟的中间代码
  private void emitDelayed(String op, String arg1, String arg2, String result) {
    delayedTasks.add(new MiddleTableEntry(-1, op, arg1, arg2, result));
  }

  // 处理延迟任务
  private void processDelayedTasks() {
    for (MiddleTableEntry task : delayedTasks) {
      emit(task);
    }
    delayedTasks.clear();
  }

  // 递归回填
  private void backPatch(int pos, int resPos) {
    int Q = middleTableList.get(pos).getId();
    while (Q != 0) {
      Q = backSet(Q, resPos);
    }
  }

  // 回填
  private int backSet(int pos, int resPos) {
    int m = Integer.parseInt(middleTableList.get(pos).getResult());
    middleTableList.get(pos).setResult(String.valueOf(resPos));
    return m;
  }

  // 生成临时变量
  private String newTmp() {
    return "$_t" + tempId++;
  }

  // 检查标识符是否已经在对应符号表中声明
  private SymbolType checkSymbolType(String name, String scopePath) {
    if (findConst(name) != null) {
      return SymbolType.CONST;
    }
    if (findFunction(name) != null) {
      return SymbolType.FUNCTION;
    }
    if (findVariable(name, scopePath) != null) {
      return SymbolType.VAR;
    }
    return SymbolType.NONE;
  }

  // 作用域管理方法
  private void enterScope() {
    scopeStack.push(nextScopeId);
    declaredVariablesInScope.clear(); // 进入新作用域时，清空当前作用域的声明记录
    // info("进入作用域: " + getCurrentScopePath() + " (ID: " + nextScopeId + ")");
    nextScopeId++;
  }

  private void exitScope() {
    if (!scopeStack.isEmpty()) {
      declaredVariablesInScope.clear(); // 退出作用域时，清空，尽管通常在enter时处理
      // info("退出作用域: " + getCurrentScopePath() + " (原ID: " + scopeStack.pop() + ")");
    } else {
      error("尝试退出作用域失败：作用域栈为空。");
    }
  }

  // 检查是否为值节点
  private boolean isValueNode(TokenTreeView node) {
    NodeType type = node.getNodeType();
    return type == NodeType.LITERAL_INT || type == NodeType.LITERAL_FLOAT || type == NodeType.LITERAL_CHAR || type == NodeType.LITERAL_BOOL;
  }

  // 检查是否为表达式节点
  private boolean isExpressionNode(TokenTreeView node) {
    NodeType type = node.getNodeType();
    return type == NodeType.EXPRESSION || type == NodeType.BINARY_EXPR || type == NodeType.UNARY_EXPR || type == NodeType.PAREN_EXPR || type == NodeType.RELATIONAL_EXPR || type == NodeType.LOGIC_EXPR || type == NodeType.ADDITION_EXPR || type == NodeType.MULTIPLICATION_EXPR || type == NodeType.FUNCTION_CALL;
  }

  // 辅助方法：获取当前作用域路径
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

  // 辅助方法：查找变量 (考虑作用域)
  private VariableTableEntry findVariable(String name, String currentScopePath) {
    // 从当前作用域向上查找
    while (currentScopePath != null) {
      for (VariableTableEntry entry : variableTable) {
        if (entry.getName().equals(name) && entry.getScope().equals(currentScopePath)) {
          return entry;
        }
      }
      // 向上一级作用域查找
      currentScopePath = getScopeParentPath(currentScopePath);
    }
    return null; // 未找到
  }

  // 辅助方法：查找常量
  private ConstTableEntry findConst(String name) {
    // 从当前作用域向上查找
    for (ConstTableEntry entry : constTable) {
      if (entry.getName().equals(name)) {
        return entry;
      }
    }
    return null; // 未找到
  }

  // 辅助方法：获取父级作用域路径
  private String getScopeParentPath(String currentScopePath) {
    // 移除末尾的斜杠并根据路径分割
    String[] parts = currentScopePath.substring(0, currentScopePath.length() - 1).split("/");
    if (parts.length > 1) {
      // 移除最后一个部分（当前作用域ID）
      parts = java.util.Arrays.copyOf(parts, parts.length - 1);
      // 构建父级路径
      return String.join("/", parts) + "/";
    }
    return null;
  }

  // 辅助方法：查找函数
  private FunctionTableEntry findFunction(String name) {
    for (FunctionTableEntry entry : functionTable) {
      if (entry.getName().equals(name)) {
        return entry;
      }
    }
    return null; // 未找到
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

  public List<MiddleTableEntry> getMiddleTableEntries() {
    return new ArrayList<>(middleTableList); // 返回副本以防外部修改
  }

  @Getter
  private enum SymbolType {
    VAR("变量"), CONST("常量"), FUNCTION("函数"), NONE("未声明");

    private final String description;

    SymbolType(String description) {
      this.description = description;
    }

  }

  @Getter
  @Setter
  @AllArgsConstructor
  private class Result {

    private String value;
    private String type;
  }
}

