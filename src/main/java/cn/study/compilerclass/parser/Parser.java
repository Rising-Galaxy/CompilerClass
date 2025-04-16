package cn.study.compilerclass.parser;

import cn.study.compilerclass.lexer.Token;
import cn.study.compilerclass.lexer.TokenManager;
import cn.study.compilerclass.utils.OutInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

public class Parser {

  private ErrorProcess errorProcess = ErrorProcess.SKIP;
  private final TokenManager tokenManager;
  private final OutInfo outInfos;
  private final String src = "语法分析";
  private List<Token> tokens;
  private int currentPos;
  private TokenTreeView root;

  public Parser(String filePath, OutInfo outInfos) {
    this.tokenManager = new TokenManager();
    this.outInfos = outInfos;
    this.currentPos = 0;
    readTokens(filePath);
  }

  private void readTokens(String filePath) {
    if (filePath == null || filePath.isEmpty()) {
      error("Tokens 文件路径不能为空");
      tokens = null;
      return;
    }
    File file = new File(filePath);
    Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    try (Reader reader = new InputStreamReader(new FileInputStream(file))) {
      Token[] tokens = gson.fromJson(reader, Token[].class);
      this.tokens = List.of(tokens);
    } catch (NullPointerException e) {
      error("Tokens 文件不存在");
      tokens = null;
    } catch (Exception e) {
      error("读取 Tokens 文件失败", e);
      tokens = null;
    }
  }

  private void error(String msg) {
    error(msg, false);
  }

  private void error(String msg, boolean advance) {
    outInfos.error(src, msg);
    if (advance && !isEOF()) {
      consume(); // 报错后前进一个token
    }
    if (errorProcess != ErrorProcess.SKIP) {
      throw new RuntimeException(msg);
    }
  }

  private void error(String msg, Exception e) {
    outInfos.error(src, msg, e);
    if (errorProcess != ErrorProcess.SKIP) {
      throw new RuntimeException(msg);
    }
  }

  // 同步到下一个安全点（分号或语句开始标记）
  private void synchronize() {
    while (!isEOF()) {
      // 同步到语句结束符号
      if (currentToken().getType() == tokenManager.getType(";")) {
        consume(); // 消费分号
        return;
      }
      // 同步到语句开始关键字
      String value = currentToken().getValue();
      if (value.equals("if") || value.equals("int") || value.equals("float") ||
          value.equals("boolean") || value.equals("void") || value.equals("const") ||
          currentToken().getType() == tokenManager.getType("{") ||
          currentToken().getType() == tokenManager.getType("}")) {
        return;
      }
      consume(); // 跳过当前token
    }
  }

  private void warn(String msg) {
    outInfos.warn(src, msg);
    if (errorProcess != ErrorProcess.WARN) {
      throw new RuntimeException(msg);
    }
  }

  private void info(String msg) {
    outInfos.info(src, msg);
  }

  public void parse() {
    if (tokens == null) {
      error("似乎没法开始语法分析捏~请先检查词法分析的结果以及其生成的 Tokens 文件是否存在...");
      return;
    }
    info("开始分析...");
    try {
      // 更改入口点为完整程序解析
      root = program();
    } catch (Exception e) {
      errorProcess = ErrorProcess.SKIP;
      error("语法分析失败", e);
      errorProcess = ErrorProcess.ERROR;
    }
    info("分析完成");
  }

  public void getTreeView(TreeView<String> treeView) {
    if (root == null) {
      return;
    }
    TreeItem<String> rootItem = convertToTreeItem(root);
    treeView.setRoot(rootItem);

    // 设置树节点的样式
    treeView.setCellFactory(tv -> {
      TreeCell<String> cell = new TreeCell<String>() {
        @Override
        protected void updateItem(String item, boolean empty) {
          super.updateItem(item, empty);

          // 清除所有旧样式，确保没有样式残留
          getStyleClass().removeAll(
              "root-node", "middle-node", "type-node", "error",
              "operator-node", "keyword-node", "value-node",
              "declaration-node", "symbol-node", "highlight");

          if (empty || item == null) {
            setText(null);
            setGraphic(null);
          } else {
            setText(item);

            // 确保保留展开/折叠图标
            TreeItem<?> treeItem = getTreeItem();
            if (treeItem != null && !treeItem.isLeaf()) {
              // 创建展开/折叠指示器
              if (treeItem.isExpanded()) {
                // 节点已展开
                this.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("expanded"), true);
                this.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("collapsed"), false);
              } else {
                // 节点已折叠
                this.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("expanded"), false);
                this.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("collapsed"), true);
              }
            } else {
              // 叶子节点，清除两种状态
              this.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("expanded"), false);
              this.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("collapsed"), false);
            }

