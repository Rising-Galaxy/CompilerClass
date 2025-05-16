package cn.study.compilerclass.controller;

import cn.study.compilerclass.model.FunctionTableEntry;
import cn.study.compilerclass.model.SymbolTableEntry;
import cn.study.compilerclass.model.VariableTableEntry;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public class SemanticAnalysisController {

  @FXML
  private TableView<SymbolTableEntry> symbolTable;

  @FXML
  private TableColumn<SymbolTableEntry, String> symbolNameColumn;

  @FXML
  private TableColumn<SymbolTableEntry, String> symbolTypeColumn;

  @FXML
  private TableColumn<SymbolTableEntry, String> symbolScopeColumn;

  @FXML
  private TableColumn<SymbolTableEntry, Integer> symbolLineColumn;

  @FXML
  private TableColumn<SymbolTableEntry, String> symbolInfoColumn;

  @FXML
  private TableView<VariableTableEntry> variableTable;

  @FXML
  private TableColumn<VariableTableEntry, String> varNameColumn;

  @FXML
  private TableColumn<VariableTableEntry, String> varTypeColumn;

  @FXML
  private TableColumn<VariableTableEntry, String> varScopeColumn;

  @FXML
  private TableColumn<VariableTableEntry, String> varInitColumn;

  @FXML
  private TableColumn<VariableTableEntry, Boolean> varUsedColumn;

  @FXML
  private TableView<FunctionTableEntry> functionTable;

  @FXML
  private TableColumn<FunctionTableEntry, String> funcNameColumn;

  @FXML
  private TableColumn<FunctionTableEntry, String> funcReturnTypeColumn;

  @FXML
  private TableColumn<FunctionTableEntry, String> funcParamsColumn;

  @FXML
  private TableColumn<FunctionTableEntry, Integer> funcLineColumn;

  @FXML
  private TableColumn<FunctionTableEntry, Integer> funcCalledColumn;

  @FXML
  public void initialize() {
    // 初始化表格列与数据模型的绑定
    setupSymbolTable();
    setupVariableTable();
    setupFunctionTable();
  }

  private void setupSymbolTable() {
    symbolNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
    symbolTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
    symbolScopeColumn.setCellValueFactory(new PropertyValueFactory<>("scope"));
    symbolLineColumn.setCellValueFactory(new PropertyValueFactory<>("line"));
    symbolInfoColumn.setCellValueFactory(new PropertyValueFactory<>("info"));
  }

  private void setupVariableTable() {
    varNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
    varTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
    varScopeColumn.setCellValueFactory(new PropertyValueFactory<>("scope"));
    varInitColumn.setCellValueFactory(new PropertyValueFactory<>("initialValue"));
    varUsedColumn.setCellValueFactory(new PropertyValueFactory<>("used"));
  }

  private void setupFunctionTable() {
    funcNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
    funcReturnTypeColumn.setCellValueFactory(new PropertyValueFactory<>("returnType"));
    funcParamsColumn.setCellValueFactory(new PropertyValueFactory<>("parameters"));
    funcLineColumn.setCellValueFactory(new PropertyValueFactory<>("line"));
    funcCalledColumn.setCellValueFactory(new PropertyValueFactory<>("callCount"));
  }

  // 添加方法用于从编译器控制器接收语义分析数据
  public void setSymbolTableData(ObservableList<SymbolTableEntry> data) {
    symbolTable.setItems(data);
  }

  public void setVariableTableData(ObservableList<VariableTableEntry> data) {
    variableTable.setItems(data);
  }

  public void setFunctionTableData(ObservableList<FunctionTableEntry> data) {
    functionTable.setItems(data);
  }
}