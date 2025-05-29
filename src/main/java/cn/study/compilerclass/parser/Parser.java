package cn.study.compilerclass.parser;

import cn.study.compilerclass.lexer.Token;
import cn.study.compilerclass.lexer.TokenManager;
import cn.study.compilerclass.model.NodeType;
import cn.study.compilerclass.ui.SyntaxTreeStyle;
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

  private static final Token END_OF_TOKEN = new Token("", -1, 0, 0);
  public TokenTreeView treeRoot;
  private final TokenManager tokenManager;
  private final String src = "语法分析";
  private final OutInfo outInfos;
  private ErrorProcess errorProcess = ErrorProcess.SKIP;
  private List<Token> tokens;
  private int currentPos;
  private boolean hasError;

  public Parser(String filePath, OutInfo outInfos) {
    this.treeRoot = null;
    this.tokenManager = new TokenManager();
    this.outInfos = outInfos;
    this.currentPos = 0;
    this.hasError = false;
    readTokens(filePath);
  }

  public boolean hasError() {
    return hasError;
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
    hasError = true;
    outInfos.error(src, msg);
    if (errorProcess != ErrorProcess.SKIP) {
      throw new RuntimeException(msg);
    } else if (advance && !isEOF()) {
      consume(); // 报错后前进一个token
    }
  }

  private boolean isEOF() {
    return currentPos >= tokens.size();
  }

  private void consume() {
    currentPos++;
  }

  private void error(String msg, Exception e) {
    hasError = true;
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
      if (value.equals("if") || value.equals("elif") || value.equals("else") || value.equals("while") || value.equals("do") || value.equals("int") || value.equals("float") || value.equals("bool") || value.equals("void") || value.equals("const") || currentToken().getType() == tokenManager.getType("{") || currentToken().getType() == tokenManager.getType("}")) {
        return;
      }
      consume(); // 跳过当前token
    }
  }

  private void warn(String msg) {
    outInfos.warn(src, msg);
    if (errorProcess == ErrorProcess.WARN) {
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
    info("开始语法分析...");
    try {
      // 更改入口点为完整程序解析
      treeRoot = program();

      // 总结错误
      if (hasError) {
        error("语法分析过程中出现错误，请检查输出日志");
      } else {
        info("语法分析完成，没有发现错误");
      }
    } catch (Exception e) {
      errorProcess = ErrorProcess.SKIP;
      error("分析过程中出现异常", e);
      errorProcess = ErrorProcess.ERROR;
    }
  }

  public void getTreeView(TreeView<String> treeView) {
    if (treeRoot == null) {
      return;
    }
    TreeItem<String> rootItem = convertToTreeItem(treeRoot);
    treeView.setRoot(rootItem);

    // 确保TreeView本身有正确的样式类
    treeView.getStyleClass().add("result-tree");

    // 设置树节点的样式
    treeView.setCellFactory(tv -> {
      TreeCell<String> cell = new TreeCell<String>() {
        @Override
        protected void updateItem(String item, boolean empty) {
          super.updateItem(item, empty);

          // 清除所有旧样式，确保没有样式残留
          getStyleClass().removeAll("root-node", "middle-node", "normal-node", "error", "operator-node", "keyword-node", "value-node", "declaration-node", "symbol-node", "highlight", "default", "params-list", "params", "function-call");

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

            // 根据节点类型应用样式类
            // 从显示文本中提取节点类型信息
            NodeType nodeType = null;
            if (item.contains("「") && item.contains("」")) {
              String typeStr = item.substring(item.indexOf("「") + 1, item.indexOf("」"));
              try {
                nodeType = NodeType.fromString(typeStr);
              } catch (IllegalArgumentException e) {
                // 如果无法转换为枚举，则使用默认样式
                error("无法识别的节点类型：" + typeStr);
              }
            }

            // 应用样式类
            String styleClass = SyntaxTreeStyle.getStyleClass(nodeType);
            getStyleClass().add(styleClass);

            // 高亮显示
            if (item.contains("- 重要节点")) {
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
            @SuppressWarnings("unchecked") javafx.beans.value.ChangeListener<Boolean> typedListener = (javafx.beans.value.ChangeListener<Boolean>) oldListener;
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

  /**
   * 将 {@linkplain cn.study.compilerclass.lexer.TokenView TokenTreeView} 转换为
   * {@linkplain  javafx.scene.control.TreeItem TreeItem}
   *
   * @param node 要转换的TokenTreeView节点
   * @return 转换后的TreeItem
   */
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

  /*===============================
             程序结构
   *===============================*/

  /**
   * 完整程序解析</br> 文法：</br> {@code Program} -> {@code GlobalDeclaration}*</br> {@code GlobalDeclaration} →
   * {@link Parser#mainFunction() MainFunction} | {@link Parser#constDeclaration() ConstDeclaration} |
   * {@link Parser#variableDefinition() VariableDefinition} | {@link Parser#functionPrototype() FunctionPrototype} |
   * {@link Parser#functionDefinition() FunctionDefinition}
   *
   * @return 程序的语法树
   */
  private TokenTreeView program() {
    TokenTreeView program = new TokenTreeView("程序", NodeType.PROGRAM, "入口点", currentToken().getLine(), currentToken().getColumn());
    program.setFolded(false);

    while (!isEOF()) {
      // 处理全局声明（变量声明、函数声明、函数定义）
      if (isMainFunction()) {
        program.addChild(mainFunction());
      } else if (isConstDeclaration()) {
        program.addChild(constDeclaration());
      } else if (isType(currentToken())) {
        TokenTreeView declaration = declaration();
        if (declaration != null) {
          program.addChild(declaration);
        } else {
          synchronize();
        }
      } else {
        error(String.format("[r: %d, c: %d]-非法的全局声明", currentToken().getLine(), currentToken().getColumn()), true);
        synchronize();
      }
    }

    return program;
  }

  /*===============================
             主函数
   *===============================*/

  /**
   * 主函数解析</br> 文法：</br> {@code MainFunction} -> {@link Parser#isType(Token) Type} "main" "(" ")"
   * {@link Parser#block() Block}
   *
   * @return 主函数的语法树
   */
  private TokenTreeView mainFunction() {
    // 类型
    consume();
    // main标识符
    consume();
    // 左括号
    if (currentToken().getType() != tokenManager.getType("(")) {
      error(String.format("[r: %d, c: %d]-主函数 main 后缺少'('", currentToken().getLine(), currentToken().getColumn()));
    }
    consume();
    // 右括号
    if (currentToken().getType() != tokenManager.getType(")")) {
      error(String.format("[r: %d, c: %d]-主函数缺少')'", currentToken().getLine(), currentToken().getColumn()));
    }
    consume();

    TokenTreeView node = block();
    node.setValue("主函数");
    node.setNodeInfo(NodeType.FUNCTION, "程序入口函数");
    node.highlightNode();
    node.setFolded(false);
    return node;
  }

  /*===============================
           代码块
   *===============================*/

  // 代码块解析
  private TokenTreeView block() {
    TokenTreeView node = new TokenTreeView("代码块", NodeType.BLOCK, currentToken().getLine(), currentToken().getColumn());

    if (currentToken().getType() != tokenManager.getType("{")) {
      error("缺少 '{'");
    }
    consume();

    while (!isEOF() && currentToken().getType() != tokenManager.getType("}")) {
      node.addChild(statement());
    }

    if (currentToken().getType() == tokenManager.getType("}")) {
      consume();
    } else {
      error("缺少 '}'");
    }

    return node;
  }

  /*===============================
           语句
   *===============================*/

  // 语句解析
  private TokenTreeView statement() {
    try {
      TokenTreeView tmp;
      if (isType(currentToken())) {
        // 如果是类型，则可能是变量定义或函数定义
        return declaration();
      } else if (isConstDeclaration()) {
        tmp = constDeclaration();
      } else if (isIfStatement()) {
        tmp = ifStatement();
      } else if (isWhileStatement()) {
        tmp = whileStatement();
      } else if (isDoWhileStatement()) {
        tmp = doWhileStatement();
      } else if (isReturnStatement()) {
        tmp = returnStatement();
      } else if (isAssignment()) {
        tmp = assignmentStatement();
      } else {
        // 表达式语句
        TokenTreeView expr = expression();

        // 分号
        if (currentToken().getType() != tokenManager.getType(";")) {
          error(String.format("[r: %d, c: %d]-表达式后缺少';'", currentToken().getLine(), currentToken().getColumn()));
          // 尝试同步到下一个语句
          synchronize();
        } else {
          consume();
        }
        tmp = expr;
      }
      return tmp;
    } catch (Exception e) {
      // 语句解析出错，尝试恢复到下一个有效位置
      TokenTreeView errorNode = new TokenTreeView("语法错误", NodeType.ERROR, "解析过程中发生错误", currentToken().getLine(), currentToken().getColumn());
      synchronize();
      return errorNode;
    }
  }

  // 判断是否为return语句
  private boolean isReturnStatement() {
    return currentToken().getValue().equals("return");
  }

  // 解析return语句
  private TokenTreeView returnStatement() {
    TokenTreeView node = new TokenTreeView("返回语句", NodeType.RETURN_STMT, "函数返回语句", currentToken().getLine(), currentToken().getColumn());

    // return关键字
    TokenTreeView returnNode = new TokenTreeView(node, "return", NodeType.KEYWORD, "return关键字", currentToken().getLine(), currentToken().getColumn());
    node.addChild(returnNode);
    consume();

    // 返回值表达式（如果有）
    if (currentToken().getType() != tokenManager.getType(";")) {
      TokenTreeView expr = expression();
      expr.setParent(node);
      expr.setDescription("返回值表达式");
      node.addChild(expr);
    }

    // 分号
    if (currentToken().getType() != tokenManager.getType(";")) {
      error(String.format("[r: %d, c: %d]-return语句后缺少';'", currentToken().getLine(), currentToken().getColumn()));
    } else {
      consume();
    }

    return node;
  }

  // 控制结构节点的通用方法
  private TokenTreeView createControlStructureNode(String displayText, NodeType nodeType, String nodeInfo, String keyword, boolean requiresCondition, boolean requiresBlock) {
    TokenTreeView node = new TokenTreeView(displayText, nodeType, nodeInfo, currentToken().getLine(), currentToken().getColumn());

    // 添加关键字节点
    TokenTreeView keywordNode = new TokenTreeView(node, keyword, NodeType.KEYWORD, keyword + "关键字", currentToken().getLine(), currentToken().getColumn());
    node.addChild(keywordNode);
    consume();

    // 处理条件表达式（如果需要）
    if (requiresCondition) {
      // 左括号
      if (currentToken().getType() != tokenManager.getType("(")) {
        error(String.format("[r: %d, c: %d]-'%s'后缺少'('", currentToken().getLine(), currentToken().getColumn(), keyword));
      }
      consume();

      // 条件表达式
      TokenTreeView condition = expression();
      condition.setParent(node);
      condition.setDescription("条件表达式");
      node.addChild(condition);

      // 右括号
      if (currentToken().getType() != tokenManager.getType(")")) {
        error(String.format("[r: %d, c: %d]-条件后缺少')'", currentToken().getLine(), currentToken().getColumn()));
      } else {
        consume();
      }
    }

    // 处理语句块或单个语句
    if (requiresBlock) {
      TokenTreeView body;
      if (currentToken().getType() == tokenManager.getType("{")) {
        body = block();
      } else {
        body = statement();
      }
      body.setParent(node);
      body.setDescription(keyword + "语句体");
      node.addChild(body);
    }

    return node;
  }

  // 判断是否为while语句
  private boolean isWhileStatement() {
    return currentToken().getValue().equals("while");
  }

  // 解析while语句
  private TokenTreeView whileStatement() {
    return createControlStructureNode("循环语句", NodeType.WHILE_STMT, "", "while", true, true);
  }

  // 判断是否为do-while语句
  private boolean isDoWhileStatement() {
    return currentToken().getValue().equals("do");
  }

  // 解析do-while语句
  private TokenTreeView doWhileStatement() {
    TokenTreeView node = new TokenTreeView("循环语句", NodeType.DO_WHILE_STMT, currentToken().getLine(), currentToken().getColumn());

    // do关键字
    TokenTreeView doNode = new TokenTreeView(node, "do", NodeType.KEYWORD, currentToken().getLine(), currentToken().getColumn());
    node.addChild(doNode);
    consume();

    // 循环体
    TokenTreeView loopBody;
    if (currentToken().getValue().equals("{")) {
      loopBody = block();
    } else {
      loopBody = statement();
    }
    loopBody.setParent(node);
    loopBody.setNodeInfo(NodeType.DO_WHILE_STMT, "循环体");
    node.addChild(loopBody);

    // while部分可以使用公共方法的部分逻辑
    if (!currentToken().getValue().equals("while")) {
      error(String.format("[r: %d, c: %d]-'do'后缺少'while'", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView whileNode = new TokenTreeView(node, "while", NodeType.KEYWORD, currentToken().getLine(), currentToken().getColumn());
    node.addChild(whileNode);
    consume();

    // 左括号
    if (currentToken().getType() != tokenManager.getType("(")) {
      error(String.format("[r: %d, c: %d]-'while'后缺少'('", currentToken().getLine(), currentToken().getColumn()));
    }
    consume();

    // 条件表达式
    TokenTreeView condition = expression();
    condition.setParent(node);
    condition.setNodeInfo(NodeType.EXPRESSION, "循环条件");
    node.addChild(condition);

    // 右括号
    if (currentToken().getType() != tokenManager.getType(")")) {
      error(String.format("[r: %d, c: %d]-条件后缺少')'", currentToken().getLine(), currentToken().getColumn()));
    } else {
      consume();
    }

    // 分号
    if (currentToken().getType() != tokenManager.getType(";")) {
      error(String.format("[r: %d, c: %d]-do-while语句后缺少';'", currentToken().getLine(), currentToken().getColumn()));
    } else {
      consume();
    }

    return node;
  }

  // 判断是否为if语句
  private boolean isIfStatement() {
    return currentToken().getValue().equals("if");
  }

  // 解析if语句
  private TokenTreeView ifStatement() {
    TokenTreeView node = new TokenTreeView("条件语句", NodeType.IF_STMT, currentToken().getLine(), currentToken().getColumn());
    TokenTreeView ifNode = createControlStructureNode("条件分支", NodeType.STATEMENT, "if", "if", true, true);
    ifNode.setParent(node);
    node.addChild(ifNode);

    // 处理elif和else部分
    while (!isEOF()) {
      if (currentToken().getValue().equals("elif")) {
        TokenTreeView elifNode = createControlStructureNode("条件分支", NodeType.STATEMENT, "elif", "elif", true, true);
        elifNode.setParent(node);
        node.addChild(elifNode);
      } else if (currentToken().getValue().equals("else")) {
        TokenTreeView elseNode = createControlStructureNode("条件分支", NodeType.STATEMENT, "else", "else", false, true);
        elseNode.setParent(node);
        node.addChild(elseNode);
        break; // else是最后一个分支
      } else {
        break; // 不是elif或else，结束if语句解析
      }
    }

    return node;
  }

  // 赋值语句解析
  private boolean isAssignment() {
    if (!isIdentifier(currentToken())) {
      return false;
    }

    // 保存当前位置
    int savePos = currentPos;
    consume(); // 消费标识符

    // 检查是否为赋值运算符或复合赋值运算符
    boolean isAssign = false;
    if (!isEOF()) {
      int tokenType = currentToken().getType();
      isAssign = tokenType == tokenManager.getType("=") || tokenType == tokenManager.getType("+=") || tokenType == tokenManager.getType("-=") || tokenType == tokenManager.getType("*=") || tokenType == tokenManager.getType("/=") || tokenType == tokenManager.getType("%=");
    }

    // 恢复位置
    currentPos = savePos;
    return isAssign;
  }

  private TokenTreeView assignmentStatement() {
    TokenTreeView node = new TokenTreeView("赋值语句", NodeType.ASSIGNMENT_STMT, currentToken().getLine(), currentToken().getColumn());

    // 标识符
    String identName = currentToken().getValue();
    TokenTreeView idNode = new TokenTreeView(node, identName, NodeType.IDENTIFIER, "被赋值的变量", currentToken().getLine(), currentToken().getColumn());
    node.addChild(idNode);
    consume();

    // 赋值操作符
    String operator = currentToken().getValue();
    TokenTreeView assignNode = new TokenTreeView(node, operator, NodeType.OPERATOR, currentToken().getLine(), currentToken().getColumn());

    // 根据不同的赋值运算符设置不同的描述
    switch (operator) {
      case "=" -> assignNode.setDescription("赋值操作符");
      case "+=" -> assignNode.setDescription("加法赋值操作符");
      case "-=" -> assignNode.setDescription("减法赋值操作符");
      case "*=" -> assignNode.setDescription("乘法赋值操作符");
      case "/=" -> assignNode.setDescription("除法赋值操作符");
      case "%=" -> assignNode.setDescription("取模赋值操作符");
    }

    node.addChild(assignNode);
    consume();

    // 表达式
    TokenTreeView exprNode = expression();
    exprNode.setParent(node);
    exprNode.setDescription("将赋予的值");
    node.addChild(exprNode);

    // 分号
    if (currentToken().getType() != tokenManager.getType(";")) {
      error(String.format("[r: %d, c: %d]-赋值语句后缺少';'", currentToken().getLine(), currentToken().getColumn()));
    }
    consume();

    return node;
  }

  /*===============================
           声明 / 定义
   *===============================*/

  // 变量声明预检
  private boolean isVariableDeclaration() {
    return isType(currentToken()) && !currentToken().getValue().equals("void");
  }

  /**
   * 判断 {@code token} 是否为类型 类型包括：{@code int}、{@code float}、{@code bool}、{@code void}
   *
   * @param token token对象
   * @return 是否为类型
   */
  private boolean isType(Token token) {
    String value = token.getValue();
    return value.equals("int") || value.equals("float") || value.equals("bool") || value.equals("void");
  }

  private Token currentToken() {
    if (currentPos >= tokens.size()) {
      return END_OF_TOKEN;
    }
    return tokens.get(currentPos);
  }

  /**
   * 常量定义解析</br> 文法：</br> {@code ConstDeclaration} -> "const" {@link Parser#isType(Token) Type}
   * {@link Parser#singleConstDefinition(TokenTreeView) SingleConstDefinition}
   * (","{@link Parser#singleConstDefinition(TokenTreeView) SingleConstDefinition})* ";"
   *
   * @return 常量定义的语法树
   */
  private TokenTreeView constDeclaration() {
    // 创建一个父节点来包含所有常量声明
    TokenTreeView node = new TokenTreeView("常量定义", NodeType.DEFINITION, "列表", currentToken().getLine(), currentToken().getColumn());

    // const关键字
    TokenTreeView constNode = new TokenTreeView(node, "const", NodeType.KEYWORD, currentToken().getLine(), currentToken().getColumn());
    node.addChild(constNode);
    consume();

    // 类型（所有常量共享同一类型）
    if (!isType(currentToken())) {
      error(String.format("[r: %d, c: %d]-常量定义缺少类型", currentToken().getLine(), currentToken().getColumn()));
    } else if (currentToken().getValue().equals("void")) {
      error(String.format("[r: %d, c: %d]-常量定义不允许使用 void 类型", currentToken().getLine(), currentToken().getColumn()));
      synchronize();
      return new TokenTreeView("常量定义类型错误", NodeType.ERROR, "常量定义不允许使用 void 类型", currentToken().getLine(), currentToken().getColumn());
    }
    String typeValue = currentToken().getValue();
    TokenTreeView typeNode = new TokenTreeView(node, typeValue, NodeType.TYPE, currentToken().getLine(), currentToken().getColumn());
    node.addChild(typeNode);
    consume();

    // 解析第一个常量定义
    singleConstDefinition(node);

    // 处理多个常量定义（以逗号分隔）
    while (currentToken().getType() == tokenManager.getType(",")) {
      consume();
      // 解析下一个常量定义
      singleConstDefinition(node);
    }

    // 分号
    if (currentToken().getType() != tokenManager.getType(";")) {
      error(String.format("[r: %d, c: %d]-常量定义后缺少';'", currentToken().getLine(), currentToken().getColumn()));
    } else {
      consume();
    }

    return node;
  }

  /**
   * 检查当前 {@code token} 是否为常量声明关键字
   *
   * @return 如果是常量声明关键字，则返回 {@code true}；否则返回 {@code false}
   */
  private boolean isConstDeclaration() {
    return currentToken().getValue().equals("const");
  }

  /**
   * 单个常量定义解析</br> 文法：</br> {@code SingleConstDefinition} -> {@link Parser#isIdentifier(Token) Identifier} "="
   * {@link Parser#expression() Expression}
   *
   * @param parent 常量定义的父节点
   */
  private void singleConstDefinition(TokenTreeView parent) {
    // 创建常量定义节点
    TokenTreeView constDefNode = new TokenTreeView(parent, "常量", NodeType.DEFINITION, "定义", currentToken().getLine(), currentToken().getColumn());
    parent.addChild(constDefNode);

    // 标识符
    if (currentToken().getType() != tokenManager.getType("_IDENTIFIER_")) {
      error(String.format("[r: %d, c: %d]-常量声明缺少标识符", currentToken().getLine(), currentToken().getColumn()));
    }
    String identifierValue = currentToken().getValue();
    TokenTreeView identifierNode = new TokenTreeView(constDefNode, identifierValue, NodeType.IDENTIFIER, "常量名", currentToken().getLine(), currentToken().getColumn());
    constDefNode.addChild(identifierNode);
    consume();

    // 等号
    if (currentToken().getType() != tokenManager.getType("=")) {
      error(String.format("[r: %d, c: %d]-常量定义缺少'='", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView equalsNode = new TokenTreeView(constDefNode, "=", NodeType.OPERATOR, currentToken().getLine(), currentToken().getColumn());
    constDefNode.addChild(equalsNode);
    consume();

    // 常量值（必须有初始值）
    TokenTreeView valueExpr = expression();
    valueExpr.setParent(constDefNode);
    if (valueExpr.getNodeType().equals(NodeType.ERROR)) {
      error(String.format("[r: %d, c: %d]-常量定义缺少常量值", currentToken().getLine(), currentToken().getColumn()));
      valueExpr = new TokenTreeView(constDefNode, "错误", NodeType.ERROR, "常量定义缺少常量值", currentToken().getLine(), currentToken().getColumn());
    } else {
      valueExpr.setDescription("常量值");
    }
    constDefNode.addChild(valueExpr);
  }

  /**
   * 非常量定义/声明总控
   *
   * @return 非常量定义/声明的语法树
   */
  private TokenTreeView declaration() {
    // 预先检测声明类型
    DeclarationType declarationType = detectDeclarationType();

    return switch (declarationType) {
      case FUNCTION_PROTOTYPE -> functionPrototype();
      case FUNCTION_DEFINITION -> functionDefinition();
      case VARIABLE -> variableDefinition();
      default -> {
        error(String.format("[r: %d, c: %d]-未知的声明类型", currentToken().getLine(), currentToken().getColumn()));
        yield new TokenTreeView("未知声明", NodeType.ERROR, currentToken().getLine(), currentToken().getColumn());
      }
    };
  }

  /**
   * 变量定义解析</br> 文法：</br> {@code VariableDefinition} -> {@link Parser#isType(Token) Type}
   * {@link Parser#singleVariableDefinition(TokenTreeView) SingleVariableDefinition} (","
   * {@link Parser#singleVariableDefinition(TokenTreeView) SingleVariableDefinition})* ";"
   *
   * @return 变量定义的语法树
   */
  private TokenTreeView variableDefinition() {
    // 创建一个父节点来包含所有变量定义
    TokenTreeView node = new TokenTreeView("变量定义", NodeType.DEFINITION, "列表", currentToken().getLine(), currentToken().getColumn());

    // 类型
    String typeValue = currentToken().getValue();
    if (currentToken().getType() == tokenManager.getType("void")) {
      error(String.format("[r: %d, c: %d]-变量定义类型不能是 void", currentToken().getLine(), currentToken().getColumn()));
      synchronize();
      return new TokenTreeView("变量定义类型不能是 void", NodeType.ERROR, currentToken().getLine(), currentToken().getColumn());
    }
    TokenTreeView typeNode = new TokenTreeView(node, typeValue, NodeType.TYPE, "变量定义类型", currentToken().getLine(), currentToken().getColumn());
    node.addChild(typeNode);
    consume();

    // 解析第一个变量定义
    singleVariableDefinition(node);

    // 处理多个变量定义（以逗号分隔）
    while (currentToken().getType() == tokenManager.getType(",")) {
      consume();
      // 解析下一个变量定义
      singleVariableDefinition(node);
    }

    // 分号
    if (currentToken().getType() != tokenManager.getType(";")) {
      error(String.format("[r: %d, c: %d]-变量定义后缺少';'", currentToken().getLine(), currentToken().getColumn()));
    } else {
      consume();
    }

    return node;
  }

  /*===============================
           表达式
   *===============================*/

  /**
   * 单个变量定义解析</br> 文法：</br> {@code SingleVariableDefinition} -> {@link Parser#isIdentifier(Token) Identifier} |
   * {@link Parser#isIdentifier(Token) Identifier} "=" {@link Parser#expression() Expression}
   *
   * @param parent 变量定义的父节点
   */
  private void singleVariableDefinition(TokenTreeView parent) {
    // 判断是否有初始化
    boolean hasInitializer = false;

    // 向前看一个token，检查是否有初始化器
    int savedPos = currentPos;
    String identName;

    if (isIdentifier(currentToken())) {
      consume(); // 消费标识符

      // 检查是否有等号（初始化）
      if (currentToken().getType() == tokenManager.getType("=")) {
        hasInitializer = true;
      }
    }

    // 恢复位置
    currentPos = savedPos;

    // 创建适当的节点（是否有初始化）
    TokenTreeView varNode;
    varNode = new TokenTreeView(parent, "变量定义", NodeType.DEFINITION, currentToken().getLine(), currentToken().getColumn());
    if (hasInitializer) {
      varNode.setDescription("init");
    }
    parent.addChild(varNode);

    // 标识符
    if (!isIdentifier(currentToken())) {
      error(String.format("[r: %d, c: %d]-缺少标识符", currentToken().getLine(), currentToken().getColumn()));
    } else {
      identName = currentToken().getValue();
      TokenTreeView idNode = new TokenTreeView(varNode, identName, NodeType.IDENTIFIER, "变量名", currentToken().getLine(), currentToken().getColumn());
      varNode.addChild(idNode);
      consume();
    }

    // 处理初始化（如果有）
    if (hasInitializer) {
      varInit(varNode);
    }
  }

  // 初始化变量
  private void varInit(TokenTreeView varNode) {
    TokenTreeView assignNode = new TokenTreeView(varNode, "=", NodeType.OPERATOR, "赋值操作符", currentToken().getLine(), currentToken().getColumn());
    varNode.addChild(assignNode);
    consume();

    TokenTreeView exprNode = expression();
    exprNode.setParent(varNode);
    if (exprNode.getNodeType().equals(NodeType.ERROR)) {
      error(String.format("[r: %d, c: %d]-赋值操作符'='后缺少表达式", currentToken().getLine(), currentToken().getColumn()));
      exprNode = new TokenTreeView(varNode, "缺少表达式", NodeType.ERROR, "变量初始化表达式缺失", currentToken().getLine(), currentToken().getColumn());
    } else {
      exprNode.setDescription("变量初始值");
    }
    varNode.addChild(exprNode);
  }

  // 完整表达式层次结构
  private TokenTreeView expression() {
    return booleanExpression();
  }

  // 逻辑或表达式
  private TokenTreeView booleanExpression() {
    TokenTreeView node = booleanTerm();
    while (!isEOF() && currentToken().getType() == tokenManager.getType("||")) {
      String opValue = currentToken().getValue();
      TokenTreeView root = new TokenTreeView("逻辑表达式", NodeType.LOGIC_EXPR, "逻辑或运算", currentToken().getLine(), currentToken().getColumn());
      node.setParent(root);
      root.addChild(node);

      TokenTreeView opNode = new TokenTreeView(root, opValue, NodeType.OPERATOR, "逻辑或运算符", currentToken().getLine(), currentToken().getColumn());
      root.addChild(opNode);
      consume();

      TokenTreeView right = booleanTerm();
      right.setParent(root);
      root.addChild(right);

      node = root;
    }
    return node;
  }

  // 逻辑与表达式
  private TokenTreeView booleanTerm() {
    TokenTreeView node = nonBF();
    while (!isEOF() && currentToken().getType() == tokenManager.getType("&&")) {
      String opValue = currentToken().getValue();
      TokenTreeView root = new TokenTreeView("逻辑表达式", NodeType.LOGIC_EXPR, "逻辑与运算", currentToken().getLine(), currentToken().getColumn());
      node.setParent(root);
      root.addChild(node);

      TokenTreeView opNode = new TokenTreeView(root, opValue, NodeType.OPERATOR, "逻辑与运算符", currentToken().getLine(), currentToken().getColumn());
      root.addChild(opNode);
      consume();

      TokenTreeView right = nonBF();
      right.setParent(root);
      root.addChild(right);

      node = root;
    }
    return node;
  }

  // 逻辑非表达式
  private TokenTreeView nonBF() {
    TokenTreeView node = relationalExpression();
    while (!isEOF() && (currentToken().getType() == tokenManager.getType("!"))) {
      String opValue = currentToken().getValue();
      TokenTreeView root = new TokenTreeView("逻辑表达式", NodeType.LOGIC_EXPR, "逻辑非运算", currentToken().getLine(), currentToken().getColumn());
      TokenTreeView opNode = new TokenTreeView(root, opValue, NodeType.OPERATOR, "逻辑非运算符", currentToken().getLine(), currentToken().getColumn());
      root.addChild(opNode);
      root.addChild(node);
      consume();
      node = root;
    }
    return node;
  }

  // 关系表达式
  private TokenTreeView relationalExpression() {
    TokenTreeView node = additionExpression();
    while (!isEOF() && (currentToken().getType() == tokenManager.getType("<") || currentToken().getType() == tokenManager.getType(">") || currentToken().getType() == tokenManager.getType("<=") || currentToken().getType() == tokenManager.getType(">=") || currentToken().getType() == tokenManager.getType("==") || currentToken().getType() == tokenManager.getType("!="))) {
      String opValue = currentToken().getValue();
      TokenTreeView root = new TokenTreeView("关系表达式", NodeType.RELATIONAL_EXPR, "关系比较", currentToken().getLine(), currentToken().getColumn());
      node.setParent(root);
      root.addChild(node);

      TokenTreeView opNode = new TokenTreeView(root, opValue, NodeType.OPERATOR, "关系运算符", currentToken().getLine(), currentToken().getColumn());
      root.addChild(opNode);
      consume();

      TokenTreeView right = additionExpression();
      right.setParent(root);
      root.addChild(right);

      node = root;
    }
    return node;
  }

  // 算术表达式
  private TokenTreeView additionExpression() {
    TokenTreeView node = term();
    while (!isEOF() && (currentToken().getType() == tokenManager.getType("+") || currentToken().getType() == tokenManager.getType("-"))) {
      String opValue = currentToken().getValue();
      TokenTreeView root = new TokenTreeView("加减表达式", NodeType.ADDITION_EXPR, opValue.equals("+") ? "加法运算" : "减法运算", currentToken().getLine(), currentToken().getColumn());
      node.setParent(root);
      root.addChild(node);

      TokenTreeView opNode = new TokenTreeView(root, opValue, NodeType.OPERATOR, opValue.equals("+") ? "加法运算符" : "减法运算符", currentToken().getLine(), currentToken().getColumn());
      root.addChild(opNode);
      consume();

      TokenTreeView node2 = term();
      node2.setParent(root);
      root.addChild(node2);
      node = root;
    }
    return node;
  }

  // 乘除表达式
  private TokenTreeView term() {
    TokenTreeView node = factor();
    while (!isEOF() && (currentToken().getType() == tokenManager.getType("*") || currentToken().getType() == tokenManager.getType("/") || currentToken().getType() == tokenManager.getType("%"))) {
      String opValue = currentToken().getValue();
      int opLine = currentToken().getLine();
      int opCol = currentToken().getColumn();

      // 总是创建表达式根节点，即使缺少右操作数
      TokenTreeView root = new TokenTreeView("乘除表达式", NodeType.MULTIPLICATION_EXPR, currentToken().getLine(), currentToken().getColumn());
      TokenTreeView opNode = new TokenTreeView(root, opValue, NodeType.OPERATOR, currentToken().getLine(), currentToken().getColumn());
      String opType;
      if (opValue.equals("*")) {
        opType = "乘法运算";
        opNode.setDescription("乘法运算符");
      } else if (opValue.equals("/")) {
        opType = "除法运算";
        opNode.setDescription("除法运算符");
      } else { // %
        opType = "取模运算";
        opNode.setDescription("取模运算符");
      }
      root.setDescription(opType);
      node.setParent(root);
      root.addChildren(node, opNode);
      consume();

      // 检查是否缺少第二个操作数
      if (currentToken().getType() == tokenManager.getType(";") || isEOF() || (currentToken().getType() != tokenManager.getType("_INTEGER_") && currentToken().getType() != tokenManager.getType("_FLOAT_") && currentToken().getType() != tokenManager.getType("_IDENTIFIER_") && currentToken().getType() != tokenManager.getType("(") && !currentToken().getValue()
                                                                                                                                                                                                                                                                                                                                                           .equals("True") && !currentToken().getValue()
                                                                                                                                                                                                                                                                                                                                                                                             .equals("False"))) {
        error(String.format("[r: %d, c: %d]-运算符'%s'后缺少操作数", opLine, opCol, opValue));
        TokenTreeView errorNode = new TokenTreeView(root, "缺少操作数", NodeType.ERROR, String.format("表达式不完整：第%d行第%d列的运算符'%s'后缺少操作数", opLine, opCol, opValue), currentToken().getLine(), currentToken().getColumn());
        root.addChild(errorNode);
        // 语法树已正确构建，跳出循环
        node = root;
        break;
      } else {
        TokenTreeView node2 = factor();
        node2.setParent(root);
        root.addChild(node2);
      }
      node = root;
    }
    return node;
  }

  // 因子
  private TokenTreeView factor() {
    TokenTreeView root;
    try {
      // 处理前缀自增/自减
      if (currentToken().getType() == tokenManager.getType("++") || currentToken().getType() == tokenManager.getType("--")) {
        String operator = currentToken().getValue();
        root = new TokenTreeView("前缀表达式", NodeType.UNARY_EXPR, operator.equals("++") ? "前缀自增" : "前缀自减", currentToken().getLine(), currentToken().getColumn());

        TokenTreeView opNode = new TokenTreeView(root, operator, NodeType.OPERATOR, operator.equals("++") ? "自增操作符" : "自减操作符", currentToken().getLine(), currentToken().getColumn());
        root.addChild(opNode);
        consume();

        // 后面必须是标识符
        if (!isIdentifier(currentToken())) {
          error(String.format("[r: %d, c: %d]-前缀%s后必须是标识符", currentToken().getLine(), currentToken().getColumn(), operator));
          // 创建错误节点
          TokenTreeView errorNode = new TokenTreeView(root, "缺少标识符", NodeType.ERROR, "表达式不完整", currentToken().getLine(), currentToken().getColumn());
          root.addChild(errorNode);
        } else {
          String identName = currentToken().getValue();
          TokenTreeView idNode = new TokenTreeView(root, identName, NodeType.IDENTIFIER, "变量名", currentToken().getLine(), currentToken().getColumn());
          root.addChild(idNode);
          consume();
        }

        return root;
      } else if (currentToken().getType() == tokenManager.getType("(")) {
        root = new TokenTreeView("括号表达式", NodeType.PAREN_EXPR, currentToken().getLine(), currentToken().getColumn());

        TokenTreeView left = new TokenTreeView(root, "(", NodeType.SYMBOL, currentToken().getLine(), currentToken().getColumn());
        consume();

        TokenTreeView node = expression();
        node.setParent(root);

        TokenTreeView right;
        if (currentToken().getType() != tokenManager.getType(")")) {
          error(String.format("[r: %d, c: %d]-缺少')'", currentToken().getLine(), currentToken().getColumn()));
          right = new TokenTreeView(root, "缺少)", NodeType.ERROR, "括号不匹配", currentToken().getLine(), currentToken().getColumn());
          // 不再进行consume，让同步机制处理
        } else {
          right = new TokenTreeView(root, ")", NodeType.SYMBOL, currentToken().getLine(), currentToken().getColumn());
          consume();
        }
        root.addChildren(left, node, right);
      } else if (currentToken().getType() == tokenManager.getType("-") || currentToken().getType() == tokenManager.getType("+")) {
        // 处理一元操作符
        String op = currentToken().getValue();
        root = new TokenTreeView("一元表达式", NodeType.UNARY_EXPR, op.equals("+") ? "正号运算" : "负号运算", currentToken().getLine(), currentToken().getColumn());
        TokenTreeView opNode = new TokenTreeView(root, op, NodeType.OPERATOR, op.equals("+") ? "正号运算符" : "负号运算符", currentToken().getLine(), currentToken().getColumn());
        root.addChild(opNode);
        consume();

        // 检查一元操作符后是否有操作数
        if (isEOF() || (!isConst(currentToken()) && !isIdentifier(currentToken()) && currentToken().getType() != tokenManager.getType("("))) {
          // 一元操作符后缺少操作数
          error(String.format("[r: %d, c: %d]-一元操作符'%s'后缺少操作数", currentToken().getLine(), currentToken().getColumn(), op), true);
          TokenTreeView errorNode = new TokenTreeView(root, "缺少操作数", NodeType.ERROR, "表达式不完整", currentToken().getLine(), currentToken().getColumn());
          root.addChild(errorNode);
        } else {
          TokenTreeView operandNode = factor(); // 使用factor而不是expression，避免无限递归
          operandNode.setParent(root);
          root.addChild(operandNode);
        }
      } else if (isConst(currentToken())) {
        String constValue = currentToken().getValue();
        root = new TokenTreeView(constValue, null, "值", currentToken().getLine(), currentToken().getColumn());
        if (currentToken().getType() == tokenManager.getType("_INTEGER_")) {
          root.setNodeType(NodeType.LITERAL_INT);
        } else if (currentToken().getType() == tokenManager.getType("_FLOAT_")) {
          root.setNodeType(NodeType.LITERAL_FLOAT);
        } else if (currentToken().getType() == tokenManager.getType("_CHAR_")) {
          root.setNodeType(NodeType.LITERAL_CHAR);
        } else {
          root.setNodeType(NodeType.LITERAL_BOOL);
        }
        consume();
      } else if (isIdentifier(currentToken())) {
        String identName = currentToken().getValue();
        consume(); // 先消费标识符

        // 检查是否是函数调用（标识符后跟左括号）
        if (!isEOF() && currentToken().getType() == tokenManager.getType("(")) {
          // 创建函数调用节点
          root = new TokenTreeView("函数调用", NodeType.FUNCTION_CALL, currentToken().getLine(), currentToken().getColumn());

          // 添加函数名节点
          TokenTreeView funcNameNode = new TokenTreeView(root, identName, NodeType.IDENTIFIER, "函数名", currentToken().getLine(), currentToken().getColumn());
          root.addChild(funcNameNode);

          consume(); // 消费左括号

          // 处理参数列表
          TokenTreeView argsNode = new TokenTreeView(root, "函数参数", NodeType.PARAM_LIST, "参数列表", currentToken().getLine(), currentToken().getColumn());
          int id = 1; // 参数编号
          while (!isEOF() && currentToken().getType() != tokenManager.getType(")")) {
            if (currentToken().getType() == tokenManager.getType(",")) {
              consume(); // 消费逗号
              if (isEOF() || currentToken().getType() == tokenManager.getType(")")) {
                error(String.format("[r: %d, c: %d]-函数调用参数列表中缺少参数", currentToken().getLine(), currentToken().getColumn()));
                break; // 如果逗号后没有参数，直接跳出循环
              }
            }
            TokenTreeView argNode = new TokenTreeView(argsNode, "参数" + id++, NodeType.PARAM, currentToken().getLine(), currentToken().getColumn());
            argsNode.addChild(argNode);
            TokenTreeView exprNode = expression();
            exprNode.setParent(argNode);
            argNode.addChild(exprNode);
          }
          if (!argsNode.getChildren().isEmpty()) {
            root.addChild(argsNode);
          }
          if (currentToken().getType() != tokenManager.getType(")")) {
            error(String.format("[r: %d, c: %d]-函数调用缺少')'", currentToken().getLine(), currentToken().getColumn()));
            TokenTreeView errorNode = new TokenTreeView(root, "缺少)", NodeType.ERROR, "括号不匹配", currentToken().getLine(), currentToken().getColumn());
            root.addChild(errorNode);
          } else {
            consume(); // 消费右括号
          }
        } else {
          // 普通变量引用
          root = new TokenTreeView(identName, NodeType.IDENTIFIER, "变量名", currentToken().getLine(), currentToken().getColumn());

          // 检查是否有后缀自增/自减
          if (!isEOF() && (currentToken().getType() == tokenManager.getType("++") || currentToken().getType() == tokenManager.getType("--"))) {
            String operator = currentToken().getValue();
            TokenTreeView suffixNode = new TokenTreeView("后缀表达式", NodeType.UNARY_EXPR, operator.equals("++") ? "后缀自增" : "后缀自减", currentToken().getLine(), currentToken().getColumn());

            // 将标识符节点作为子节点
            root.setParent(suffixNode);
            suffixNode.addChild(root);

            // 添加运算符
            TokenTreeView opNode = new TokenTreeView(suffixNode, operator, NodeType.OPERATOR, operator.equals("++") ? "自增操作符" : "自减操作符", currentToken().getLine(), currentToken().getColumn());
            suffixNode.addChild(opNode);
            consume();

            root = suffixNode;
          }
        }
      } else if (isDelimiter(currentToken())) {
        root = new TokenTreeView("语法错误", NodeType.ERROR, "缺少表达式", currentToken().getLine(), currentToken().getColumn());
        error(String.format("[r: %d, c: %d]-缺少表达式", currentToken().getLine(), currentToken().getColumn()), false);
      } else {
        root = new TokenTreeView("语法错误", NodeType.ERROR, "无法识别的表达式", currentToken().getLine(), currentToken().getColumn());
        error(String.format("[r: %d, c: %d]-无法识别的表达式", currentToken().getLine(), currentToken().getColumn()), true);
      }
    } catch (Exception e) {
      root = new TokenTreeView("表达式解析错误", NodeType.ERROR, "解析过程中发生异常", currentToken().getLine(), currentToken().getColumn());
      // synchronize(); // 同步到下一个安全点
    }
    return root;
  }

  // 判断是否是界符
  private boolean isDelimiter(Token token) {
    return token.getType() == tokenManager.getType(";") || token.getType() == tokenManager.getType("{") || token.getType() == tokenManager.getType("}") || token.getType() == tokenManager.getType("(") || token.getType() == tokenManager.getType(")") || token.getType() == tokenManager.getType(",");
  }

  private boolean isIdentifier(Token token) {
    return token.getType() == tokenManager.getType("_IDENTIFIER_");
  }

  private boolean isConst(Token token) {
    return token.getType() == tokenManager.getType("_INTEGER_") || token.getType() == tokenManager.getType("_FLOAT_") || token.getType() == tokenManager.getType("_CHAR_") || token.getValue()
                                                                                                                                                                                   .equals("True") || token.getValue()
                                                                                                                                                                                                           .equals("False");
  }

  /**
   * 预先检测当前语法结构是变量定义还是函数定义
   *
   * @return 检测结果枚举
   */
  private DeclarationType detectDeclarationType() {
    // 保存当前位置以便回溯
    int savedPos = currentPos;

    // 跳过类型
    consume();

    // 检查是否有标识符
    if (!isIdentifier(currentToken())) {
      currentPos = savedPos; // 恢复位置
      return DeclarationType.UNKNOWN;
    }

    // 跳过标识符
    consume();

    // 检查下一个token
    Token nextToken = currentToken();

    // 恢复位置
    currentPos = savedPos;

    // 根据下一个token判断类型
    if (nextToken.getType() == tokenManager.getType("(")) {
      // 如果是左括号，可能是函数声明或定义
      // 进一步判断是函数声明还是函数定义
      int pos = currentPos + 3;
      while (pos < tokens.size() && !tokens.get(pos).getValue().equals(")")) {
        pos++;
      }
      pos++;
      // 看右括号后面的token是否是左大括号从而判断是否是函数定义
      if (pos < tokens.size() && tokens.get(pos).getValue().equals("{")) {
        return DeclarationType.FUNCTION_DEFINITION;
      } else {
        // 否则全当成函数声明
        // 无论后面是缺分号或是没有token了，都认为是函数声明
        // 交给分析进行处理报错，此处仅作预检测
        return DeclarationType.FUNCTION_PROTOTYPE;
      }
    }

    // 默认为变量声明/定义
    return DeclarationType.VARIABLE;
  }

  // 判断是否为main函数
  private boolean isMainFunction() {
    // 首先检查是否有足够的token
    if (isEOF() || currentPos + 3 >= tokens.size()) {
      return false;
    }

    // main函数的模式：void main ( )
    return currentToken().getValue().equals("void") && lookahead(1).getValue()
                                                                   .equals("main") && lookahead(2).getType() == tokenManager.getType("(");
  }

  /**
   * 预览 offset 位后的token
   *
   * @param offset 偏移量
   * @return 预览的token
   */
  private Token lookahead(int offset) {
    int index = currentPos + offset;
    if (index >= tokens.size()) {
      return new Token("", -1, 0, 0);
    }
    return tokens.get(index);
  }

  // 解析函数声明（原型）
  private TokenTreeView functionPrototype() {
    TokenTreeView node = new TokenTreeView("函数", NodeType.DECLARATION, "声明", currentToken().getLine(), currentToken().getColumn());

    // 返回类型
    if (!isType(currentToken())) {
      error(String.format("[r: %d, c: %d]-缺少函数返回类型", currentToken().getLine(), currentToken().getColumn()));
    }
    String typeValue = currentToken().getValue();
    TokenTreeView typeNode = new TokenTreeView(node, typeValue, NodeType.TYPE, "返回值类型", currentToken().getLine(), currentToken().getColumn());
    node.addChild(typeNode);
    consume();

    // 函数名
    if (!isIdentifier(currentToken())) {
      error(String.format("[r: %d, c: %d]-缺少函数名", currentToken().getLine(), currentToken().getColumn()));
    }
    String funcName = currentToken().getValue();
    TokenTreeView funcNameNode = new TokenTreeView(node, funcName, NodeType.IDENTIFIER, "函数名", currentToken().getLine(), currentToken().getColumn());
    node.addChild(funcNameNode);
    consume();

    // 左括号
    if (currentToken().getType() != tokenManager.getType("(")) {
      error(String.format("[r: %d, c: %d]-函数名后缺少'('", currentToken().getLine(), currentToken().getColumn()));
    }
    consume();

    // 参数列表
    if (currentToken().getType() != tokenManager.getType(")")) {
      node.addChild(parameterList(false));
    }

    // 右括号
    if (currentToken().getType() != tokenManager.getType(")")) {
      error(String.format("[r: %d, c: %d]-参数列表后缺少')'", currentToken().getLine(), currentToken().getColumn()));
    }
    consume();

    // 分号
    if (currentToken().getType() != tokenManager.getType(";")) {
      error(String.format("[r: %d, c: %d]-函数声明后缺少';'", currentToken().getLine(), currentToken().getColumn()));
    } else {
      consume();
    }

    return node;
  }

  // 解析函数定义
  private TokenTreeView functionDefinition() {
    TokenTreeView node = new TokenTreeView("函数", NodeType.FUNCTION, "具体实现", currentToken().getLine(), currentToken().getColumn());

    // 返回类型
    if (!isType(currentToken())) {
      error(String.format("[r: %d, c: %d]-缺少函数返回类型", currentToken().getLine(), currentToken().getColumn()));
    }
    String typeValue = currentToken().getValue();
    TokenTreeView typeNode = new TokenTreeView(node, typeValue, NodeType.TYPE, "返回值类型", currentToken().getLine(), currentToken().getColumn());
    node.addChild(typeNode);
    consume();

    // 函数名
    if (!isIdentifier(currentToken())) {
      error(String.format("[r: %d, c: %d]-缺少函数名", currentToken().getLine(), currentToken().getColumn()));
    }
    String funcName = currentToken().getValue();
    TokenTreeView funcNameNode = new TokenTreeView(node, funcName, NodeType.IDENTIFIER, "函数名", currentToken().getLine(), currentToken().getColumn());
    node.addChild(funcNameNode);
    consume();

    // 左括号
    if (currentToken().getType() != tokenManager.getType("(")) {
      error(String.format("[r: %d, c: %d]-函数名后缺少'('", currentToken().getLine(), currentToken().getColumn()));
    }
    consume();

    // 参数列表
    if (currentToken().getType() != tokenManager.getType(")")) {
      node.addChild(parameterList(true));
    }

    // 右括号
    if (currentToken().getType() != tokenManager.getType(")")) {
      error(String.format("[r: %d, c: %d]-参数列表后缺少')'", currentToken().getLine(), currentToken().getColumn()));
    }
    consume();

    // 函数体（代码块）
    node.addChild(block());

    return node;
  }

  // 解析参数列表
  private TokenTreeView parameterList(boolean isDefinition) {
    TokenTreeView node = new TokenTreeView("参数列表", NodeType.PARAM_LIST, currentToken().getLine(), currentToken().getColumn());
    while (!isEOF() && !currentToken().getValue().equals(")")) {
      if (currentToken().getType() == tokenManager.getType(",")) {
        consume(); // 跳过逗号
        if (isEOF() || currentToken().getType() == tokenManager.getType(")")) {
          error(String.format("[r: %d, c: %d]-参数列表中逗号后缺少参数", currentToken().getLine(), currentToken().getColumn()));
          node.addChild(new TokenTreeView("逗号后缺少参数", NodeType.ERROR, "参数列表错误", currentToken().getLine(), currentToken().getColumn()));
          break; // 如果逗号后没有参数，直接跳出循环
        }
      }
      node.addChild(parameter(isDefinition));
    }
    if (currentToken().getType() != tokenManager.getType(")")) {
      error(String.format("[r: %d, c: %d]-参数列表缺少')'", currentToken().getLine(), currentToken().getColumn()));
    } else {
      consume(); // 跳过右括号
    }
    return node;
  }

  // 解析单个参数
  private TokenTreeView parameter(boolean isDefinition) {
    TokenTreeView node = new TokenTreeView("参数", NodeType.PARAM, "函数参数", currentToken().getLine(), currentToken().getColumn());

    // 参数类型
    if (!isType(currentToken()) || currentToken().getValue().equals("void")) {
      error(String.format("[r: %d, c: %d]-缺少参数类型或使用了无效的类型void", currentToken().getLine(), currentToken().getColumn()));
    }
    String typeValue = currentToken().getValue();
    TokenTreeView typeNode = new TokenTreeView(node, typeValue, NodeType.TYPE, "参数类型", currentToken().getLine(), currentToken().getColumn());
    node.addChild(typeNode);
    consume();

    // 参数名
    if (isIdentifier(currentToken())) {
      if (isDefinition) {
        String paramName = currentToken().getValue();
        TokenTreeView paramNameNode = new TokenTreeView(node, paramName, NodeType.IDENTIFIER, "参数名", currentToken().getLine(), currentToken().getColumn());
        node.addChild(paramNameNode);
      } else {
        // 如果是声明且存在参数名，给予警告
        warn(String.format("[r: %d, c: %d]-函数声明无需参数名'%s'", currentToken().getLine(), currentToken().getColumn(), currentToken().getValue()));
      }
      consume();
    } else {
      // 如果是定义且缺少参数名，则报错
      if (isDefinition) {
        error(String.format("[r: %d, c: %d]-缺少参数名", currentToken().getLine(), currentToken().getColumn()));
      }
    }

    return node;
  }

  /**
   * 声明类型枚举
   */
  private enum DeclarationType {
    VARIABLE,           // 变量声明/定义
    FUNCTION_PROTOTYPE, // 函数声明
    FUNCTION_DEFINITION,// 函数定义
    UNKNOWN            // 未知类型
  }

  private enum ErrorProcess {
    SKIP, ERROR, WARN
  }
}