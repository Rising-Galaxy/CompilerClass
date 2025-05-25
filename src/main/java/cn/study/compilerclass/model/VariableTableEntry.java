package cn.study.compilerclass.model;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;

public class VariableTableEntry {
    private final SimpleStringProperty name;
    private final SimpleStringProperty type;
    private final SimpleStringProperty scope;
    private final SimpleStringProperty value;

    public VariableTableEntry(String name, String type, String scope, String value) {
        this.name = new SimpleStringProperty(name);
        this.type = new SimpleStringProperty(type);
        this.scope = new SimpleStringProperty(scope);
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

    public String getScope() {
        return scope.get();
    }

    public SimpleStringProperty scopeProperty() {
        return scope;
    }

    public String getValue() {
        return value.get();
    }

    public SimpleStringProperty valueProperty() {
        return value;
    }
}