package cn.study.compilerclass.model;

import java.util.ArrayList;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import lombok.Getter;

public class FunctionTableEntry {

  private final SimpleStringProperty name;
  private final SimpleStringProperty returnType;
  @Getter
  private final ArrayList<String> paramTypes; // 参数类型列表
  private final SimpleStringProperty listString; // 参数类型列表
  private final SimpleIntegerProperty paramCount; // 参数个数

  public FunctionTableEntry(String name, String returnType, ArrayList<String> paramTypes) {
    this.name = new SimpleStringProperty(name);
    this.returnType = new SimpleStringProperty(returnType);
    this.paramTypes = paramTypes;
    this.listString = new SimpleStringProperty(String.join(", ", paramTypes));
    this.paramCount = new SimpleIntegerProperty(paramTypes.size());
  }

  public String getListString() {
    return listString.get();
  }

  public SimpleStringProperty listStringProperty() {
    return listString;
  }

  public String getName() {
    return name.get();
  }

  public SimpleStringProperty nameProperty() {
    return name;
  }

  public String getReturnType() {
    return returnType.get();
  }

  public SimpleStringProperty returnTypeProperty() {
    return returnType;
  }

  public int getParamCount() {
    return paramCount.get();
  }

  public SimpleIntegerProperty paramCountProperty() {
    return paramCount;
  }
}