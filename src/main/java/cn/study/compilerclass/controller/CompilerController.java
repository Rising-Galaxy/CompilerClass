package cn.study.compilerclass.controller;

import cn.study.compilerclass.assembly.AssemblyGenerator;
import cn.study.compilerclass.lexer.Lexer;
import cn.study.compilerclass.lexer.Token;
import cn.study.compilerclass.lexer.TokenView;
import cn.study.compilerclass.model.FunctionTableEntry;
import cn.study.compilerclass.model.ConstTableEntry;
import cn.study.compilerclass.model.MiddleTableEntry;
import cn.study.compilerclass.model.VariableTableEntry;
import cn.study.compilerclass.parser.Parser;
import cn.study.compilerclass.parser.TokenTreeView;
import cn.study.compilerclass.syntax.SemanticAnalyzer;
import cn.study.compilerclass.utils.Debouncer;
import cn.study.compilerclass.utils.OutInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.stage.FileChooser;
import java.io.File;
import java.nio.file.Files;
import lombok.extern.slf4j.Slf4j;

import static cn.study.compilerclass.CompilerApp.stage;

@Slf4j
public class CompilerController {

  private SemanticAnalyzer semanticAnalyzer;
  private Parser parser;
  private File currentFile;
  private SimpleBooleanProperty isModified = new SimpleBooleanProperty(false);

  @FXML
  private TableView<ConstTableEntry> constTable;
  @FXML
  private TableColumn<ConstTableEntry, String> constNameColumn;
  @FXML
  private TableColumn<ConstTableEntry, String> constTypeColumn;
  @FXML
  private TableColumn<ConstTableEntry, String> constValueColumn;
  @FXML
  private TableView<VariableTableEntry> variableTable;
  @FXML
  private TableColumn<VariableTableEntry, String> varNameColumn;
  @FXML
  private TableColumn<VariableTableEntry, String> varTypeColumn;
  @FXML
  private TableColumn<VariableTableEntry, String> varScopeColumn;
  @FXML
  private TableColumn<VariableTableEntry, String> varValueColumn;
  @FXML
  private TableView<FunctionTableEntry> functionTable;
  @FXML
  private TableColumn<FunctionTableEntry, String> funcNameColumn;
  @FXML
  private TableColumn<FunctionTableEntry, String> funcReturnTypeColumn;
  @FXML
  private TableColumn<FunctionTableEntry, String> funcParamTypeColumn;
  @FXML
  private TableColumn<FunctionTableEntry, Integer> funcParamCountColumn;
  @FXML
  private TableView<MiddleTableEntry> middleTable;
  @FXML
  private TableColumn<MiddleTableEntry, Integer> midIdColumn;
  @FXML
  private TableColumn<MiddleTableEntry, String> midOpColumn;
  @FXML
  private TableColumn<MiddleTableEntry, String> midArg1Column;
  @FXML
  private TableColumn<MiddleTableEntry, String> midArg2Column;
  @FXML
  private TableColumn<MiddleTableEntry, String> midResultColumn;
  @FXML
  private TextArea codeArea;
  @FXML
  private TextArea resArea;
  @FXML
  private TextArea outArea;
  @FXML
  private TextArea lineNumbersCode;
  @FXML
  private TextArea lineNumbersRes;
  @FXML
  private Menu editMenu;
  @FXML
  private Menu fileMenu;
  @FXML
  private MenuItem generateAssemblyMenuItem;
  @FXML
  private Label cursorPositionLabel;
  @FXML
  private TitledPane outPane;
  @FXML
  private TableView<TokenView> resultTable;
  @FXML
  private TableColumn<TokenView, Integer> indexColumn;
  @FXML
  private TableColumn<TokenView, String> wordColumn;
  @FXML
  private TableColumn<TokenView, Integer> codeColumn;
  @FXML
  private TableColumn<TokenView, String> posColumn;
  @FXML
  private TreeView<String> resultTreeView;
  @FXML
  private Label fileLabel;
  private String statusLabel = "源程序";
  private int lineWidth = 2;
  private final Debouncer debouncer = new Debouncer(300);
  private String fileTooltip = "未命名文件";
  private Tooltip tooltip;
  @FXML
  private TabPane mainTabPane;

