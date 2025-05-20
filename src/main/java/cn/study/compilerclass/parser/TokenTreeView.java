package cn.study.compilerclass.parser;

import cn.study.compilerclass.model.NodeType;
import java.util.ArrayList;
import java.util.Arrays;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class TokenTreeView {

  private TokenTreeView parent;
  private String value;
  private final ArrayList<TokenTreeView> children;
  private NodeType nodeType; // 节点类型：语法单元类型
  private String description; // 节点描述：附加信息
  private boolean highlight; // 高亮显示
  private boolean folded; // 是否折叠子节点

  public TokenTreeView(TokenTreeView parent, String value) {
    this.parent = parent;
    this.value = value;
    this.children = new ArrayList<>();
    this.nodeType = null;
    this.description = "";
    this.highlight = false;
    this.folded = true;
  }

  /**
   * 创建完整信息的节点
   */
  public TokenTreeView(TokenTreeView parent, String value, NodeType nodeType) {
    this.parent = parent;
    this.value = value;
    this.children = new ArrayList<>();
    this.nodeType = nodeType;
    this.description = "";
    this.highlight = false;
    this.folded = true;
  }
  
  /**
   * 兼容旧版本的字符串类型构造函数
   */
  public TokenTreeView(TokenTreeView parent, String value, String nodeTypeStr) {
    this.parent = parent;
    this.value = value;
    this.children = new ArrayList<>();
    try {
      this.nodeType = NodeType.valueOf(nodeTypeStr);
    } catch (IllegalArgumentException e) {
      // 如果无法转换为枚举，则保持为null
      this.nodeType = null;
    }
    this.description = "";
    this.highlight = false;
    this.folded = true;
  }

  public TokenTreeView(TokenTreeView parent, String value, NodeType nodeType, String description) {
    this(parent, value, nodeType);
    this.description = description;
  }
  
  public TokenTreeView(TokenTreeView parent, String value, String nodeTypeStr, String description) {
    this(parent, value, nodeTypeStr);
    this.description = description;
  }

  public void addChild(TokenTreeView child) {
    children.add(child);
  }

  public void addChildren(TokenTreeView... children) {
    this.children.addAll(Arrays.asList(children));
  }

  /**
   * 添加子节点
   *
   * @param value 子节点的值
   */
  public void addChild(String value) {
    children.add(new TokenTreeView(this, value));
  }

  /**
   * 获取节点的显示文本
   *
   * @return 显示文本
   */
  public String getDisplayText() {
    if (nodeType != null) {
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
   *
   * @param nodeType 节点类型
   * @param description 节点描述
   */
  public void setNodeInfo(NodeType nodeType, String description) {
    this.nodeType = nodeType;
    this.description = description;
  }
  
  /**
   * 兼容旧版本的字符串类型设置方法
   */
  public void setNodeInfo(String nodeTypeStr, String description) {
    try {
      this.nodeType = NodeType.valueOf(nodeTypeStr);
    } catch (IllegalArgumentException e) {
      // 如果无法转换为枚举，则保持为null
      this.nodeType = null;
    }
    this.description = description;
  }

  /**
   * 高亮显示此节点
   */
  public void highlightNode() {
    this.highlight = true;
  }
}
