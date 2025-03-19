package cn.study.compilerclass.lexer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder
@AllArgsConstructor
public class TokenView {

  private int index;
  private String value;
  private int type;
  private String pos;

}
