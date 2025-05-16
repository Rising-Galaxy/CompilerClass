package cn.study.compilerclass.model;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;

public class VariableTableEntry {
    private final SimpleStringProperty name;
    private final SimpleStringProperty type;
    private final SimpleStringProperty scope;
    private final SimpleStringProperty initialValue;
    private final SimpleBooleanProperty used;

    public VariableTableEntry(String name, String type, String scope, String initialValue, boolean used) {
        this.name = new SimpleStringProperty(name);
        this.type = new SimpleStringProperty(type);
        this.scope = new SimpleStringProperty(scope);
        this.initialValue = new SimpleStringProperty(initialValue);
        this.used = new SimpleBooleanProperty(used);
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

    public String getInitialValue() {
        return initialValue.get();
    }

    public SimpleStringProperty initialValueProperty() {
        return initialValue;
    }

    public boolean getUsed() {
        return used.get();
    }

    public SimpleBooleanProperty usedProperty() {
        return used;
    }
}