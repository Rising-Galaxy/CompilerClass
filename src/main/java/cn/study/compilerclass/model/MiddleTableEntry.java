package cn.study.compilerclass.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class MiddleTableEntry {

  private final SimpleIntegerProperty id;
  private final SimpleStringProperty op;
  private final SimpleStringProperty arg1;
  private final SimpleStringProperty arg2;
  private final SimpleStringProperty result;

  public MiddleTableEntry(int id, String op, String arg1, String arg2, String result) {
    this.id = new SimpleIntegerProperty(id);
    this.op = new SimpleStringProperty(op);
    this.arg1 = new SimpleStringProperty(arg1);
    this.arg2 = new SimpleStringProperty(arg2);
    this.result = new SimpleStringProperty(result);
  }

  public int getId() {
    return id.get();
  }

  public String getOp() {
    return op.get();
  }

  public String getArg1() {
    return arg1.get();
  }

  public String getArg2() {
    return arg2.get();
  }

  public String getResult() {
    return result.get();
  }

  public SimpleIntegerProperty idProperty() {
    return id;
  }

  public SimpleStringProperty opProperty() {
    return op;
  }

  public SimpleStringProperty arg1Property() {
    return arg1;
  }

  public SimpleStringProperty arg2Property() {
    return arg2;
  }

  public SimpleStringProperty resultProperty() {
    return result;
  }
}