            // 应用正确的样式类
            String value = item;
            if (value.contains("「KEYWORD」")) {
              getStyleClass().add("keyword-node");
            } else if (value.contains("「OPERATOR」")) {
              getStyleClass().add("operator-node");
            } else if (value.contains("「VALUE」")) {
              getStyleClass().add("value-node");
            } else if (value.contains("「DECLARATION」")) {
              getStyleClass().add("declaration-node");
            } else if (value.contains("「SYMBOL」")) {
              getStyleClass().add("symbol-node");
            } else if (value.contains("「ERROR」")) {
              getStyleClass().add("error");
            } else if (value.startsWith("程序")) {
              getStyleClass().add("root-node");
            } else if (value.contains("表达式") ||
                       value.contains("函数") ||
                       value.contains("语句") ||
                       value.contains("声明") ||
                       value.contains("块")) {
              getStyleClass().add("middle-node");
            } else if (value.contains("常量") ||
                       value.contains("标识符") ||
                       value.contains("「TYPE」")) {
              getStyleClass().add("type-node");
            }

            // 高亮显示
            if (value.contains("- 重要节点")) {
              getStyleClass().add("highlight");
            }
          }
        }
      };

      // 添加监听器以确保展开/折叠状态正确更新样式
      cell.treeItemProperty().addListener((obs, oldItem, newItem) -> {
        if (oldItem != null) {
          // 从旧项目中移除扩展监听器
          Object oldListener = cell.getProperties().get("expandedListener");
          if (oldListener instanceof javafx.beans.value.ChangeListener) {
            @SuppressWarnings("unchecked")
            javafx.beans.value.ChangeListener<Boolean> typedListener =
                (javafx.beans.value.ChangeListener<Boolean>) oldListener;
            oldItem.expandedProperty().removeListener(typedListener);
          }
        }

        if (newItem != null && !newItem.isLeaf()) {
          // 为新项目添加扩展监听器
          javafx.beans.value.ChangeListener<Boolean> expandedListener = (obsVal, wasExpanded, isNowExpanded) -> {
            // 更新折叠/展开的伪类状态
            cell.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("expanded"), isNowExpanded);
            cell.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("collapsed"), !isNowExpanded);
          };

          // 存储监听器并立即设置正确的初始状态
          cell.getProperties().put("expandedListener", expandedListener);
          newItem.expandedProperty().addListener(expandedListener);

          // 确保初始状态正确
          boolean isExpanded = newItem.isExpanded();
          cell.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("expanded"), isExpanded);
          cell.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("collapsed"), !isExpanded);
        }
      });

      return cell;
    });
  }

  private TreeItem<String> convertToTreeItem(TokenTreeView node) {
    // 使用更丰富的节点显示文本
    TreeItem<String> treeItem = new TreeItem<>(node.getDisplayText());

    // 根据节点的folded属性决定是否默认展开
    treeItem.setExpanded(!node.isFolded());

    for (TokenTreeView child : node.getChildren()) {
      treeItem.getChildren().add(convertToTreeItem(child));
    }
    return treeItem;
  }

  // 1. 入口点：程序解析
  private TokenTreeView program() {
    TokenTreeView program = new TokenTreeView(null, "程序", "PROGRAM", "root-node");
    program.setNodeInfo("PROGRAM", "程序入口点");

    // 解析全局变量和常量声明
    while (!isEOF() && (isVariableDeclaration() || isConstDeclaration())) {
      if (isConstDeclaration()) {
        program.addChild(constDeclaration());
      } else if (isVariableDeclaration()) {
        program.addChild(variableDeclaration());
      }
    }

    // 解析main函数
    if (!isEOF()) {
      program.addChild(mainFunction());
    }

    return program;
  }

  // 2. Main函数解析
  private TokenTreeView mainFunction() {
    TokenTreeView node = new TokenTreeView(null, "主函数", "FUNCTION", "middle-node");
    node.setNodeInfo("FUNCTION", "程序入口函数");
    node.highlightNode();

    // 返回类型
    if (!isType(currentToken())) {
      error(String.format("[r: %d, c: %d]-Expected return type for main function", currentToken().getLine(), currentToken().getColumn()));
    }
    String typeValue = currentToken().getValue();
    TokenTreeView typeNode = new TokenTreeView(node, typeValue, "TYPE", "type-node");
    typeNode.setNodeInfo("TYPE", "返回值类型");
    node.addChild(typeNode);
    consume();

    // main标识符
    if (!currentToken().getValue().equals("main")) {
      error(String.format("[r: %d, c: %d]-Expected 'main' function", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView mainNode = new TokenTreeView(node, "main", "IDENTIFIER", "keyword-node");
    mainNode.setNodeInfo("IDENTIFIER", "函数名");
    node.addChild(mainNode);
    consume();

    // 左括号
    if (currentToken().getType() != tokenManager.getType("(")) {
      error(String.format("[r: %d, c: %d]-Expected '(' after main", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView openParenNode = new TokenTreeView(node, "(", "SYMBOL", "symbol-node");
    node.addChild(openParenNode);
    consume();

    // 右括号
    if (currentToken().getType() != tokenManager.getType(")")) {
      error(String.format("[r: %d, c: %d]-Expected ')' after parameters", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView closeParenNode = new TokenTreeView(node, ")", "SYMBOL", "symbol-node");
    node.addChild(closeParenNode);
    consume();

    // 函数体
    node.addChild(block());

    return node;
  }

  // 代码块解析
  private TokenTreeView block() {
    TokenTreeView node = new TokenTreeView(null, "代码块", "BLOCK", "middle-node");
    node.setNodeInfo("BLOCK", "语句块");

    // 左大括号
    if (currentToken().getType() != tokenManager.getType("{")) {
      error(String.format("[r: %d, c: %d]-Expected '{' to start block", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView openBraceNode = new TokenTreeView(node, "{", "SYMBOL", "symbol-node");
    node.addChild(openBraceNode);
    consume();

    // 语句序列
    while (currentToken().getType() != tokenManager.getType("}") && !isEOF()) {
      node.addChild(statement());
    }

    // 右大括号
    if (currentToken().getType() != tokenManager.getType("}")) {
      error(String.format("[r: %d, c: %d]-Expected '}' to end block", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView closeBraceNode = new TokenTreeView(node, "}", "SYMBOL", "symbol-node");
    node.addChild(closeBraceNode);
    consume();

    return node;
  }

  // 语句解析
  private TokenTreeView statement() {
    try {
      if (isVariableDeclaration()) {
        return variableDeclaration();
      } else if (isConstDeclaration()) {
        return constDeclaration();
      } else if (isIfStatement()) {
        return ifStatement();
      } else if (isAssignment()) {
        return assignmentStatement();
      } else {
        // 表达式语句
        TokenTreeView expr = expression();

        // 分号
        if (currentToken().getType() != tokenManager.getType(";")) {
          error(String.format("[r: %d, c: %d]-Expected ';' after expression",
                currentToken().getLine(), currentToken().getColumn()));
          // 尝试同步到下一个语句
          synchronize();
        } else {
          TokenTreeView semicolonNode = new TokenTreeView(expr, ";", "SYMBOL", "symbol-node");
          expr.addChild(semicolonNode);
          consume();
        }

        return expr;
      }
    } catch (Exception e) {
      // 语句解析出错，尝试恢复到下一个有效位置
      TokenTreeView errorNode = new TokenTreeView(null, "语法错误", "ERROR", "error");
      errorNode.setNodeInfo("ERROR", "解析过程中发生错误");
      synchronize();
      return errorNode;
    }
  }

  // 3. 变量声明解析
  private boolean isVariableDeclaration() {
    return isType(currentToken()) && !currentToken().getValue().equals("void");
  }

  private TokenTreeView variableDeclaration() {
    TokenTreeView node = new TokenTreeView(null, "变量声明", "DECLARATION", "declaration-node");
    node.setNodeInfo("DECLARATION", "变量定义");

    // 类型
    String typeValue = currentToken().getValue();
    TokenTreeView typeNode = new TokenTreeView(node, typeValue, "TYPE", "type-node");
    node.addChild(typeNode);
    consume();

    // 标识符
    if (!isIdentifier(currentToken())) {
      error(String.format("[r: %d, c: %d]-Expected identifier", currentToken().getLine(), currentToken().getColumn()));
    }
    String identName = currentToken().getValue();
    TokenTreeView idNode = new TokenTreeView(node, identName, "IDENTIFIER", "type-node");
    idNode.setNodeInfo("IDENTIFIER", "变量名");
    node.addChild(idNode);
    consume();

    // 可选的初始化
    if (currentToken().getType() == tokenManager.getType("=")) {
      TokenTreeView assignNode = new TokenTreeView(node, "=", "OPERATOR", "operator-node");
      assignNode.setNodeInfo("OPERATOR", "赋值操作符");
      node.addChild(assignNode);
      consume();

      TokenTreeView exprNode = expression();
      exprNode.setParent(node);
      node.addChild(exprNode);
    }

    // 分号
    if (currentToken().getType() != tokenManager.getType(";")) {
      error(String.format("[r: %d, c: %d]-Expected ';'", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView semicolonNode = new TokenTreeView(node, ";", "SYMBOL", "symbol-node");
    node.addChild(semicolonNode);
    consume();

    return node;
  }

  // 7. 常量声明解析
  private boolean isConstDeclaration() {
    return currentToken().getValue().equals("const");
  }

  private TokenTreeView constDeclaration() {
    TokenTreeView node = new TokenTreeView(null, "常量声明", "DECLARATION", "declaration-node");
    node.setNodeInfo("DECLARATION", "常量定义");
    node.highlightNode(); // 常量是重要节点，高亮显示

    // const关键字
    TokenTreeView constNode = new TokenTreeView(node, "const", "KEYWORD", "keyword-node");
    constNode.setNodeInfo("KEYWORD", "常量关键字");
    node.addChild(constNode);
    consume();

    // 类型
    if (!isType(currentToken()) || currentToken().getValue().equals("void")) {
      error(String.format("[r: %d, c: %d]-Expected type after const", currentToken().getLine(), currentToken().getColumn()));
    }
    String typeValue = currentToken().getValue();
    TokenTreeView typeNode = new TokenTreeView(node, typeValue, "TYPE", "type-node");
    typeNode.setNodeInfo("TYPE", "常量类型");
    node.addChild(typeNode);
    consume();

    // 标识符
    if (!isIdentifier(currentToken())) {
      error(String.format("[r: %d, c: %d]-Expected identifier", currentToken().getLine(), currentToken().getColumn()));
    }
    String identName = currentToken().getValue();
    TokenTreeView idNode = new TokenTreeView(node, identName, "IDENTIFIER", "type-node");
    idNode.setNodeInfo("IDENTIFIER", "常量名");
    node.addChild(idNode);
    consume();

    // 必须进行初始化
    if (currentToken().getType() != tokenManager.getType("=")) {
      error(String.format("[r: %d, c: %d]-Constants must be initialized", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView assignNode = new TokenTreeView(node, "=", "OPERATOR", "operator-node");
    assignNode.setNodeInfo("OPERATOR", "赋值操作符");
    node.addChild(assignNode);
    consume();

    // 表达式
    TokenTreeView exprNode = expression();
    exprNode.setParent(node);
    node.addChild(exprNode);

    // 分号
    if (currentToken().getType() != tokenManager.getType(";")) {
      error(String.format("[r: %d, c: %d]-Expected ';'", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView semicolonNode = new TokenTreeView(node, ";", "SYMBOL", "symbol-node");
    node.addChild(semicolonNode);
    consume();

    return node;
  }

  // 5. if语句解析
  private boolean isIfStatement() {
    return currentToken().getValue().equals("if");
  }

  private TokenTreeView ifStatement() {
    TokenTreeView node = new TokenTreeView(null, "条件语句", "STATEMENT", "middle-node");
    node.setNodeInfo("STATEMENT", "条件控制结构");

    // if关键字
    TokenTreeView ifNode = new TokenTreeView(node, "if", "KEYWORD", "keyword-node");
    ifNode.setNodeInfo("KEYWORD", "条件关键字");
    node.addChild(ifNode);
    consume();

    // 左括号
    if (currentToken().getType() != tokenManager.getType("(")) {
      error(String.format("[r: %d, c: %d]-Expected '(' after 'if'", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView openParenNode = new TokenTreeView(node, "(", "SYMBOL", "symbol-node");
    node.addChild(openParenNode);
    consume();

    // 条件表达式
    TokenTreeView condition = expression();
    condition.setParent(node);
    condition.setNodeInfo("EXPRESSION", "条件表达式");
    node.addChild(condition);

    // 右括号
    if (currentToken().getType() != tokenManager.getType(")")) {
      error(String.format("[r: %d, c: %d]-Expected ')' after condition", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView closeParenNode = new TokenTreeView(node, ")", "SYMBOL", "symbol-node");
    node.addChild(closeParenNode);
    consume();

    // if分支的语句
    TokenTreeView thenStatement = statement();
    thenStatement.setParent(node);
    thenStatement.setNodeInfo("STATEMENT", "满足条件时执行");
    node.addChild(thenStatement);

    // 可选的else分支
    if (!isEOF() && currentToken().getValue().equals("else")) {
      TokenTreeView elseNode = new TokenTreeView(node, "else", "KEYWORD", "keyword-node");
      elseNode.setNodeInfo("KEYWORD", "否则关键字");
      node.addChild(elseNode);
      consume();

      TokenTreeView elseStatement = statement();
      elseStatement.setParent(node);
      elseStatement.setNodeInfo("STATEMENT", "不满足条件时执行");
      node.addChild(elseStatement);
    }

    return node;
  }

  // 6. 赋值表达式解析
  private boolean isAssignment() {
    return isIdentifier(currentToken()) && !isEOF() && lookahead(1).getType() == tokenManager.getType("=");
  }

  private TokenTreeView assignmentStatement() {
    TokenTreeView node = new TokenTreeView(null, "赋值语句", "STATEMENT", "middle-node");
    node.setNodeInfo("STATEMENT", "变量赋值");

    // 标识符
    String identName = currentToken().getValue();
    TokenTreeView idNode = new TokenTreeView(node, identName, "IDENTIFIER", "type-node");
    idNode.setNodeInfo("IDENTIFIER", "被赋值的变量");
    node.addChild(idNode);
    consume();

    // 赋值操作符
    TokenTreeView assignNode = new TokenTreeView(node, "=", "OPERATOR", "operator-node");
    assignNode.setNodeInfo("OPERATOR", "赋值操作符");
    node.addChild(assignNode);
    consume();

    // 表达式
    TokenTreeView exprNode = expression();
    exprNode.setParent(node);
    exprNode.setNodeInfo("EXPRESSION", "表达式值");
    node.addChild(exprNode);

    // 分号
    if (currentToken().getType() != tokenManager.getType(";")) {
      error(String.format("[r: %d, c: %d]-Expected ';' after assignment", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView semicolonNode = new TokenTreeView(node, ";", "SYMBOL", "symbol-node");
    node.addChild(semicolonNode);
    consume();

    return node;
  }

  // 4. 完整表达式层次结构
  private TokenTreeView expression() {
    return booleanExpression();
  }

  private TokenTreeView booleanExpression() {
    TokenTreeView node = booleanTerm();
    while (!isEOF() && currentToken().getType() == tokenManager.getType("||")) {
      String opValue = currentToken().getValue();
      TokenTreeView root = new TokenTreeView(null, "逻辑表达式", "EXPRESSION", "middle-node");
      root.setNodeInfo("EXPRESSION", "逻辑或运算");
      node.setParent(root);
      root.addChild(node);

      TokenTreeView opNode = new TokenTreeView(root, opValue, "OPERATOR", "operator-node");
      opNode.setNodeInfo("OPERATOR", "逻辑或运算符");
      root.addChild(opNode);
      consume();

      TokenTreeView right = booleanTerm();
      right.setParent(root);
      root.addChild(right);

      node = root;
    }
    return node;
  }

  private TokenTreeView booleanTerm() {
    TokenTreeView node = equalityExpression();
    while (!isEOF() && currentToken().getType() == tokenManager.getType("&&")) {
      String opValue = currentToken().getValue();
      TokenTreeView root = new TokenTreeView(null, "逻辑项", "EXPRESSION", "middle-node");
      root.setNodeInfo("EXPRESSION", "逻辑与运算");
      node.setParent(root);
      root.addChild(node);

      TokenTreeView opNode = new TokenTreeView(root, opValue, "OPERATOR", "operator-node");
      opNode.setNodeInfo("OPERATOR", "逻辑与运算符");
      root.addChild(opNode);
      consume();

      TokenTreeView right = equalityExpression();
      right.setParent(root);
      root.addChild(right);

      node = root;
    }
    return node;
  }

  private TokenTreeView equalityExpression() {
    TokenTreeView node = relationalExpression();
    while (!isEOF() && (currentToken().getType() == tokenManager.getType("==") || currentToken().getType() == tokenManager.getType("!="))) {
      String opValue = currentToken().getValue();
      TokenTreeView root = new TokenTreeView(null, "相等性表达式", "EXPRESSION", "middle-node");
      root.setNodeInfo("EXPRESSION", "相等性比较");
      node.setParent(root);
      root.addChild(node);

      TokenTreeView opNode = new TokenTreeView(root, opValue, "OPERATOR", "operator-node");
      opNode.setNodeInfo("OPERATOR", "相等性比较运算符");
      root.addChild(opNode);
      consume();

      TokenTreeView right = relationalExpression();
      right.setParent(root);
      root.addChild(right);

      node = root;
    }
    return node;
  }

  private TokenTreeView relationalExpression() {
    TokenTreeView node = additiveExpression();
    while (!isEOF() && (currentToken().getType() == tokenManager.getType("<") ||
                         currentToken().getType() == tokenManager.getType(">") ||
                         currentToken().getType() == tokenManager.getType("<=") ||
                         currentToken().getType() == tokenManager.getType(">="))) {
      String opValue = currentToken().getValue();
      TokenTreeView root = new TokenTreeView(null, "关系表达式", "EXPRESSION", "middle-node");
      root.setNodeInfo("EXPRESSION", "大小比较");
      node.setParent(root);
      root.addChild(node);

      TokenTreeView opNode = new TokenTreeView(root, opValue, "OPERATOR", "operator-node");
      opNode.setNodeInfo("OPERATOR", "关系比较运算符");
      root.addChild(opNode);
      consume();

      TokenTreeView right = additiveExpression();
      right.setParent(root);
      root.addChild(right);

      node = root;
    }
    return node;
  }

  // 重命名原来的expression为additiveExpression
  private TokenTreeView additiveExpression() {
    TokenTreeView node = term();
    while (!isEOF() && (currentToken().getType() == tokenManager.getType("+") || currentToken().getType() == tokenManager.getType("-"))) {
      String opValue = currentToken().getValue();
      TokenTreeView root = new TokenTreeView(null, "加减表达式", "EXPRESSION", "middle-node");
      root.setNodeInfo("EXPRESSION", opValue.equals("+") ? "加法运算" : "减法运算");
      node.setParent(root);
      root.addChild(node);

      TokenTreeView opNode = new TokenTreeView(root, opValue, "OPERATOR", "operator-node");
      opNode.setNodeInfo("OPERATOR", opValue.equals("+") ? "加法运算符" : "减法运算符");
      root.addChild(opNode);
      consume();

      TokenTreeView node2 = term();
      node2.setParent(root);
      root.addChild(node2);
      node = root;
    }
    return node;
  }

  private TokenTreeView term() {
    TokenTreeView node = factor();
    while (!isEOF() && (currentToken().getType() == tokenManager.getType("*") || currentToken().getType() == tokenManager.getType("/"))) {
      String opValue = currentToken().getValue();
      TokenTreeView root = new TokenTreeView(null, "乘除表达式", "EXPRESSION", "middle-node");
      root.setNodeInfo("EXPRESSION", opValue.equals("*") ? "乘法运算" : "除法运算");
      node.setParent(root);
      root.addChild(node);

      TokenTreeView opNode = new TokenTreeView(root, opValue, "OPERATOR", "operator-node");
      opNode.setNodeInfo("OPERATOR", opValue.equals("*") ? "乘法运算符" : "除法运算符");
      root.addChild(opNode);
      consume();

      TokenTreeView node2 = factor();
      node2.setParent(root);
      root.addChild(node2);
      node = root;
    }
    return node;
  }

  private TokenTreeView factor() {
    TokenTreeView root;
    try {
      if (currentToken().getType() == tokenManager.getType("(")) {
        root = new TokenTreeView(null, "括号表达式", "EXPRESSION", "middle-node");
        root.setNodeInfo("EXPRESSION", "括号内表达式");

        TokenTreeView left = new TokenTreeView(root, "(", "SYMBOL", "symbol-node");
        consume();

        TokenTreeView node = expression();
        node.setParent(root);

        TokenTreeView right;
        if (currentToken().getType() != tokenManager.getType(")")) {
          error(String.format("[r: %d, c: %d]-缺少 ')'", currentToken().getLine(), currentToken().getColumn()));
          right = new TokenTreeView(root, "缺少)", "ERROR", "error");
          right.setNodeInfo("ERROR", "括号不匹配");
          // 不再进行consume，让同步机制处理
        } else {
          right = new TokenTreeView(root, ")", "SYMBOL", "symbol-node");
          consume();
        }
        root.addChildren(left, node, right);
      } else if (currentToken().getType() == tokenManager.getType("-") || currentToken().getType() == tokenManager.getType("+")) {
        // 处理一元操作符
        String op = currentToken().getValue();
        root = new TokenTreeView(null, "一元表达式", "EXPRESSION", "middle-node");
        root.setNodeInfo("EXPRESSION", op.equals("+") ? "正号运算" : "负号运算");

        TokenTreeView opNode = new TokenTreeView(root, op, "OPERATOR", "operator-node");
        opNode.setNodeInfo("OPERATOR", op.equals("+") ? "正号运算符" : "负号运算符");
        root.addChild(opNode);
        consume();

        // 检查一元操作符后是否有操作数
        if (isEOF() || (!isConst(currentToken()) && !isIdentifier(currentToken()) &&
            currentToken().getType() != tokenManager.getType("("))) {
          // 一元操作符后缺少操作数
          error(String.format("[r: %d, c: %d]-一元操作符'%s'后缺少操作数",
                currentToken().getLine(), currentToken().getColumn(), op), true);
          TokenTreeView errorNode = new TokenTreeView(root, "缺少操作数", "ERROR", "error");
          errorNode.setNodeInfo("ERROR", "表达式不完整");
          root.addChild(errorNode);
        } else {
          TokenTreeView operandNode = factor(); // 使用factor而不是expression，避免无限递归
          operandNode.setParent(root);
          root.addChild(operandNode);
        }
      } else if (isConst(currentToken())) {
        String constValue = currentToken().getValue();
        root = new TokenTreeView(null, "常量值", "VALUE", "value-node");

        TokenTreeView node = new TokenTreeView(root, constValue, "VALUE", "value-node");
        if (currentToken().getType() == tokenManager.getType("_INTEGER_")) {
          node.setNodeInfo("VALUE", "整数字面量");
        } else if (currentToken().getType() == tokenManager.getType("_FLOAT_")) {
          node.setNodeInfo("VALUE", "浮点数字面量");
        } else {
          node.setNodeInfo("VALUE", "布尔字面量");
        }
        root.addChild(node);
        consume();
      } else if (isIdentifier(currentToken())) {
        String identName = currentToken().getValue();
        root = new TokenTreeView(null, "标识符", "IDENTIFIER", "type-node");

        TokenTreeView node = new TokenTreeView(root, identName, "IDENTIFIER", "type-node");
        node.setNodeInfo("IDENTIFIER", "变量引用");
        root.addChild(node);
        consume();
      } else {
        root = new TokenTreeView(null, "语法错误", "ERROR", "error");
        root.setNodeInfo("ERROR", "无法识别的表达式");
        error(String.format("[r: %d, c: %d]-语法错误", currentToken().getLine(), currentToken().getColumn()), true);
      }
    } catch (Exception e) {
      root = new TokenTreeView(null, "表达式解析错误", "ERROR", "error");
      root.setNodeInfo("ERROR", "解析过程中发生异常");
      synchronize(); // 同步到下一个安全点
    }
    return root;
  }

  // 辅助方法
  private boolean isType(Token token) {
    String value = token.getValue();
    return value.equals("int") || value.equals("float") || value.equals("boolean") || value.equals("void");
  }

  private boolean isIdentifier(Token token) {
    return token.getType() == tokenManager.getType("_IDENTIFIER_");
  }

  private boolean isConst(Token token) {
    return token.getType() == tokenManager.getType("_INTEGER_") || token.getType() == tokenManager.getType("_FLOAT_") || token.getValue()
                                                                                                                              .equals("true") || token.getValue()
                                                                                                                                                      .equals("false");
  }

  private Token currentToken() {
    if (currentPos >= tokens.size()) {
      return new Token("", -1, 0, 0);
    }
    return tokens.get(currentPos);
  }

  private boolean isEOF() {
    return currentPos >= tokens.size();
  }

  private Token lookahead(int offset) {
    int index = currentPos + offset;
    if (index >= tokens.size()) {
      return new Token("", -1, 0, 0);
    }
    return tokens.get(index);
  }

  private void consume() {
    currentPos++;
  }

  private enum ErrorProcess {
    SKIP, ERROR, WARN
  }
}
