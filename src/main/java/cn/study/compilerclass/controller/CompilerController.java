package cn.study.compilerclass.controller;

import cn.study.compilerclass.lexer.Lexer;
import cn.study.compilerclass.lexer.Token;
import cn.study.compilerclass.lexer.TokenView;
import cn.study.compilerclass.model.FunctionTableEntry;
import cn.study.compilerclass.model.ConstTableEntry;
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
  private File currentFile;
  private SimpleBooleanProperty isModified = new SimpleBooleanProperty(false);

  @FXML
  private TableView<ConstTableEntry> constTable;
  @FXML
  private TableColumn<ConstTableEntry, String> constNameColumn;
  @FXML
  private TableColumn<ConstTableEntry, String> constTypeColumn;
  @FXML
  private TableColumn<ConstTableEntry, String> constScopeColumn;
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
  private TextArea codeArea;
  @FXML
  private TextArea outArea;
  @FXML
  private TextArea lineNumbersCode;
  @FXML
  private Menu editMenu;
  @FXML
  private Menu fileMenu;
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
  private Debouncer debouncer = new Debouncer(300);
  private String fileTooltip = "未命名文件";
  private Tooltip tooltip;
  @FXML
  private TabPane mainTabPane;
  @FXML
  private MenuItem exportSyntaxTreeItem;

  @FXML
  public void initialize() {
    setupLineNumbers(codeArea, lineNumbersCode);
    codeArea.textProperty().addListener((obs, old, val) -> markModified());
    codeArea.setFocusTraversable(true);
    setupContextMenu(codeArea);
    setupEditMenu();
    setupFileMenu();

    // 添加光标位置监听器
    codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> updateSrcCodeLabel());

    // 添加 Tab 键拦截器
    codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (event.getCode() == KeyCode.TAB) {
        event.consume(); // 阻止默认 Tab 输入
        codeArea.insertText(codeArea.getCaretPosition(), "  "); // 插入两个空格
      }
    });

    // 初始化标签
    updateSrcCodeLabel();

    // 初始化表格列与数据模型的绑定
    setupConstTable();
    setupVariableTable();

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
    constScopeColumn.setCellValueFactory(new PropertyValueFactory<>("scope"));
    constValueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
  }

  private void setupVariableTable() {
    varNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
    varTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
    varScopeColumn.setCellValueFactory(new PropertyValueFactory<>("scope"));
    varValueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
  }

  @FXML
  private void handleSyntaxAnalysis(ActionEvent event) {
    if (currentFile == null || isModified.getValue()) {
      showAlert(AlertType.WARNING, "请先保存当前内容，再进行语法分析。");
      return;
    }
    // 确保样式类被应用
    resultTreeView.getStyleClass().add("result-tree");
    OutInfo outInfo = new OutInfo();
    Parser parser = new Parser(currentFile.getParent() + File.separator + "lex_tokens.json", outInfo);
    parser.parse();
    // treeRoot 应该在 parser.parse() 后被赋值
    if (Parser.treeRoot != null) {
      parser.getTreeView(resultTreeView);
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

  private void showAlert(Alert.AlertType type, String message) {
    new Alert(type, message, ButtonType.OK).showAndWait();
  }

  @FXML
  private void handleSemanticAnalysis(ActionEvent event) {
    if (currentFile == null || isModified.getValue()) {
      showAlert(AlertType.WARNING, "请先保存当前内容，再进行语义分析。");
      return;
    }
    OutInfo outInfo = new OutInfo();
    semanticAnalyzer = new SemanticAnalyzer(outInfo);

    // 切换到语义分析选项卡
    mainTabPane.getSelectionModel().select(2);

    // 如果有语法树，则进行语义分析
    if (Parser.treeRoot != null) { // 使用 Parser.treeRoot
      semanticAnalyzer.analyze(Parser.treeRoot); // 使用 Parser.treeRoot

      // 获取分析结果并转换为ObservableList
      ObservableList<ConstTableEntry> symbolData = FXCollections.observableArrayList(semanticAnalyzer.getConstTableEntries());
      setSymbolTableData(symbolData);

      ObservableList<VariableTableEntry> variableData = FXCollections.observableArrayList(semanticAnalyzer.getVariableTableEntries());
      setVariableTableData(variableData);

      ObservableList<FunctionTableEntry> functionData = FXCollections.observableArrayList(semanticAnalyzer.getFunctionTableEntries());
      // setFunctionTableData(functionData);

      if (!outInfo.isEmpty()) {
        outArea.setText(outInfo.toString());
        outPane.setExpanded(true);
      }
    } else {
      // 如果没有语法树，显示提示信息
      Alert alert = new Alert(AlertType.WARNING);
      alert.setTitle("警告");
      alert.setHeaderText("无法进行语义分析");
      alert.setContentText("请先进行词法分析和语法分析以生成语法树");
      alert.showAndWait();
    }
  }

  // 添加方法用于从编译器控制器接收语义分析数据
  public void setSymbolTableData(ObservableList<ConstTableEntry> data) {
    constTable.setItems(data);
  }

  public void setVariableTableData(ObservableList<VariableTableEntry> data) {
    variableTable.setItems(data);
  }

  // 统一处理制表符替换
  private String replaceTabs(String text) {
    return text.replace("\t", "  ");
  }

  private void updateSrcCodeLabel() {
    int caretPos = codeArea.getCaretPosition();
    String rawText = codeArea.getText();

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
        // 输出 Token 列表到同级目录 lex_tokens.json 文件
        try {
          Gson gson = new GsonBuilder().disableHtmlEscaping().create();
          String json = gson.toJson(tokens);
          String filePath = currentFile.getParent() + File.separator + "lex_tokens.json";
          Files.writeString(new File(filePath).toPath(), json, StandardCharsets.UTF_8);
          outInfos.info("词法分析", "结果已保存到同级目录 lex_tokens.json 文件。");
        } catch (IOException e) {
          outInfos.error("词法分析", "保存词法分析结果到文件时发生错误: " + e.getMessage());
        }
      }
      outArea.setText(outInfos.toString());
      outPane.setExpanded(true);
    }
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
      // if (functionTable != null) functionTable.getItems().clear();
      Parser.treeRoot = null; // 清空静态语法树
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

  @FXML
  private void handleExportSyntaxTree(ActionEvent event) {
    if (Parser.treeRoot == null) {
      showAlert(AlertType.WARNING, "请先进行语法分析再导出");
      return;
    }

    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("导出语法树");

    // 设置默认文件名
    fileChooser.setInitialFileName("ParserTree.txt");
    
    // 新增路径记忆功能
    Preferences prefs = Preferences.userNodeForPackage(CompilerController.class);
    String lastUsedExportDirectory = prefs.get("lastUsedExportDirectory", System.getProperty("user.home"));
    File initialDirectory = new File(lastUsedExportDirectory);
    if (initialDirectory.exists() && initialDirectory.isDirectory()) {
      fileChooser.setInitialDirectory(initialDirectory);
    }

    fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("文本文件", "*.txt"));
    File file = fileChooser.showSaveDialog(stage);

    if (file != null) {
      try (FileWriter writer = new FileWriter(file)) {
        // 保存当前目录到首选项
        prefs.put("lastUsedExportDirectory", file.getParent());
        
        String treeText = buildTreeText(Parser.treeRoot, "", true);
        writer.write(treeText);
        showAlert(AlertType.INFORMATION, "语法树已成功导出至：" + file.getAbsolutePath());
      } catch (IOException e) {
        log.error("导出失败", e);
        showAlert(AlertType.ERROR, "导出失败：" + e.getMessage());
      }
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

    sb.append(node.getValue()).append("\n");

    List<TokenTreeView> children = node.getChildren();
    for (int i = 0; i < children.size(); i++) {
      sb.append(buildTreeText(children.get(i), indent, i == children.size() - 1));
    }

    return sb.toString();
  }
}