  // 词法分析
  @FXML
  private void handleLexicalAnalysis(ActionEvent event) {
    if (currentFile == null || isModified.getValue()) {
      showAlert(AlertType.WARNING, "请先保存当前内容，再进行词法分析。");
      return;
    }

    // 切换到词法分析选项卡
    mainTabPane.getSelectionModel().select(0);

    String sourceCode = codeArea.getText();
    if (sourceCode == null || sourceCode.trim().isEmpty()) {
      outArea.setText("源代码为空，无法进行词法分析。");
      outPane.setExpanded(true);
      return;
    }

    OutInfo outInfos = new OutInfo();
    Lexer lexer = new Lexer(sourceCode, outInfos);
    List<Token> tokens = lexer.analyze();

    // 添加到表格中
    indexColumn.setCellValueFactory(new PropertyValueFactory<>("index"));
    wordColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
    codeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
    posColumn.setCellValueFactory(new PropertyValueFactory<>("pos"));
    ObservableList<TokenView> tokenViews = tokens.stream()
                                                 .map(token -> token.toView(tokens.indexOf(token)))
                                                 .collect(Collectors.toCollection(FXCollections::observableArrayList));
    resultTable.setItems(tokenViews);

    if (!outInfos.isEmpty()) {
      if (outInfos.hasError()) {
        outInfos.error("词法分析", "词法分析过程中发生错误。");
      } else {
        // 自动导出 Token 列表到同级目录 {文件名}_tokens.json 文件
        try {
          Gson gson = new GsonBuilder().disableHtmlEscaping().create();
          String json = gson.toJson(tokens);
          String fileName = getFileNameWithoutExtension(currentFile.getName());
          String filePath = currentFile.getParent() + File.separator + fileName + "_tokens.json";
          Files.writeString(new File(filePath).toPath(), json, StandardCharsets.UTF_8);
          outInfos.info("词法分析", "结果已自动保存到同级目录 " + fileName + "_tokens.json 文件。");
        } catch (IOException e) {
          outInfos.error("词法分析", "保存词法分析结果到文件时发生错误: " + e.getMessage());
        }
      }
      outArea.setText(outInfos.toString());
      outPane.setExpanded(true);
    }
  }

  // 语法分析
  @FXML
  private void handleSyntaxAnalysis(ActionEvent event) {
    if (currentFile == null || isModified.getValue()) {
      showAlert(AlertType.WARNING, "请先保存当前内容，再进行语法分析。");
      return;
    }
    handleLexicalAnalysis(event); // 确保先进行词法分析
    // 确保样式类被应用
    resultTreeView.getStyleClass().add("result-tree");
    OutInfo outInfo = new OutInfo();
    String fileName = getFileNameWithoutExtension(currentFile.getName());
    parser = new Parser(currentFile.getParent() + File.separator + fileName + "_tokens.json", outInfo);
    parser.parse();
    // treeRoot 应该在 parser.parse() 后被赋值
    if (parser.treeRoot != null) {
      parser.getTreeView(resultTreeView);

      // 自动导出语法树到同级目录 {文件名}_tree.txt 文件
      if (!outInfo.hasError()) {
        try {
          String treeText = buildTreeText(parser.treeRoot, "", true);
          String filePath = currentFile.getParent() + File.separator + fileName + "_tree.txt";
          Files.writeString(new File(filePath).toPath(), treeText, StandardCharsets.UTF_8);
          outInfo.info("语法分析", "语法树已自动保存到同级目录 " + fileName + "_tree.txt 文件。");
        } catch (IOException e) {
          outInfo.error("语法分析", "保存语法树到文件时发生错误: " + e.getMessage());
        }
      }
    } else {
      log.warn("语法分析后 treeRoot 仍为 null");
    }

    // 切换到语法分析选项卡
    mainTabPane.getSelectionModel().select(1);

    if (!outInfo.isEmpty()) {
      outArea.setText(outInfo.toString());
      outPane.setExpanded(true);
    }
  }

