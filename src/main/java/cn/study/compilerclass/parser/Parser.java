package cn.study.compilerclass.parser;

import cn.study.compilerclass.lexer.Token;
import cn.study.compilerclass.lexer.TokenManager;
import cn.study.compilerclass.syntax.SemanticAnalyzer;
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

  private final TokenManager tokenManager;
  private final OutInfo outInfos;
  private final String src = "语法分析";
  private ErrorProcess errorProcess = ErrorProcess.SKIP;
  private List<Token> tokens;
  private int currentPos;
  private TokenTreeView root;
  private SemanticAnalyzer semanticAnalyzer;

  public Parser(String filePath, OutInfo outInfos) {
    this.tokenManager = new TokenManager();
    this.outInfos = outInfos;
    this.currentPos = 0;
    this.semanticAnalyzer = new SemanticAnalyzer(outInfos);
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

  private boolean isEOF() {
    return currentPos >= tokens.size();
  }

  private void consume() {
    currentPos++;
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
      if (value.equals("if") || value.equals("elif") || value.equals("else") || value.equals("while") || value.equals("do") || value.equals("int") || value.equals("float") || value.equals("bool") || value.equals("void") || value.equals("const") || currentToken().getType() == tokenManager.getType("{") || currentToken().getType() == tokenManager.getType("}")) {
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
    info("开始语法分析...");
    try {
      // 更改入口点为完整程序解析
      root = program();

      // 语法分析成功后，执行语义分析
      // if (root != null) {
      //   info("语法分析完成，开始语义分析...");
      //   semanticAnalyzer.analyze(root);
      // }
    } catch (Exception e) {
      errorProcess = ErrorProcess.SKIP;
      error("分析过程中出现异常", e);
      errorProcess = ErrorProcess.ERROR;
    }
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
          getStyleClass().removeAll("root-node", "middle-node", "type-node", "error", "operator-node", "keyword-node", "value-node", "declaration-node", "symbol-node", "highlight");

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
            if (item.contains("「KEYWORD」")) {
              getStyleClass().add("keyword-node");
            } else if (item.contains("「OPERATOR」")) {
              getStyleClass().add("operator-node");
            } else if (item.contains("「VALUE」")) {
              getStyleClass().add("value-node");
            } else if (item.contains("「DECLARATION」")) {
              getStyleClass().add("declaration-node");
            } else if (item.contains("「SYMBOL」")) {
              getStyleClass().add("symbol-node");
            } else if (item.contains("「ERROR」")) {
              getStyleClass().add("error");
            } else if (item.startsWith("程序")) {
              getStyleClass().add("root-node");
            } else if (item.contains("表达式") || item.contains("函数") || item.contains("语句") || item.contains("声明") || item.contains("块")) {
              getStyleClass().add("middle-node");
            } else if (item.contains("常量") || item.contains("标识符") || item.contains("「TYPE」")) {
              getStyleClass().add("type-node");
            }

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

  // 入口点：程序解析
  private TokenTreeView program() {
    TokenTreeView program = new TokenTreeView(null, "程序", "PROGRAM", "root-node");
    program.setNodeInfo("PROGRAM", "程序入口点");
    program.setFolded(false);

    // 解析全局变量、常量声明和函数定义
    while (!isEOF()) {
      if (isMainFunction()) {
        program.addChild(mainFunction());
      } else if (isConstDeclaration()) {
        program.addChild(constDeclaration());
      } else if (isVariableDeclaration()) {
        program.addChild(variableDeclaration());
      } else if (isFunctionPrototype()) {
        program.addChild(functionPrototype());
      } else if (isFunctionDefinition()) {
        program.addChild(functionDefinition());
      } else {
        error(String.format("[r: %d, c: %d]-无法识别的全局声明", currentToken().getLine(), currentToken().getColumn()));
        // 确保至少消费一个token，避免死循环
        consume();
        synchronize();
      }
    }

    return program;
  }

  // Main函数解析
  private TokenTreeView mainFunction() {
    TokenTreeView node = new TokenTreeView(null, "主函数", "FUNCTION", "middle-node");
    node.setNodeInfo("FUNCTION", "程序入口函数");
    node.highlightNode();
    node.setFolded(false);

    // 返回类型
    if (!isType(currentToken())) {
      error(String.format("[r: %d, c: %d]-缺少主函数返回类型", currentToken().getLine(), currentToken().getColumn()));
    }
    String typeValue = currentToken().getValue();
    TokenTreeView typeNode = new TokenTreeView(node, typeValue, "TYPE", "type-node");
    typeNode.setNodeInfo("TYPE", "返回值类型");
    node.addChild(typeNode);
    consume();

    // main标识符
    if (!currentToken().getValue().equals("main")) {
      error(String.format("[r: %d, c: %d]-缺少'main'函数", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView mainNode = new TokenTreeView(node, "main", "IDENTIFIER", "keyword-node");
    mainNode.setNodeInfo("IDENTIFIER", "函数名");
    node.addChild(mainNode);
    consume();

    // 左括号
    if (currentToken().getType() != tokenManager.getType("(")) {
      error(String.format("[r: %d, c: %d]-main后缺少'('", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView openParenNode = new TokenTreeView(node, "(", "SYMBOL", "symbol-node");
    node.addChild(openParenNode);
    consume();

    // 右括号
    if (currentToken().getType() != tokenManager.getType(")")) {
      error(String.format("[r: %d, c: %d]-参数后缺少')'", currentToken().getLine(), currentToken().getColumn()));
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

    if (currentToken().getType() != tokenManager.getType("{")) {
      error("缺少 '{'");
    }
    consume();

    while (!isEOF() && currentToken().getType() != tokenManager.getType("}")) {
      if (isVariableDeclaration()) {
        node.addChild(variableDeclaration());
      } else {
        node.addChild(statement());
      }
    }

    if (currentToken().getType() == tokenManager.getType("}")) {
      consume();
    } else {
      error("缺少 '}'");
    }

    return node;
  }

  // 语句解析
  private TokenTreeView statement() {
    try {
      TokenTreeView tmp;
      if (isVariableDeclaration()) {
        tmp = variableDeclaration();
      } else if (isConstDeclaration()) {
        tmp = constDeclaration();
      } else if (isIfStatement()) {
        tmp = ifStatement();
      } else if (isWhileStatement()) {
        tmp = whileStatement();
      } else if (isDoWhileStatement()) {
        tmp = doWhileStatement();
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
          TokenTreeView semicolonNode = new TokenTreeView(expr, ";", "SYMBOL", "symbol-node");
          expr.addChild(semicolonNode);
          consume();
        }
        tmp = expr;
      }
      return tmp;
    } catch (Exception e) {
      // 语句解析出错，尝试恢复到下一个有效位置
      TokenTreeView errorNode = new TokenTreeView(null, "语法错误", "ERROR", "error");
      errorNode.setNodeInfo("ERROR", "解析过程中发生错误");
      synchronize();
      return errorNode;
    }
  }

  // 控制结构节点的通用方法
  private TokenTreeView createControlStructureNode(String displayText, String nodeType, String nodeInfo, String keyword, boolean requiresCondition, boolean requiresBlock) {
    TokenTreeView node = new TokenTreeView(null, displayText, nodeType, "middle-node");
    node.setNodeInfo(nodeType, nodeInfo);

    // 添加关键字节点
    TokenTreeView keywordNode = new TokenTreeView(node, keyword, "KEYWORD", "keyword-node");
    keywordNode.setNodeInfo("KEYWORD", keyword + "关键字");
    node.addChild(keywordNode);
    consume();

    // 处理条件表达式（如果需要）
    if (requiresCondition) {
      // 左括号
      if (currentToken().getType() != tokenManager.getType("(")) {
        error(String.format("[r: %d, c: %d]-'%s'后缺少'('", currentToken().getLine(), currentToken().getColumn(), keyword));
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
        error(String.format("[r: %d, c: %d]-条件后缺少')'", currentToken().getLine(), currentToken().getColumn()));
      } else {
        TokenTreeView closeParenNode = new TokenTreeView(node, ")", "SYMBOL", "symbol-node");
        node.addChild(closeParenNode);
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
      body.setNodeInfo("STATEMENT", keyword + "语句体");
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
    return createControlStructureNode("循环语句", "STATEMENT", "while循环结构", "while", true, true);
  }

  // 判断是否为do-while语句
  private boolean isDoWhileStatement() {
    return currentToken().getValue().equals("do");
  }

  // 解析do-while语句
  private TokenTreeView doWhileStatement() {
    TokenTreeView node = new TokenTreeView(null, "循环语句", "STATEMENT", "middle-node");
    node.setNodeInfo("STATEMENT", "do-while循环结构");

    // do关键字
    TokenTreeView doNode = new TokenTreeView(node, "do", "KEYWORD", "keyword-node");
    doNode.setNodeInfo("KEYWORD", "do循环关键字");
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
    loopBody.setNodeInfo("STATEMENT", "循环体");
    node.addChild(loopBody);

    // while部分可以使用公共方法的部分逻辑
    if (!currentToken().getValue().equals("while")) {
      error(String.format("[r: %d, c: %d]-'do'后缺少'while'", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView whileNode = new TokenTreeView(node, "while", "KEYWORD", "keyword-node");
    whileNode.setNodeInfo("KEYWORD", "while循环关键字");
    node.addChild(whileNode);
    consume();

    // 左括号
    if (currentToken().getType() != tokenManager.getType("(")) {
      error(String.format("[r: %d, c: %d]-'while'后缺少'('", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView openParenNode = new TokenTreeView(node, "(", "SYMBOL", "symbol-node");
    node.addChild(openParenNode);
    consume();

    // 条件表达式
    TokenTreeView condition = expression();
    condition.setParent(node);
    condition.setNodeInfo("EXPRESSION", "循环条件表达式");
    node.addChild(condition);

    // 右括号
    if (currentToken().getType() != tokenManager.getType(")")) {
      error(String.format("[r: %d, c: %d]-条件后缺少')'", currentToken().getLine(), currentToken().getColumn()));
    } else {
      TokenTreeView closeParenNode = new TokenTreeView(node, ")", "SYMBOL", "symbol-node");
      node.addChild(closeParenNode);
      consume();
    }

    // 分号
    if (currentToken().getType() != tokenManager.getType(";")) {
      error(String.format("[r: %d, c: %d]-do-while语句后缺少';'", currentToken().getLine(), currentToken().getColumn()));
    } else {
      TokenTreeView semicolonNode = new TokenTreeView(node, ";", "SYMBOL", "symbol-node");
      node.addChild(semicolonNode);
      consume();
    }

    return node;
  }

  // 变量声明解析
  private boolean isVariableDeclaration() {
    return isType(currentToken()) && !currentToken().getValue().equals("void");
  }

  private TokenTreeView variableDeclaration() {
    // 创建一个父节点来包含所有变量声明
    TokenTreeView node = new TokenTreeView(null, "变量声明列表", "DECLARATION_LIST", "declaration-node");
    node.setNodeInfo("DECLARATION_LIST", "变量声明列表");

    // 类型（所有变量共享同一类型）
    String typeValue = currentToken().getValue();
    TokenTreeView typeNode = new TokenTreeView(node, typeValue, "TYPE", "type-node");
    node.addChild(typeNode);
    consume();

    // 解析第一个变量声明/定义
    parseVariableDeclarationOrDefinition(node, typeValue);

    // 处理多个变量声明/定义（以逗号分隔）
    while (currentToken().getType() == tokenManager.getType(",")) {
      // 添加逗号节点
      TokenTreeView commaNode = new TokenTreeView(node, ",", "SYMBOL", "symbol-node");
      node.addChild(commaNode);
      consume();

      // 解析下一个变量声明/定义
      parseVariableDeclarationOrDefinition(node, typeValue);
    }

    // 分号
    if (currentToken().getType() != tokenManager.getType(";")) {
      error(String.format("[r: %d, c: %d]-变量声明后缺少';'", currentToken().getLine(), currentToken().getColumn()));
    } else {
      TokenTreeView semicolonNode = new TokenTreeView(node, ";", "SYMBOL", "symbol-node");
      node.addChild(semicolonNode);
      consume();
    }

    return node;
  }

  // 解析单个变量的声明或定义
  private void parseVariableDeclarationOrDefinition(TokenTreeView parent, String typeValue) {
    // 判断是声明还是定义
    boolean hasInitializer = false;

    // 向前看一个token，检查是否有初始化器
    int savedPos = currentPos;
    String identName = "";

    if (isIdentifier(currentToken())) {
      identName = currentToken().getValue();
      consume(); // 消费标识符

      // 检查是否有等号（初始化）
      if (currentToken().getType() == tokenManager.getType("=")) {
        hasInitializer = true;
      }
    }

    // 恢复位置
    currentPos = savedPos;

    // 创建适当的节点（声明或定义）
    TokenTreeView varNode;
    if (hasInitializer) {
      varNode = new TokenTreeView(parent, "变量定义", "DEFINITION", "declaration-node");
      varNode.setNodeInfo("DEFINITION", "变量定义（带初始值）");
    } else {
      varNode = new TokenTreeView(parent, "变量声明", "DECLARATION", "declaration-node");
      varNode.setNodeInfo("DECLARATION", "变量声明（无初始值）");
    }
    parent.addChild(varNode);

    // 添加类型信息
    TokenTreeView typeNode = new TokenTreeView(varNode, typeValue, "TYPE", "type-node");
    varNode.addChild(typeNode);

    // 标识符
    if (!isIdentifier(currentToken())) {
      error(String.format("[r: %d, c: %d]-缺少标识符", currentToken().getLine(), currentToken().getColumn()));
    } else {
      identName = currentToken().getValue();
      TokenTreeView idNode = new TokenTreeView(varNode, identName, "IDENTIFIER", "type-node");
      idNode.setNodeInfo("IDENTIFIER", "变量名");
      varNode.addChild(idNode);
      consume();
    }

    // 处理初始化（如果有）
    if (currentToken().getType() == tokenManager.getType("=")) {
      varInit(varNode);
    }
  }

  private void varInit(TokenTreeView varNode) {
    TokenTreeView assignNode = new TokenTreeView(varNode, "=", "OPERATOR", "operator-node");
    assignNode.setNodeInfo("OPERATOR", "赋值操作符");
    varNode.addChild(assignNode);
    consume();

    TokenTreeView exprNode = expression();
    exprNode.setParent(varNode);
    varNode.addChild(exprNode);
  }

  // 常量声明解析
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
      error(String.format("[r: %d, c: %d]-const后缺少类型", currentToken().getLine(), currentToken().getColumn()));
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
      error(String.format("[r: %d, c: %d]-常量必须进行初始化", currentToken().getLine(), currentToken().getColumn()));
    }
    varInit(node);

    // 分号
    if (currentToken().getType() != tokenManager.getType(";")) {
      error(String.format("[r: %d, c: %d]-Expected ';'", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView semicolonNode = new TokenTreeView(node, ";", "SYMBOL", "symbol-node");
    node.addChild(semicolonNode);
    consume();

    return node;
  }

  // 判断是否为if语句
  private boolean isIfStatement() {
    return currentToken().getValue().equals("if");
  }

  private Token currentToken() {
    if (currentPos >= tokens.size()) {
      return new Token("", -1, 0, 0);
    }
    return tokens.get(currentPos);
  }

  // 解析if语句
  private TokenTreeView ifStatement() {
    TokenTreeView node = new TokenTreeView(null, "条件语句", "STATEMENT", "middle-node");
    TokenTreeView ifNode = createControlStructureNode("条件分支", "STATEMENT", "if条件分支", "if", true, true);
    ifNode.setParent(node);
    node.addChild(ifNode);

    // 处理elif和else部分
    while (!isEOF()) {
      if (currentToken().getValue().equals("elif")) {
        TokenTreeView elifNode = createControlStructureNode("条件分支", "STATEMENT", "elif条件分支", "elif", true, true);
        elifNode.setParent(node);
        node.addChild(elifNode);
      } else if (currentToken().getValue().equals("else")) {
        TokenTreeView elseNode = createControlStructureNode("条件分支", "STATEMENT", "else条件分支", "else", false, true);
        elseNode.setParent(node);
        node.addChild(elseNode);
        break; // else是最后一个分支
      } else {
        break; // 不是elif或else，结束if语句解析
      }
    }

    return node;
  }

  // 赋值表达式解析
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
    TokenTreeView node = new TokenTreeView(null, "赋值语句", "STATEMENT", "middle-node");
    node.setNodeInfo("STATEMENT", "变量赋值");

    // 标识符
    String identName = currentToken().getValue();
    TokenTreeView idNode = new TokenTreeView(node, identName, "IDENTIFIER", "type-node");
    idNode.setNodeInfo("IDENTIFIER", "被赋值的变量");
    node.addChild(idNode);
    consume();

    // 赋值操作符
    String operator = currentToken().getValue();
    TokenTreeView assignNode = new TokenTreeView(node, operator, "OPERATOR", "operator-node");

    // 根据不同的赋值运算符设置不同的描述
    switch (operator) {
      case "=" -> assignNode.setNodeInfo("OPERATOR", "赋值操作符");
      case "+=" -> assignNode.setNodeInfo("OPERATOR", "加法赋值操作符");
      case "-=" -> assignNode.setNodeInfo("OPERATOR", "减法赋值操作符");
      case "*=" -> assignNode.setNodeInfo("OPERATOR", "乘法赋值操作符");
      case "/=" -> assignNode.setNodeInfo("OPERATOR", "除法赋值操作符");
      case "%=" -> assignNode.setNodeInfo("OPERATOR", "取模赋值操作符");
    }

    node.addChild(assignNode);
    consume();

    // 表达式
    TokenTreeView exprNode = expression();
    exprNode.setParent(node);
    exprNode.setNodeInfo("EXPRESSION", "表达式值");
    node.addChild(exprNode);

    // 分号
    if (currentToken().getType() != tokenManager.getType(";")) {
      error(String.format("[r: %d, c: %d]-赋值后缺少';'", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView semicolonNode = new TokenTreeView(node, ";", "SYMBOL", "symbol-node");
    node.addChild(semicolonNode);
    consume();

    return node;
  }

  // 完整表达式层次结构
  private TokenTreeView expression() {
    return booleanExpression();
  }

  // 解析布尔表达式
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
    while (!isEOF() && (currentToken().getType() == tokenManager.getType("<") || currentToken().getType() == tokenManager.getType(">") || currentToken().getType() == tokenManager.getType("<=") || currentToken().getType() == tokenManager.getType(">="))) {
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

  // 算术表达式
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
    while (!isEOF() && (currentToken().getType() == tokenManager.getType("*") || currentToken().getType() == tokenManager.getType("/") || currentToken().getType() == tokenManager.getType("%"))) {
      String opValue = currentToken().getValue();
      int opLine = currentToken().getLine();
      int opCol = currentToken().getColumn();

      // 总是创建表达式根节点，即使缺少右操作数
      TokenTreeView root = new TokenTreeView(null, "乘除表达式", "EXPRESSION", "middle-node");
      String opType;
      if (opValue.equals("*")) {
        opType = "乘法运算";
      } else if (opValue.equals("/")) {
        opType = "除法运算";
      } else { // %
        opType = "取模运算";
      }
      root.setNodeInfo("EXPRESSION", opType);
      node.setParent(root);
      root.addChild(node);

      TokenTreeView opNode = new TokenTreeView(root, opValue, "OPERATOR", "operator-node");
      if (opValue.equals("*")) {
        opNode.setNodeInfo("OPERATOR", "乘法运算符");
      } else if (opValue.equals("/")) {
        opNode.setNodeInfo("OPERATOR", "除法运算符");
      } else { // %
        opNode.setNodeInfo("OPERATOR", "取模运算符");
      }
      root.addChild(opNode);
      consume();

      // 检查是否缺少第二个操作数
      if (currentToken().getType() == tokenManager.getType(";") || isEOF() || (currentToken().getType() != tokenManager.getType("_INTEGER_") && currentToken().getType() != tokenManager.getType("_FLOAT_") && currentToken().getType() != tokenManager.getType("_IDENTIFIER_") && currentToken().getType() != tokenManager.getType("(") && !currentToken().getValue()
                                                                                                                                                                                                                                                                                                                                                           .equals("True") && !currentToken().getValue()
                                                                                                                                                                                                                                                                                                                                                                                             .equals("False"))) {
        error(String.format("[r: %d, c: %d]-运算符'%s'后缺少操作数", opLine, opCol, opValue));
        TokenTreeView errorNode = new TokenTreeView(root, "缺少操作数", "ERROR", "error");
        errorNode.setNodeInfo("ERROR", String.format("表达式不完整：第%d行第%d列的运算符'%s'后缺少操作数", opLine, opCol, opValue));
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

  private TokenTreeView factor() {
    TokenTreeView root;
    try {
      // 处理前缀自增/自减
      if (currentToken().getType() == tokenManager.getType("++") || currentToken().getType() == tokenManager.getType("--")) {
        String operator = currentToken().getValue();
        root = new TokenTreeView(null, "前缀表达式", "EXPRESSION", "middle-node");
        root.setNodeInfo("EXPRESSION", operator.equals("++") ? "前缀自增" : "前缀自减");

        TokenTreeView opNode = new TokenTreeView(root, operator, "OPERATOR", "operator-node");
        opNode.setNodeInfo("OPERATOR", operator.equals("++") ? "自增操作符" : "自减操作符");
        root.addChild(opNode);
        consume();

        // 后面必须是标识符
        if (!isIdentifier(currentToken())) {
          error(String.format("[r: %d, c: %d]-前缀%s后必须是标识符", currentToken().getLine(), currentToken().getColumn(), operator));
          // 创建错误节点
          TokenTreeView errorNode = new TokenTreeView(root, "缺少标识符", "ERROR", "error");
          errorNode.setNodeInfo("ERROR", "表达式不完整");
          root.addChild(errorNode);
        } else {
          String identName = currentToken().getValue();
          TokenTreeView idNode = new TokenTreeView(root, identName, "IDENTIFIER", "type-node");
          idNode.setNodeInfo("IDENTIFIER", "变量名");
          root.addChild(idNode);
          consume();
        }

        return root;
      } else if (currentToken().getType() == tokenManager.getType("(")) {
        root = new TokenTreeView(null, "括号表达式", "EXPRESSION", "middle-node");
        root.setNodeInfo("EXPRESSION", "括号内表达式");

        TokenTreeView left = new TokenTreeView(root, "(", "SYMBOL", "symbol-node");
        consume();

        TokenTreeView node = expression();
        node.setParent(root);

        TokenTreeView right;
        if (currentToken().getType() != tokenManager.getType(")")) {
          error(String.format("[r: %d, c: %d]-缺少')'", currentToken().getLine(), currentToken().getColumn()));
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
        if (isEOF() || (!isConst(currentToken()) && !isIdentifier(currentToken()) && currentToken().getType() != tokenManager.getType("("))) {
          // 一元操作符后缺少操作数
          error(String.format("[r: %d, c: %d]-一元操作符'%s'后缺少操作数", currentToken().getLine(), currentToken().getColumn(), op), true);
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

        // 检查是否有后缀自增/自减
        if (!isEOF() && (currentToken().getType() == tokenManager.getType("++") || currentToken().getType() == tokenManager.getType("--"))) {
          String operator = currentToken().getValue();
          TokenTreeView postfixNode = new TokenTreeView(null, "后缀表达式", "EXPRESSION", "middle-node");
          postfixNode.setNodeInfo("EXPRESSION", operator.equals("++") ? "后缀自增" : "后缀自减");

          // 将标识符节点作为子节点
          root.setParent(postfixNode);
          postfixNode.addChild(root);

          // 添加运算符
          TokenTreeView opNode = new TokenTreeView(postfixNode, operator, "OPERATOR", "operator-node");
          opNode.setNodeInfo("OPERATOR", operator.equals("++") ? "自增操作符" : "自减操作符");
          postfixNode.addChild(opNode);
          consume();

          root = postfixNode;
        }
      } else {
        root = new TokenTreeView(null, "语法错误", "ERROR", "error");
        root.setNodeInfo("ERROR", "无法识别的表达式");
        error(String.format("[r: %d, c: %d]-无法识别的表达式", currentToken().getLine(), currentToken().getColumn()), true);
      }
    } catch (Exception e) {
      root = new TokenTreeView(null, "表达式解析错误", "ERROR", "error");
      root.setNodeInfo("ERROR", "解析过程中发生异常");
      synchronize(); // 同步到下一个安全点
    }
    return root;
  }

  private boolean isIdentifier(Token token) {
    return token.getType() == tokenManager.getType("_IDENTIFIER_");
  }

  private boolean isConst(Token token) {
    return token.getType() == tokenManager.getType("_INTEGER_") || token.getType() == tokenManager.getType("_FLOAT_") || token.getValue()
                                                                                                                              .equals("true") || token.getValue()
                                                                                                                                                      .equals("false") || token.getValue()
                                                                                                                                                                               .equals("True") || token.getValue()
                                                                                                                                                                                                       .equals("False");
  }

  // 判断是否为函数声明（原型）
  private boolean isFunctionPrototype() {
    if (isNotFunctionDeclarationStart()) {
      return false;
    }

    int pos = currentPos + 3;
    int parenCount = 1;

    while (pos < tokens.size() && parenCount > 0) {
      Token t = tokens.get(pos++);
      if (t.getType() == tokenManager.getType("(")) parenCount++;
      else if (t.getType() == tokenManager.getType(")")) parenCount--;
    }

    return pos < tokens.size() && tokens.get(pos).getType() == tokenManager.getType(";");
  }

  // 辅助方法
  private boolean isType(Token token) {
    String value = token.getValue();
    return value.equals("int") || value.equals("float") || value.equals("bool") || value.equals("void");
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

  // 判断是否为函数定义
  private boolean isFunctionDefinition() {
    if (isNotFunctionDeclarationStart()) {
      return false;
    }

    int pos = currentPos + 3;
    int parenCount = 1;

    while (pos < tokens.size() && parenCount > 0) {
      Token t = tokens.get(pos++);
      if (t.getType() == tokenManager.getType("(")) parenCount++;
      else if (t.getType() == tokenManager.getType(")")) parenCount--;
    }

    while (pos < tokens.size() && !tokens.get(pos).getValue().equals("{")) {
      pos++;
    }

    return pos < tokens.size() && tokens.get(pos).getValue().equals("{");
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

  private boolean isNotFunctionDeclarationStart() {
    if (isEOF() || currentPos + 2 >= tokens.size()) {
      return true;
    }
    return !isType(currentToken()) || lookahead(1).getType() != tokenManager.getType("_IDENTIFIER_") || lookahead(2).getType() != tokenManager.getType("(");
  }

  // 解析函数声明（原型）
  private TokenTreeView functionPrototype() {
    TokenTreeView node = new TokenTreeView(null, "函数声明", "FUNCTION_PROTOTYPE", "middle-node");
    node.setNodeInfo("FUNCTION_PROTOTYPE", "函数原型声明");

    // 返回类型
    if (!isType(currentToken())) {
      error(String.format("[r: %d, c: %d]-缺少函数返回类型", currentToken().getLine(), currentToken().getColumn()));
    }
    String typeValue = currentToken().getValue();
    TokenTreeView typeNode = new TokenTreeView(node, typeValue, "TYPE", "type-node");
    typeNode.setNodeInfo("TYPE", "返回值类型");
    node.addChild(typeNode);
    consume();

    // 函数名
    if (!isIdentifier(currentToken())) {
      error(String.format("[r: %d, c: %d]-缺少函数名", currentToken().getLine(), currentToken().getColumn()));
    }
    String funcName = currentToken().getValue();
    TokenTreeView funcNameNode = new TokenTreeView(node, funcName, "IDENTIFIER", "type-node");
    funcNameNode.setNodeInfo("IDENTIFIER", "函数名");
    node.addChild(funcNameNode);
    consume();

    // 左括号
    if (currentToken().getType() != tokenManager.getType("(")) {
      error(String.format("[r: %d, c: %d]-函数名后缺少'('", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView openParenNode = new TokenTreeView(node, "(", "SYMBOL", "symbol-node");
    node.addChild(openParenNode);
    consume();

    // 参数列表
    if (currentToken().getType() != tokenManager.getType(")")) {
      node.addChild(parameterList());
    }

    // 右括号
    if (currentToken().getType() != tokenManager.getType(")")) {
      error(String.format("[r: %d, c: %d]-参数列表后缺少')'", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView closeParenNode = new TokenTreeView(node, ")", "SYMBOL", "symbol-node");
    node.addChild(closeParenNode);
    consume();

    // 分号
    if (currentToken().getType() != tokenManager.getType(";")) {
      error(String.format("[r: %d, c: %d]-函数声明后缺少';'", currentToken().getLine(), currentToken().getColumn()));
    } else {
      TokenTreeView semicolonNode = new TokenTreeView(node, ";", "SYMBOL", "symbol-node");
      node.addChild(semicolonNode);
      consume();
    }

    return node;
  }

  // 解析函数定义
  private TokenTreeView functionDefinition() {
    TokenTreeView node = new TokenTreeView(null, "函数定义", "FUNCTION_DEFINITION", "middle-node");
    node.setNodeInfo("FUNCTION_DEFINITION", "函数实现");

    // 返回类型
    if (!isType(currentToken())) {
      error(String.format("[r: %d, c: %d]-缺少函数返回类型", currentToken().getLine(), currentToken().getColumn()));
    }
    String typeValue = currentToken().getValue();
    TokenTreeView typeNode = new TokenTreeView(node, typeValue, "TYPE", "type-node");
    typeNode.setNodeInfo("TYPE", "返回值类型");
    node.addChild(typeNode);
    consume();

    // 函数名
    if (!isIdentifier(currentToken())) {
      error(String.format("[r: %d, c: %d]-缺少函数名", currentToken().getLine(), currentToken().getColumn()));
    }
    String funcName = currentToken().getValue();
    TokenTreeView funcNameNode = new TokenTreeView(node, funcName, "IDENTIFIER", "type-node");
    funcNameNode.setNodeInfo("IDENTIFIER", "函数名");
    node.addChild(funcNameNode);
    consume();

    // 左括号
    if (currentToken().getType() != tokenManager.getType("(")) {
      error(String.format("[r: %d, c: %d]-函数名后缺少'('", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView openParenNode = new TokenTreeView(node, "(", "SYMBOL", "symbol-node");
    node.addChild(openParenNode);
    consume();

    // 参数列表
    if (currentToken().getType() != tokenManager.getType(")")) {
      node.addChild(parameterList());
    }

    // 右括号
    if (currentToken().getType() != tokenManager.getType(")")) {
      error(String.format("[r: %d, c: %d]-参数列表后缺少')'", currentToken().getLine(), currentToken().getColumn()));
    }
    TokenTreeView closeParenNode = new TokenTreeView(node, ")", "SYMBOL", "symbol-node");
    node.addChild(closeParenNode);
    consume();

    // 函数体（代码块）
    node.addChild(block());

    return node;
  }

  // 解析参数列表
  private TokenTreeView parameterList() {
    TokenTreeView node = new TokenTreeView(null, "参数列表", "PARAMETERS", "middle-node");
    while (!isEOF() && !currentToken().getValue().equals(")")) {
      node.addChild(parameter());
      if (currentToken().getType() == tokenManager.getType(",")) {
        consume(); // 跳过逗号
      }
    }
    return node;
  }

  // 解析单个参数
  private TokenTreeView parameter() {
    TokenTreeView node = new TokenTreeView(null, "参数", "PARAMETER", "middle-node");
    node.setNodeInfo("PARAMETER", "函数参数");

    // 参数类型
    if (!isType(currentToken()) || currentToken().getValue().equals("void")) {
      error(String.format("[r: %d, c: %d]-缺少参数类型或使用了无效的类型void", currentToken().getLine(), currentToken().getColumn()));
    }
    String typeValue = currentToken().getValue();
    TokenTreeView typeNode = new TokenTreeView(node, typeValue, "TYPE", "type-node");
    typeNode.setNodeInfo("TYPE", "参数类型");
    node.addChild(typeNode);
    consume();

    // 参数名
    if (!isIdentifier(currentToken())) {
      error(String.format("[r: %d, c: %d]-缺少参数名", currentToken().getLine(), currentToken().getColumn()));
    }
    String paramName = currentToken().getValue();
    TokenTreeView paramNameNode = new TokenTreeView(node, paramName, "IDENTIFIER", "type-node");
    paramNameNode.setNodeInfo("IDENTIFIER", "参数名");
    node.addChild(paramNameNode);
    consume();

    return node;
  }

  private enum ErrorProcess {
    SKIP, ERROR, WARN
  }
}
