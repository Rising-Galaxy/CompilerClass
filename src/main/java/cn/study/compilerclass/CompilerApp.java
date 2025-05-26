package cn.study.compilerclass;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import java.io.IOException;

public class CompilerApp extends Application {
  public static Stage stage;

  @Override
  public void start(Stage stage1) throws IOException {
    stage = stage1;
    Parent root = new FXMLLoader(CompilerApp.class.getResource("my-compiler.fxml")).load();
    Scene scene = new Scene(root);
    Font.loadFont(getClass().getResourceAsStream("/cn/study/compilerclass/font/MapleMonoNormal-NF-CN-Regular.ttf"), 16);
    // 修改CSS加载方式
    String dracula = CompilerApp.class.getResource("css/dracula.css").toExternalForm();
    String style = CompilerApp.class.getResource("css/style.css").toExternalForm();
    Application.setUserAgentStylesheet(dracula);
    scene.getStylesheets().add(style);
    stage.setTitle("景明编译器");
    stage.setScene(scene);
    stage.setMaximized(true);
    stage.show();
  }

  public static void main(String[] args) {
    launch();
  }
}