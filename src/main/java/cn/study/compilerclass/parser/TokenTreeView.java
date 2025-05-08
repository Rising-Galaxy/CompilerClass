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
  private String nodeType; // 节点类型：语法单元类型
  private String description; // 节点描述：附加信息
  private boolean highlight; // 高亮显示
  private boolean folded; // 是否折叠子节点

  public TokenTreeView(TokenTreeView parent, String value) {
    this.parent = parent;
    this.value = value;
    this.children = new ArrayList<>();
    this.style = "";
    this.nodeType = "";
    this.description = "";
    this.highlight = false;
    this.folded = true;
  }

  /**
   * 创建完整信息的节点
   */
  public TokenTreeView(TokenTreeView parent, String value, String nodeType, String style) {
    this.parent = parent;
    this.value = value;
    this.children = new ArrayList<>();
    this.style = style;
    this.nodeType = nodeType;
    this.description = "";
    this.highlight = false;
    this.folded = true;
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

  /**
   * 获取节点的显示文本
   */
  public String getDisplayText() {
    if (nodeType != null && !nodeType.isEmpty()) {
      if (description != null && !description.isEmpty()) {
        return String.format("%s「%s」- %s", value, nodeType, description);
      } else {
        return String.format("%s「%s」", value, nodeType);
      }
    } else {
      return value;
    }
  }

  /**
   * 设置节点类型和描述
   */
  public void setNodeInfo(String nodeType, String description) {
    this.nodeType = nodeType;
    this.description = description;
  }

  /**
   * 高亮显示此节点
   */
  public void highlightNode() {
    this.highlight = true;
  }

  /**
   * 设置节点是否默认折叠子节点
   */
  public void setFolded(boolean folded) {
    this.folded = folded;
  }
}
