package cn.study.compilerclass.parser;

import java.util.ArrayList;
import java.util.Arrays;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class TokenTreeView {

  private TokenTreeView parent;
  private final String value;
  private final ArrayList<TokenTreeView> children;
  private String style;

  public TokenTreeView(TokenTreeView parent, String value) {
    this.parent = parent;
    this.value = value;
    this.children = new ArrayList<>();
    this.style = "";
  }

  public void addChild(TokenTreeView child) {
    children.add(child);
  }

  public void addChildren(TokenTreeView... children) {
    this.children.addAll(Arrays.asList(children));
  }

  public void addChild(String value) {
    children.add(new TokenTreeView(this, value));
  }

}
