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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;

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
    outInfos.error(src, msg);
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
      root = expression();
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
  }

  private TreeItem<String> convertToTreeItem(TokenTreeView root) {
    TreeItem<String> treeItem = new TreeItem<>(root.getValue());
    treeItem.setExpanded(true);
    for (TokenTreeView child : root.getChildren()) {
      treeItem.getChildren().add(convertToTreeItem(child));
    }
    return treeItem;
  }

  private TokenTreeView expression() {
    TokenTreeView node = term();
    while (currentToken().getType() == tokenManager.getType("+") || currentToken().getType() == tokenManager.getType("-")) {
      TokenTreeView root = new TokenTreeView(null, currentToken().getValue());
      node.setParent(root);
      root.addChild(node);
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
    while (currentToken().getType() == tokenManager.getType("*") || currentToken().getType() == tokenManager.getType("/")) {
      TokenTreeView root = new TokenTreeView(null, currentToken().getValue());
      node.setParent(root);
      root.addChild(node);
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
    if (currentToken().getType() == tokenManager.getType("(")) {
      root = new TokenTreeView(null, "Expression");
      root.setStyle("middle-node");
      TokenTreeView left = new TokenTreeView(root, currentToken().getValue());
      consume();
      TokenTreeView node = expression();
      node.setParent(root);
      TokenTreeView right;
      if (currentToken().getType() != tokenManager.getType(")")) {
        error(String.format("[r: %d, c: %d]-缺少 ')'", currentToken().getLine(), currentToken().getColumn()));
        right = new TokenTreeView(root, "[ERROR]-缺少 ')'");
        right.setStyle("error");
      } else {
        right = new TokenTreeView(root, currentToken().getValue());
      }
      root.addChildren(left, node, right);
      consume();
    } else if (currentToken().getType() == tokenManager.getType("-")) {
      root = new TokenTreeView(null, "Expression");
      root.setStyle("middle-node");
      TokenTreeView left = new TokenTreeView(root, currentToken().getValue());
      consume();
      TokenTreeView right = expression();
      right.setParent(root);
      root.addChildren(left, right);
    } else if (isConst(currentToken())) {
      root = new TokenTreeView(null, "Const");
      root.setStyle("type-node");
      TokenTreeView node = new TokenTreeView(root, currentToken().getValue());
      root.addChild(node);
      consume();
    } else if (isIdentifier(currentToken())) {
      root = new TokenTreeView(null, "Identifier");
      root.setStyle("type-node");
      TokenTreeView node = new TokenTreeView(root, currentToken().getValue());
      root.addChild(node);
      consume();
    } else {
      root = new TokenTreeView(null, "[ERROR]-语法错误");
      root.setStyle("error");
      error(String.format("[r: %d, c: %d]-语法错误", currentToken().getLine(), currentToken().getColumn()));
    }
    return root;
  }

  private boolean isIdentifier(Token token) {
    return token.getType() == tokenManager.getType("_IDENTIFIER_");
  }

  private boolean isConst(Token token) {
    return token.getType() == tokenManager.getType("_INTEGER_") || token.getType() == tokenManager.getType("_FLOAT_");
  }

  private Token currentToken() {
    if (currentPos >= tokens.size()) {
      return new Token("", -1, 0, 0);
    }
    return tokens.get(currentPos);
  }

  private void consume() {
    currentPos++;
  }

  private enum ErrorProcess {
    SKIP, ERROR, WARN
  }
}