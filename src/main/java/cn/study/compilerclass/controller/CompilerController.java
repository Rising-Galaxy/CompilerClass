package cn.study.compilerclass.controller;

import cn.study.compilerclass.lexer.Lexer;
import cn.study.compilerclass.lexer.Token;
import cn.study.compilerclass.lexer.TokenView;
import cn.study.compilerclass.utils.OutInfo;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompilerController {

  private File currentFile;
  private boolean isModified = false;

  @FXML
  private TextArea codeArea;
  @FXML
  private TextArea resultArea;
  @FXML
  private TextArea outArea;
  @FXML
  private TextArea lineNumbersCode;
  @FXML
  private TextArea lineNumbersResult;
  @FXML
  private Menu editMenu;
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
  private VBox ResultVBox;
  @FXML
  private HBox ResultHBox;

  @FXML
  public void initialize() {
    setupLineNumbers(codeArea, lineNumbersCode);
    setupLineNumbers(resultArea, lineNumbersResult);
    codeArea.textProperty().addListener((obs, old, val) -> markModified());
    codeArea.setFocusTraversable(true);
    setupContextMenu(codeArea);
    setupEditMenu();

    // 添加光标位置监听器
    codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> updateCursorPositionLabel());

    // 添加 Tab 键拦截器
    codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (event.getCode() == KeyCode.TAB) {
        event.consume(); // 阻止默认 Tab 输入
        codeArea.insertText(codeArea.getCaretPosition(), "  "); // 插入两个空格
      }
    });
  }

  // 统一处理制表符替换
  private String replaceTabs(String text) {
    return text.replace("\t", "  ");
  }

  private void updateCursorPositionLabel() {
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
    ResultVBox.getChildren().remove(ResultHBox);

    String sourceCode = codeArea.getText();
    if (sourceCode == null || sourceCode.trim().isEmpty()) {
      outArea.setText("源代码为空，无法进行词法分析。");
      outPane.setExpanded(true);
      return;
    }


    OutInfo outInfos = new OutInfo();
    Lexer lexer = new Lexer(sourceCode, outInfos);
    List<Token> tokens = lexer.analyze();

    // 添加到 表格中
    indexColumn.setCellValueFactory(new PropertyValueFactory<>("index"));
    wordColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
    codeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
    posColumn.setCellValueFactory(new PropertyValueFactory<>("pos"));
    ObservableList<TokenView> tokenViews = tokens.stream()
                                                  .map(token -> token.toView(tokens.indexOf(token)))
                                                  .collect(Collectors.toCollection(FXCollections::observableArrayList));
    resultTable.setItems(tokenViews);

    // 初版的输出框-保留以防万一
    // StringBuilder result = new StringBuilder();
    // for (Token token : tokens) {
    //   result.append(String.format("[%d:%d]-{Type: %d, Value: %s}%n", token.getLine(), token.getColumn(), token.getType(), token.getValue()));
    // }
    // resultArea.setText(result.toString());
    if (!outInfos.isEmpty()) {
      outArea.setText(outInfos.toString());
      outPane.setExpanded(true);
      // TODO 检测到词法分析存在 [ERROR] 级别的错误时就不会执行后续的语法分析等系列操作
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
    if (codeArea.isFocused()) {
      return codeArea;
    } else if (resultArea.isFocused()) {
      return resultArea;
    }
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

    // 确保正确的焦点行为
    // contentArea.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
    //   if (!contentArea.isFocused() && contentArea.isEditable()) {
    //     contentArea.requestFocus();
    //     log.info("请求焦点到区域: {}", contentArea.getId());
    //   }
    // });
  }

  private void updateLineNumbers(TextArea contentArea, TextArea lineNumberArea) {
    int lines = contentArea.getParagraphs().size();
    lineNumberArea.setText(String.join("\n", java.util.stream.IntStream.rangeClosed(1, lines)
                                                                       .mapToObj(String::valueOf)
                                                                       .toArray(String[]::new)));
  }

  private boolean confirmSave() {
    if (!isModified) {
      return true; // 如果文件未被修改，则无需保存
    }

    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle("未保存的更改");
    alert.setHeaderText("当前文件包含未保存的更改，是否保存？");

    // 设置提示框的按钮选项
    alert.getButtonTypes().setAll(ButtonType.YES,   // 保存更改
        ButtonType.NO,    // 不保存更改
        ButtonType.CANCEL // 取消操作
    );

    Optional<ButtonType> result = alert.showAndWait();
    if (result.get() == ButtonType.YES) {
      return saveFile(); // 用户选择保存
    }
    return result.get() == ButtonType.NO; // 用户选择不保存
  }


  private boolean saveFile() {
    if (currentFile == null) {
      return saveAs();
    }

    try {
      String content = replaceTabs(codeArea.getText()); // 二次验证替换
      Files.write(currentFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
      isModified = false;
      return true;
    } catch (IOException e) {
      showAlert("保存错误", "无法保存文件: " + e.getMessage());
      return false;
    }
  }


  @FXML
  private void handleNew(ActionEvent event) {
    if (confirmSave()) {
      codeArea.clear();
      currentFile = null;
      isModified = false;
    }
  }

  @FXML
  private void handleOpen(ActionEvent event) {
    if (!confirmSave()) {
      return;
    }

    FileChooser chooser = new FileChooser();
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Star Files", "*.star"));

    // 获取上次保存的路径
    Preferences prefs = Preferences.userNodeForPackage(CompilerController.class);
    String lastPath = prefs.get("lastOpenPath", null);
    if (lastPath != null) {
      chooser.setInitialDirectory(new File(lastPath));
    }

    File file = chooser.showOpenDialog(codeArea.getScene().getWindow());
    if (file != null) {
      // 保存当前路径
      prefs.put("lastOpenPath", file.getParent());
      loadFile(file);
    }
  }

  private void loadFile(File file) {
    try {
      String content = Files.readString(file.toPath());
      codeArea.setText(replaceTabs(content)); // 强制转换制表符
      currentFile = file;
      isModified = false;
    } catch (IOException e) {
      showAlert("打开错误", "无法读取文件: " + e.getMessage());
    }
  }

  @FXML
  private void handleSave(ActionEvent event) {
    saveFile();
  }

  @FXML
  private void handleSaveAs(ActionEvent event) {
    saveAs();
  }

  private boolean saveAs() {
    FileChooser chooser = new FileChooser();
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Star Files", "*.star"));

    File file = chooser.showSaveDialog(codeArea.getScene().getWindow());
    if (file != null) {
      if (!file.getName().endsWith(".star")) {
        file = new File(file.getAbsolutePath() + ".star");
      }
      currentFile = file;
      return saveFile();
    }
    return false;
  }

  @FXML
  private void handleClose(ActionEvent event) {
    if (confirmSave()) {
      codeArea.clear();
      currentFile = null;
      isModified = false;
    }
  }

  private void markModified() {
    isModified = true;
  }

  private void showAlert(String title, String message) {
    new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait();
  }
}