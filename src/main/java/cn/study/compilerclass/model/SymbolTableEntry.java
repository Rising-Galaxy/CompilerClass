package cn.study.compilerclass.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class SymbolTableEntry {
    private final SimpleStringProperty name;
    private final SimpleStringProperty type;
    private final SimpleStringProperty scope;
    private final SimpleIntegerProperty line;
    private final SimpleStringProperty info;

    public SymbolTableEntry(String name, String type, String scope, int line, String info) {
        this.name = new SimpleStringProperty(name);
        this.type = new SimpleStringProperty(type);
        this.scope = new SimpleStringProperty(scope);
        this.line = new SimpleIntegerProperty(line);
        this.info = new SimpleStringProperty(info);
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

    public int getLine() {
        return line.get();
    }

    public SimpleIntegerProperty lineProperty() {
        return line;
    }

    public String getInfo() {
        return info.get();
    }

    public SimpleStringProperty infoProperty() {
        return info;
    }
}