module cn.study.compilerclass {
  requires javafx.controls;
  requires javafx.fxml;

  requires org.controlsfx.controls;
  requires org.kordamp.ikonli.javafx;
  requires static lombok;
  requires org.slf4j;
  requires com.google.gson;

  opens cn.study.compilerclass.controller to javafx.fxml;
  opens cn.study.compilerclass to javafx.fxml;
  exports cn.study.compilerclass;
}