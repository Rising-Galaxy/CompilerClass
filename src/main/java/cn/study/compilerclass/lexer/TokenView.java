package cn.study.compilerclass.lexer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder
@AllArgsConstructor
public class TokenView {

  private Integer index;
  private String value;
  private Integer type;
  private String pos;
  private String meaning;

}