  // 语义分析和生成四元式
  @FXML
  private void handleSemanticAnalysis(ActionEvent event) {
    if (currentFile == null || isModified.getValue()) {
      showAlert(AlertType.WARNING, "请先保存当前内容，再执行语义分析");
      return;
    }
    handleSyntaxAnalysis(event); // 确保先进行语法分析
    if (parser == null || parser.hasError()) {
      showAlert(AlertType.WARNING, "无法进行语义分析，请先确保语法分析无误");
      return;
    }
    OutInfo outInfo = new OutInfo();
    semanticAnalyzer = new SemanticAnalyzer(outInfo);

    // 切换到语义分析选项卡
    mainTabPane.getSelectionModel().select(2);
    semanticAnalyzer.analyze(parser.treeRoot); // 使用 Parser.treeRoot

    // 获取分析结果并转换为ObservableList
    ObservableList<ConstTableEntry> constData = FXCollections.observableArrayList(semanticAnalyzer.getConstTableEntries());
    setConstTableData(constData);

    ObservableList<VariableTableEntry> variableData = FXCollections.observableArrayList(semanticAnalyzer.getVariableTableEntries());
    setVariableTableData(variableData);

    ObservableList<FunctionTableEntry> functionData = FXCollections.observableArrayList(semanticAnalyzer.getFunctionTableEntries());
    setFunctionTableData(functionData);

    ObservableList<MiddleTableEntry> middleData = FXCollections.observableArrayList(semanticAnalyzer.getMiddleTableEntries());
    setMiddleTableData(middleData);

    // 自动导出四元式到同级目录 {文件名}_middle.txt 文件
    if (!outInfo.hasError() && !semanticAnalyzer.getMiddleTableEntries().isEmpty()) {
      try {
        String fileName = getFileNameWithoutExtension(currentFile.getName());
        String middleText = formatMiddleTable(semanticAnalyzer.getMiddleTableEntries());
        String filePath = currentFile.getParent() + File.separator + fileName + "_middle.txt";
        Files.writeString(new File(filePath).toPath(), middleText, StandardCharsets.UTF_8);
        outInfo.info("语义分析", "四元式已自动保存到同级目录 " + fileName + "_middle.txt 文件。");
      } catch (IOException e) {
        outInfo.error("语义分析", "保存四元式到文件时发生错误: " + e.getMessage());
      }
    }

    if (!outInfo.isEmpty()) {
      outArea.setText(outInfo.toString());
      outPane.setExpanded(true);
    }
  }

  // 生成汇编代码
  public void handleGenerateAssembly(ActionEvent event) {
    if (currentFile == null || isModified.getValue()) {
      showAlert(AlertType.WARNING, "请先保存当前内容，再执行语义分析");
      return;
    }
    handleSemanticAnalysis(event); // 确保先进行语义分析
    if (semanticAnalyzer == null || semanticAnalyzer.middleTableList.isEmpty() || semanticAnalyzer.hasError()) {
      showAlert(AlertType.WARNING, "请先确保语义分析无误");
      return;
    }

    AssemblyGenerator assemblyGenerator = new AssemblyGenerator(semanticAnalyzer.constTable, semanticAnalyzer.variableTable, semanticAnalyzer.functionTable, semanticAnalyzer.middleTableList);
    String assemblyCode = assemblyGenerator.generateAssembly();

    if (assemblyGenerator.hasError()) {
      showAlert(AlertType.ERROR, "生成汇编代码失败: " + assemblyCode);
      return;
    }

    mainTabPane.getSelectionModel().select(4);
    resArea.setText(assemblyCode);

    // 自动导出汇编代码到同级目录 {文件名}.asm 文件
    try {
      String fileName = getFileNameWithoutExtension(currentFile.getName());
      String filePath = currentFile.getParent() + File.separator + fileName + ".asm";
      Files.writeString(new File(filePath).toPath(), assemblyCode, StandardCharsets.UTF_8);
      // 可以选择显示成功信息或者静默保存
      // showAlert(AlertType.INFORMATION, "汇编代码已自动保存到同级目录 " + fileName + ".asm 文件。");
    } catch (IOException e) {
      showAlert(AlertType.ERROR, "保存汇编代码到文件时发生错误: " + e.getMessage());
    }

    // 粘贴到粘贴板
    Platform.runLater(() -> {
      Clipboard clipboard = Clipboard.getSystemClipboard();
      ClipboardContent content = new ClipboardContent();
      content.putString(assemblyCode);
      clipboard.setContent(content);
    });
    // showAlert(AlertType.INFORMATION, "汇编代码已复制到粘贴板。");
  }

