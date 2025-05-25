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
 * 主要功能： 1. 符号表管理（变量定义、使用检查） 2. 类型检查（表达式类型推导、类型匹配验证） 3. 常量处理 4. 标识冗余计算（无副作用表达式警告） 5. 作用域管理（嵌套作用域支持） 6. 函数声明与调用分析
 */
@Getter
public class SemanticAnalyzer {

  private final OutInfo outInfos;
  private final String src = "语义分析";
  private final List<String> errors;                  // 错误信息列表
  private final List<String> warnings;                // 警告信息列表

  private final List<VariableTableEntry> variableTable; // 变量表
  private final List<ConstTableEntry> constTable;     // 常量表
  private final List<FunctionTableEntry> functionTable; // 函数表

  private final Stack<Integer> scopeStack;            // 作用域栈
  private int nextScopeId;                            //下一个作用域的ID
  private boolean mainFunctionFound;                  // 是否找到主函数
  // 用于跟踪变量使用情况
  private final Set<VariableTableEntry> usedVariables;

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
    // functionTable.clear();
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

  // 分析主程序
  private void analyzeProgram(TokenTreeView node) {
    for (TokenTreeView child : node.getChildren()) {
      NodeType nodeType = child.getNodeType();
      if (nodeType == null) {
          warn("节点 " + child.getValue() + " 的 NodeType 为空，跳过处理。");
          continue;
      }
      switch (nodeType) {
        case DEFINITION -> analyzeDefinition(child); // 全局变量/常量定义
        case FUNCTION -> {
          // 主函数
          if ("主函数".equals(child.getValue())) { // 假设主函数节点的值为 "主函数"
            if (mainFunctionFound) {
                error("程序中不能有多个主函数");
            }
            mainFunctionFound = true;
            analyzeMainFunction(child);
          } else {
            // analyzeFunctionDeclaration(child); // 其他函数声明
            warn("发现非主函数定义：" + child.getValue() + "，暂不处理。");
          }
        }
        default -> warn("在 Program 级别发现未处理的节点类型: " + nodeType + " (Value: " + child.getValue() + ")");
      }
    }
  }

  // 进入新的作用域
  private void enterScope() {
    scopeStack.push(nextScopeId++);
  }

  // 退出当前作用域
  private void exitScope() {
    if (!scopeStack.isEmpty()) {
      String exitedScopePath = getCurrentScopePath(false); // 获取即将退出的作用域路径
      // 检查当前作用域内声明但未使用的变量
      checkForUnusedVariables(exitedScopePath);
      scopeStack.pop();
    } else {
      error("尝试退出作用域时，作用域栈为空");
    }
  }

  // 获取当前作用域路径字符串
  private String getCurrentScopePath(boolean forNewEntry) {
    if (scopeStack.isEmpty()) {
      return "/"; // 或者抛出错误，不应该为空
    }
    if (forNewEntry) {
        return scopeStack.stream().map(String::valueOf).collect(Collectors.joining("/")) + "/";
    }
    // 当检查父作用域或已存在条目时，不需要尾部的 nextScopeId
    Stack<Integer> tempStack = new Stack<>();
    tempStack.addAll(scopeStack);
    if (!forNewEntry && tempStack.size() > 1) { // 如果不是为新条目且不是全局作用域，则不包含最后一个元素（当前新作用域的id）
        // 这个逻辑可能需要调整，取决于 getCurrentScopePath 何时被调用
        // 对于 checkForUnusedVariables，我们需要的是当前栈代表的完整路径
    }
    return tempStack.stream().map(String::valueOf).collect(Collectors.joining("/")) + "/";
  }

  // 声明的变量未使用警告 (在此阶段仅为框架，具体实现需要跟踪变量使用)
  private void checkForUnusedVariables(String scopePath) {
    for (VariableTableEntry varEntry : variableTable) {
      if (varEntry.getScope().equals(scopePath) && !usedVariables.contains(varEntry)) {
        warn("变量 '" + varEntry.getName() + "' 在作用域 '" + scopePath + "' 中已声明但从未使用");
      }
    }
  }

  // 分析定义语句 (变量或常量)
  private void analyzeDefinition(TokenTreeView definitionNode) {
    // TODO: 实现定义分析，包括提取名称、类型、值，并进行重复声明检查
    // NodeType.DEFINITION 的子节点结构未知，需要根据实际情况调整
    // 假设子节点：0: 类型, 1: 名称, [2: 初始值 (可选)]
    // 假设 definitionNode 本身包含是变量还是常量的类型信息，或者其子节点指明
    warn("analyzeDefinition 方法尚未完全实现。节点: " + definitionNode.getValue());
  }

  // 分析主函数
  private void analyzeMainFunction(TokenTreeView functionNode) {
    info("开始分析主函数...");
    enterScope();
    // TODO: 分析主函数体内的语句，例如变量定义、赋值等
    // 遍历 functionNode.getChildren() 来处理函数体内的语句
    // for (TokenTreeView statement : functionNode.getChildren()) { ... }
    warn("analyzeMainFunction 方法尚未完全实现函数体分析。节点: " + functionNode.getValue());
    exitScope();
    info("主函数分析结束。");
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

  /**
   * 输出信息
   *
   * @param message 信息
   */
  private void info(String message) {
    outInfos.info(src, message);
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
