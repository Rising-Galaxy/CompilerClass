package cn.study.compilerclass.model;

import javafx.beans.property.SimpleStringProperty;

public class ConstTableEntry {

  private final SimpleStringProperty name;
  private final SimpleStringProperty type;
  private final SimpleStringProperty value;
  private final SimpleStringProperty scope;

  public ConstTableEntry(String name, String type, String value, String scope) { // 修改构造函数
    this.name = new SimpleStringProperty(name);
    this.type = new SimpleStringProperty(type);
    this.value = new SimpleStringProperty(value);
    this.scope = new SimpleStringProperty(scope);
  }

  public String getName() {
    return name.get();
  }

  public SimpleStringProperty nameProperty() {
    return name;
  }

  public String getType() {
    return type.get();
  }

  public SimpleStringProperty typeProperty() {
    return type;
  }

  public String getValue() {
    return value.get();
  }

  public SimpleStringProperty valueProperty() {
    return value;
  }

  public String getScope() {
    return scope.get();
  }

  public SimpleStringProperty scopeProperty() {
    return scope;
  }
}