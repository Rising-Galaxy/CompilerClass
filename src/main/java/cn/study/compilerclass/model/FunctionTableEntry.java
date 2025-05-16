package cn.study.compilerclass.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class FunctionTableEntry {
    private final SimpleStringProperty name;
    private final SimpleStringProperty returnType;
    private final SimpleStringProperty parameters;
    private final SimpleIntegerProperty line;
    private final SimpleIntegerProperty callCount;

    public FunctionTableEntry(String name, String returnType, String parameters, int line, int callCount) {
        this.name = new SimpleStringProperty(name);
        this.returnType = new SimpleStringProperty(returnType);
        this.parameters = new SimpleStringProperty(parameters);
        this.line = new SimpleIntegerProperty(line);
        this.callCount = new SimpleIntegerProperty(callCount);
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

    public String getParameters() {
        return parameters.get();
    }

    public SimpleStringProperty parametersProperty() {
        return parameters;
    }

    public int getLine() {
        return line.get();
    }

    public SimpleIntegerProperty lineProperty() {
        return line;
    }

    public int getCallCount() {
        return callCount.get();
    }

    public SimpleIntegerProperty callCountProperty() {
        return callCount;
    }
}