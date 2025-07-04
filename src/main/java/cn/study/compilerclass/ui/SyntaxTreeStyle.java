package cn.study.compilerclass.ui;

import cn.study.compilerclass.model.NodeType;

/**
 * 语法树显示样式
 */
public class SyntaxTreeStyle {

  /**
   * 根据节点类型获取CSS样式类名
   *
   * @param nodeType 节点类型
   * @return 对应的CSS样式类名
   */
  public static String getStyleClass(NodeType nodeType) {
    if (nodeType == null) {
      return "default";
    }

    return switch (nodeType) {
      case ERROR -> "error";
      case PROGRAM -> "root-node";
      case FUNCTION, BLOCK, STATEMENT, ASSIGNMENT_STMT, IF_STMT, WHILE_STMT, DO_WHILE_STMT, RETURN_STMT, EXPRESSION,
           BINARY_EXPR, UNARY_EXPR, PAREN_EXPR, RELATIONAL_EXPR, LOGIC_EXPR, ADDITION_EXPR,
           MULTIPLICATION_EXPR -> "middle-node";
      case DEFINITION, DECLARATION -> "declaration-node";
      case KEYWORD -> "keyword-node";
      case OPERATOR -> "operator-node";
      case LITERAL_BOOL, LITERAL_INT, LITERAL_FLOAT, LITERAL_CHAR -> "value-node";
      case SYMBOL -> "symbol-node";
      case IDENTIFIER, TYPE -> "normal-node";
      case FUNCTION_CALL -> "function-call";
      case PARAM -> "params";
      case PARAM_LIST -> "params-list";
    };
  }
}