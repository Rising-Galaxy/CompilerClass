package cn.study.compilerclass.lexer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 表示词法单元的类
 */
@Getter
@AllArgsConstructor
@Builder
public class Token {

  private String value;  // token的值
  private int type;      // token的种别码
  private int line;      // token所在行号
  private int column;    // token所在列号

  @Override
  public String toString() {
    return String.format("<'%s', %d> at line %d, column %d", value, type, line, column);
  }

  public TokenView toView(int index) {
    return TokenView.builder().index(index).value(value).type(type).pos("<" + line + ", " + column + ">").build();
  }
}