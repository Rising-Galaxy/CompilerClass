module cn.study.compilerclass {
  requires javafx.controls;
  requires javafx.base;
  requires javafx.fxml;

  requires org.controlsfx.controls;
  requires org.kordamp.ikonli.javafx;
  requires static lombok;
  requires org.slf4j;
  requires com.google.gson;
  requires org.apache.commons.lang3;
  requires org.apache.commons.text;
  requires java.prefs;

  opens cn.study.compilerclass.controller to javafx.fxml;
  opens cn.study.compilerclass to javafx.fxml;
  opens cn.study.compilerclass.lexer to javafx.base, com.google.gson;
  exports cn.study.compilerclass;
}