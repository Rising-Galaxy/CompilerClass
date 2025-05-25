package cn.study.compilerclass.model;

import lombok.Getter;

/**
 * 语法树节点类型枚举
 */
@Getter
public enum NodeType {
  // 程序结构
  PROGRAM("程序"), FUNCTION("函数"), BLOCK("代码块"),

  // 声明与定义
  DECLARATION("声明"), DEFINITION("定义"),

  // 语句
  STATEMENT("语句"), ASSIGNMENT_STMT("赋值语句"), IF_STMT("条件语句"), WHILE_STMT("while循环语句"), DO_WHILE_STMT("do-while循环语句"), RETURN_STMT("返回语句"),

  // 表达式
  EXPRESSION("表达式"), BINARY_EXPR("二元表达式"), UNARY_EXPR("一元表达式"), PAREN_EXPR("括号表达式"), LOGICAL_EXPR("逻辑表达式"), RELATIONAL_EXPR("关系表达式"), EQUALITY_EXPR("相等性表达式"), ARITHMETIC_EXPR("算术表达式"),

  // 基本元素
  IDENTIFIER("标识符"), TYPE("类型"), VALUE("值"), LITERAL_BOOL("布尔字面量"), LITERAL_INT("整数字面量"), LITERAL_FLOAT("浮点字面量"), LITERAL_STRING("字符串字面量"),

  // 操作符和符号
  OPERATOR("操作符"), SYMBOL("符号"), KEYWORD("关键字"),

  // 函数调用
  FUNCTION_CALL("函数调用"), PARAM_LIST("参数列表"), PARAM("参数"),

  // 错误
  ERROR("错误");

  private final String description;

  NodeType(String description) {
    this.description = description;
  }

  /**
   * 从字符串转换为枚举值
   *
   * @param text 字符串表示的枚举值
   * @return 对应的枚举值，如果没有匹配的则返回null
   */
  public static NodeType fromString(String text) {
    for (NodeType nodeType : NodeType.values()) {
      if (nodeType.description.equalsIgnoreCase(text)) {
        return nodeType;
      }
    }
    return null; // 如果没有找到匹配的枚举值，返回null
  }

  @Override
  public String toString() {
    return description;
  }
}