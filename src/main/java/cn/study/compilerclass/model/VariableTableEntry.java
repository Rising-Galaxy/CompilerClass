package cn.study.compilerclass.model;

import javafx.beans.property.SimpleStringProperty;

public class VariableTableEntry extends ConstTableEntry {

  private final SimpleStringProperty scope;

  public VariableTableEntry(String name, String type, String scope, String value, int initCol, int initRow) {
    super(name, type, value, initCol, initRow);
    this.scope = new SimpleStringProperty(scope);
  }

  public String getScope() {
    return scope.get();
  }

  public SimpleStringProperty scopeProperty() {
    return scope;
  }
}