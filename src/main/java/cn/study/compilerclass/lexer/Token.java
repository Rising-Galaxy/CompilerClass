package cn.study.compilerclass.lexer;

import lombok.Getter;

/**
 * 表示词法单元的类
 */
@Getter
public class Token {

  private final String value;  // token的值
  private final int type;      // token的种别码
  private final int line;      // token所在行号
  private final int column;    // token所在列号

  public Token(String value, int type, int line, int column) {
    this.value = value;
    this.type = type;
    this.line = line;
    this.column = column;
  }

  @Override
  public String toString() {
    return String.format("<'%s', %d> at line %d, column %d", value, type, line, column);
  }
}