  @FXML
  public void initialize() {
    setupLineNumbers(codeArea, lineNumbersCode);
    setupLineNumbers(resArea, lineNumbersRes);
    codeArea.textProperty().addListener((obs, old, val) -> markModified());
    codeArea.setFocusTraversable(true);
    setupContextMenu(codeArea);
    setupEditMenu();
    setupFileMenu();

    // 添加光标位置监听器
    codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> updatePosLabel(codeArea));
    resArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> updatePosLabel(resArea));

    // 添加 Tab 键拦截器
    codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (event.getCode() == KeyCode.TAB) {
        event.consume(); // 阻止默认 Tab 输入
        codeArea.insertText(codeArea.getCaretPosition(), "  "); // 插入两个空格
      }
    });

    // 初始化标签
    updatePosLabel(codeArea);

    // 初始化表格列与数据模型的绑定
    setupConstTable();
    setupVariableTable();
    setupFunctionTable();
    setupMiddleTable();

    isModified.addListener((obs, oldVal, newVal) -> {
      if (newVal) {
        statusLabel = "*" + statusLabel;
      } else {
        statusLabel = statusLabel.replaceFirst("\\*", "");
      }
      debouncer.debounceFX(() -> fileLabel.setText(statusLabel));
    });

    tooltip = new Tooltip(fileTooltip);
    fileLabel.setTooltip(tooltip);

    // 汇编代码快捷键
    generateAssemblyMenuItem.setAccelerator(KeyCodeCombination.keyCombination("Ctrl+B"));

    stage.setOnCloseRequest(event -> {
      if (isModified.getValue()) {
        log.info("检测到文件修改，询问是否保存");
        Alert alert = new Alert(AlertType.CONFIRMATION, "是否保存当前文件？", ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
          saveFile();
          log.info("文件已保存，退出程序");
        } else if (result.isPresent() && result.get() == ButtonType.CANCEL) {
          log.info("用户取消退出，程序继续运行");
          debouncer.cancel();
          event.consume();
          return;
        } else {
          log.info("用户选择不保存，退出程序");
        }
      } else {
        log.info("文件未修改，直接退出程序");
      }
      debouncer.cancel();
      Platform.exit();
      System.exit(0);
    });
  }

  private void setupConstTable() {
    constNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
    constTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
    constValueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
  }

  private void setupVariableTable() {
    varNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
    varTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
    varScopeColumn.setCellValueFactory(new PropertyValueFactory<>("scope"));
    varValueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
  }

  private void setupFunctionTable() {
    funcNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
    funcReturnTypeColumn.setCellValueFactory(new PropertyValueFactory<>("returnType"));
    funcParamTypeColumn.setCellValueFactory(new PropertyValueFactory<>("listString"));
    funcParamCountColumn.setCellValueFactory(new PropertyValueFactory<>("paramCount"));
  }

  private void setupMiddleTable() {
    midIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
    midOpColumn.setCellValueFactory(new PropertyValueFactory<>("op"));
    midArg1Column.setCellValueFactory(new PropertyValueFactory<>("arg1"));
    midArg2Column.setCellValueFactory(new PropertyValueFactory<>("arg2"));
    midResultColumn.setCellValueFactory(new PropertyValueFactory<>("result"));
  }

  private void showAlert(Alert.AlertType type, String message) {
    new Alert(type, message, ButtonType.OK).showAndWait();
  }

  public void setConstTableData(ObservableList<ConstTableEntry> data) {
    constTable.setItems(data);
  }

  public void setVariableTableData(ObservableList<VariableTableEntry> data) {
    variableTable.setItems(data);
  }

  public void setFunctionTableData(ObservableList<FunctionTableEntry> data) {
    functionTable.setItems(data);
  }

  public void setMiddleTableData(ObservableList<MiddleTableEntry> data) {
    middleTable.setItems(data);
  }

  private void updatePosLabel(TextArea area) {
    int caretPos = area.getCaretPosition();
    String rawText = area.getText();

    // 直接使用原始文本计算（已通过事件过滤器确保无制表符）
    List<String> paragraphs = rawText.lines().toList();

    int line = 1;
    int column = caretPos + 1;

    for (String paragraph : paragraphs) {
      int paraLength = paragraph.length() + 1; // +1 包含换行符
      if (caretPos >= paraLength) {
        caretPos -= paraLength;
        line++;
      } else {
        column = caretPos + 1;
        break;
      }
    }

    cursorPositionLabel.setText(String.format("行: %d, 列: %d", line, column));
  }

  // 初始化文件菜单
  private void setupFileMenu() {
    List<MenuItem> fileMenuItems = fileMenu.getItems();
    MenuItem newItem = fileMenuItems.get(0);
    MenuItem openItem = fileMenuItems.get(1);
    MenuItem saveItem = fileMenuItems.get(3);
    MenuItem saveAsItem = fileMenuItems.get(4);
    MenuItem closeItem = fileMenuItems.get(6); // 索引可能需要根据FXML调整

    bindKeyAndEvent(newItem, KeyCodeCombination.keyCombination("Ctrl+N"), this::handleNew);
    bindKeyAndEvent(openItem, KeyCodeCombination.keyCombination("Ctrl+O"), this::handleOpen);
    bindKeyAndEvent(saveItem, KeyCodeCombination.keyCombination("Ctrl+S"), this::handleSave);
    bindKeyAndEvent(saveAsItem, KeyCodeCombination.keyCombination("Ctrl+Shift+S"), this::handleSaveAs);
    bindKeyAndEvent(closeItem, KeyCodeCombination.keyCombination("Ctrl+Q"), this::handleClose);
  }

  // 初始化编辑菜单
  private void setupEditMenu() {
    List<MenuItem> editMenuItems = editMenu.getItems();

    MenuItem undoItem = editMenuItems.get(0);
    MenuItem redoItem = editMenuItems.get(1);
    MenuItem cutItem = editMenuItems.get(2);
    MenuItem copyItem = editMenuItems.get(3);
    MenuItem pasteItem = editMenuItems.get(4);
    MenuItem deleteItem = editMenuItems.get(5);
    MenuItem selectAllItem = editMenuItems.get(6);

    bindKeyAndEvent(undoItem, KeyCodeCombination.keyCombination("Ctrl+Z"), this::handleUndo);
    bindKeyAndEvent(redoItem, KeyCodeCombination.keyCombination("Ctrl+Y"), this::handleRedo);
    bindKeyAndEvent(cutItem, KeyCodeCombination.keyCombination("Ctrl+X"), this::handleCut);
    bindKeyAndEvent(copyItem, KeyCodeCombination.keyCombination("Ctrl+C"), this::handleCopy);
    bindKeyAndEvent(pasteItem, KeyCodeCombination.keyCombination("Ctrl+V"), this::handlePaste);
    bindKeyAndEvent(deleteItem, KeyCodeCombination.keyCombination("Delete"), this::handleDelete);
    bindKeyAndEvent(selectAllItem, KeyCodeCombination.keyCombination("Ctrl+A"), this::handleSelectAll);
  }

  // 绑定快捷键和事件
  private void bindKeyAndEvent(MenuItem item, KeyCombination keyCombination, javafx.event.EventHandler<ActionEvent> handler) {
    item.setAccelerator(keyCombination);
    item.setOnAction(handler);
  }

  // 设置右键菜单
  private void setupContextMenu(TextArea textArea) {
    ContextMenu contextMenu = new ContextMenu();

    MenuItem undoItem = new MenuItem("撤销");
    undoItem.setOnAction(this::handleUndo);

    MenuItem redoItem = new MenuItem("重做");
    redoItem.setOnAction(this::handleRedo);

    MenuItem cutItem = new MenuItem("剪切");
    cutItem.setOnAction(this::handleCut);

    MenuItem copyItem = new MenuItem("复制");
    copyItem.setOnAction(this::handleCopy);

    MenuItem pasteItem = new MenuItem("粘贴");
    pasteItem.setOnAction(this::handlePaste);

    MenuItem deleteItem = new MenuItem("删除");
    deleteItem.setOnAction(this::handleDelete);

    MenuItem selectAllItem = new MenuItem("全选");
    selectAllItem.setOnAction(this::handleSelectAll);

    contextMenu.getItems()
               .addAll(undoItem, redoItem, new SeparatorMenuItem(), cutItem, copyItem, pasteItem, deleteItem, new SeparatorMenuItem(), selectAllItem);

    textArea.setContextMenu(contextMenu);

    // 动态更新右键菜单状态
    textArea.setOnContextMenuRequested(event -> {
      TextArea target = (TextArea) event.getSource();
      boolean hasSelection = !target.getSelectedText().isEmpty();
      boolean isEditable = target.isEditable();
      boolean hasContent = !target.getText().isEmpty();

      undoItem.setDisable(!isEditable);
      redoItem.setDisable(!isEditable);
      cutItem.setDisable(!hasSelection || !isEditable);
      copyItem.setDisable(!hasSelection);
      pasteItem.setDisable(!isEditable || !Clipboard.getSystemClipboard().hasString());
      deleteItem.setDisable(!hasSelection || !isEditable);
      selectAllItem.setDisable(!hasContent);
    });
  }

  @FXML
  private void handleUndo(ActionEvent event) {
    TextArea focusedTextArea = getFocusedTextArea();
    if (focusedTextArea != null && focusedTextArea.isEditable()) {
      focusedTextArea.undo();
    }
  }

  @FXML
  private void handleRedo(ActionEvent event) {
    TextArea focusedTextArea = getFocusedTextArea();
    if (focusedTextArea != null && focusedTextArea.isEditable()) {
      focusedTextArea.redo();
    }
  }

  @FXML
  private void handleCut(ActionEvent event) {
    TextArea focusedTextArea = getFocusedTextArea();
    if (focusedTextArea != null && focusedTextArea.isEditable()) {
      focusedTextArea.cut();
    }
  }

  @FXML
  private void handleCopy(ActionEvent event) {
    TextArea focusedTextArea = getFocusedTextArea();
    if (focusedTextArea != null) {
      focusedTextArea.copy();
    }
  }

  @FXML
  private void handlePaste(ActionEvent event) {
    TextArea focusedTextArea = getFocusedTextArea();
    if (focusedTextArea != null && focusedTextArea.isEditable()) {
      focusedTextArea.paste();
    }
  }

  @FXML
  private void handleDelete(ActionEvent event) {
    TextArea focusedTextArea = getFocusedTextArea();
    if (focusedTextArea != null && focusedTextArea.isEditable()) {
      focusedTextArea.replaceSelection("");
    }
  }

  @FXML
  private void handleSelectAll(ActionEvent event) {
    TextArea focusedTextArea = getFocusedTextArea();
    if (focusedTextArea != null) {
      focusedTextArea.selectAll();
    }
  }

  private TextArea getFocusedTextArea() {
    return codeArea;
  }

  private void setupLineNumbers(TextArea contentArea, TextArea lineNumberArea) {

    // 阻止行号区域的滚动
    lineNumberArea.addEventFilter(javafx.scene.input.ScrollEvent.ANY, Event::consume);

    // 阻止行号区域的鼠标点击获取焦点
    lineNumberArea.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
      if (event.getTarget() == lineNumberArea) {
        event.consume();
      }
    });

    contentArea.textProperty().addListener((obs, old, val) -> updateLineNumbers(contentArea, lineNumberArea));
    contentArea.scrollTopProperty().addListener((obs, old, val) -> lineNumberArea.setScrollTop(val.doubleValue()));

  }

  private void updateLineNumbers(TextArea contentArea, TextArea lineNumberArea) {
    int lines = contentArea.getParagraphs().size();
    lineNumberArea.setText(String.join("\n", java.util.stream.IntStream.rangeClosed(1, lines)
                                                                       .mapToObj(String::valueOf)
                                                                       .toArray(String[]::new)));
    // 根据 lines 的位数调整行号区域的宽度
    int lineNumber = String.valueOf(lines).length();
    if (lineNumber != lineWidth) {
      double width = lineNumberArea.getPrefWidth();
      width = lineNumber * (width / lineWidth);
      lineWidth = lineNumber;
      lineNumberArea.setPrefWidth(width);
    }
  }

  public boolean confirmSave() {
    if (!isModified.get()) {
      return true; // 如果未修改，则无需确认
    }
    Alert alert = new Alert(AlertType.CONFIRMATION, "是否保存对当前文件的更改?", ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
    Optional<ButtonType> result = alert.showAndWait();
    if (result.isPresent() && result.get() == ButtonType.YES) {
      return saveFile();
    } else {
      return result.isPresent() && result.get() == ButtonType.NO;
    }
  }

  @FXML
  private void handleNew(ActionEvent event) {
    if (confirmSave()) {
      codeArea.clear();
      currentFile = null;
      markUnmodified();
      statusLabel = "未命名文件";
      fileTooltip = "未命名文件";
      fileLabel.setText(statusLabel);
      tooltip.setText(fileTooltip);
      outArea.clear();
      resultTable.getItems().clear();
      if (resultTreeView.getRoot() != null) {
        resultTreeView.getRoot().getChildren().clear();
      }
      // 清空符号表等
      if (constTable != null) {
        constTable.getItems().clear();
      }
      if (variableTable != null) {
        variableTable.getItems().clear();
      }
      if (functionTable != null) {
        functionTable.getItems().clear();
      }
    }
  }

  @FXML
  private void handleOpen(ActionEvent event) {
    if (!confirmSave()) {
      return;
    }
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("打开文件");
    // 从首选项加载上次打开的目录
    Preferences prefs = Preferences.userNodeForPackage(CompilerController.class);
    String lastUsedDirectory = prefs.get("lastUsedOpenDirectory", System.getProperty("user.home"));
    File initialDirectory = new File(lastUsedDirectory);
    if (initialDirectory.exists() && initialDirectory.isDirectory()) {
      fileChooser.setInitialDirectory(initialDirectory);
    }

    File file = fileChooser.showOpenDialog(stage);
    if (file != null) {
      try {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        codeArea.setText(content);
        currentFile = file;
        markUnmodified();
        statusLabel = currentFile.getName();
        fileTooltip = currentFile.getAbsolutePath();
        fileLabel.setText(statusLabel);
        tooltip.setText(fileTooltip);
        // 保存当前目录到首选项
        prefs.put("lastUsedOpenDirectory", file.getParent());
      } catch (IOException e) {
        showAlert(AlertType.ERROR, "打开文件失败: " + e.getMessage());
      }
    }
  }

  private boolean saveFile() {
    if (currentFile == null) {
      return saveFileAs();
    } else {
      try {
        Files.writeString(currentFile.toPath(), codeArea.getText(), StandardCharsets.UTF_8);
        markUnmodified();
        statusLabel = currentFile.getName();
        fileLabel.setText(statusLabel);
        return true;
      } catch (IOException e) {
        showAlert(AlertType.ERROR, "保存文件失败: " + e.getMessage());
        return false;
      }
    }
  }

  private boolean saveFileAs() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("另存为");
    fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("star 文件", "*.star"));
    fileChooser.setInitialFileName(currentFile != null ? currentFile.getName() : "未命名文件.star");
    // 从首选项加载上次保存的目录
    Preferences prefs = Preferences.userNodeForPackage(CompilerController.class);
    String lastUsedDirectory = prefs.get("lastUsedSaveAsDirectory", System.getProperty("user.home"));
    File initialDirectory = new File(lastUsedDirectory);
    if (initialDirectory.exists() && initialDirectory.isDirectory()) {
      fileChooser.setInitialDirectory(initialDirectory);
    }

    File file = fileChooser.showSaveDialog(stage);
    if (file != null) {
      try {
        Files.writeString(file.toPath(), codeArea.getText(), StandardCharsets.UTF_8);
        currentFile = file;
        markUnmodified();
        statusLabel = currentFile.getName();
        fileTooltip = currentFile.getAbsolutePath();
        fileLabel.setText(statusLabel);
        tooltip.setText(fileTooltip);
        // 保存当前目录到首选项
        prefs.put("lastUsedSaveAsDirectory", file.getParent());
        return true;
      } catch (IOException e) {
        showAlert(AlertType.ERROR, "保存文件失败: " + e.getMessage());
        return false;
      }
    }
    return false;
  }

  @FXML
  private void handleSave(ActionEvent event) {
    saveFile();
  }

  @FXML
  private void handleSaveAs(ActionEvent event) {
    saveFileAs();
  }

  @FXML
  private void handleClose(ActionEvent event) {
    if (confirmSave()) {
      Platform.exit();
      System.exit(0);
    }
  }

  private void markModified() {
    if (!isModified.get()) {
      isModified.set(true);
    }
  }

  private void markUnmodified() {
    if (isModified.get()) {
      isModified.set(false);
    }
  }

  private String buildTreeText(TokenTreeView node, String indent, boolean isLast) {
    StringBuilder sb = new StringBuilder();
    sb.append(indent);

    if (isLast) {
      sb.append("└─ ");
      indent += "   ";
    } else {
      sb.append("├─ ");
      indent += "│  ";
    }

    sb.append(node.getDisplayText()).append("\n");

    List<TokenTreeView> children = node.getChildren();
    for (int i = 0; i < children.size(); i++) {
      sb.append(buildTreeText(children.get(i), indent, i == children.size() - 1));
    }

    return sb.toString();
  }

  private String getFileNameWithoutExtension(String fileName) {
    int lastDotIndex = fileName.lastIndexOf('.');
    if (lastDotIndex > 0) {
      return fileName.substring(0, lastDotIndex);
    }
    return fileName;
  }

  private String formatMiddleTable(List<MiddleTableEntry> middleTableEntries) {
    StringBuilder sb = new StringBuilder();
    sb.append("四元式表\n");
    sb.append("===========================================\n");

    for (MiddleTableEntry entry : middleTableEntries) {
      sb.append(String.format("%d.(%s, %s, %s, %s)\n", entry.getId(), entry.getOp() != null ? entry.getOp() : "-", entry.getArg1() != null ? entry.getArg1() : "-", entry.getArg2() != null ? entry.getArg2() : "-", entry.getResult() != null ? entry.getResult() : "-"));
    }

    return sb.toString();
  }
}
