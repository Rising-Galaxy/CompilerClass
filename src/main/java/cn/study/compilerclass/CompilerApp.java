package cn.study.compilerclass;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;

public class CompilerApp extends Application {

  @Override
  public void start(Stage stage) throws IOException {
    Parent root = new FXMLLoader(CompilerApp.class.getResource("my-compiler.fxml")).load();
    Scene scene = new Scene(root);
    Font.loadFont(getClass().getResourceAsStream("/cn/study/compilerclass/font/MapleMonoNormal-NF-CN-Regular.ttf"), 16);
    Application.setUserAgentStylesheet(String.valueOf(CompilerApp.class.getResource("css/dracula.css")));
    scene.getStylesheets().add(String.valueOf(CompilerApp.class.getResource("css/style.css")));
    stage.setTitle("景明编译器");
    stage.setScene(scene);
    stage.setMaximized(true);
    stage.show();
  }

  public static void main(String[] args) {
    launch();
  }
}