package cn.study.compilerclass.model;

import javafx.beans.property.SimpleStringProperty;

public class ConstTableEntry {

  private final SimpleStringProperty name;
  private final SimpleStringProperty type;
  private final SimpleStringProperty value;

  public ConstTableEntry(String name, String type, String value) {
    this.name = new SimpleStringProperty(name);
    this.type = new SimpleStringProperty(type);
    this.value = new SimpleStringProperty(value);
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
}