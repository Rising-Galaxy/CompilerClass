# 景明编译器 (Jingming Compiler)

## 项目简介

景明编译器是一个基于JavaFX开发的编译器教学工具，旨在帮助用户理解编译原理的核心概念和流程。该项目包含词法分析、语法分析、语义分析等关键编译阶段的实现，并提供了可视化的界面来展示分析结果。

## 技术栈

*   Java 21
*   JavaFX 21
*   Maven
*   Lombok
*   SLF4J & Logback (日志管理)
*   Gson (JSON处理)

## 项目结构

```
CompilerClass/
├── .mvn/                           # Maven Wrapper配置
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── cn/study/compilerclass/
│   │   │       ├── CompilerApp.java        # JavaFX应用程序入口
│   │   │       ├── controller/             # 控制器层 (UI逻辑)
│   │   │       ├── lexer/                  # 词法分析器相关代码
│   │   │       ├── model/                  # 数据模型 (Token, AST节点等)
│   │   │       ├── parser/                 # 语法分析器相关代码
│   │   │       ├── semantic/               # 语义分析器相关代码 (如果单独模块)
│   │   │       └── utils/                  # 工具类
│   │   └── resources/
│   │       ├── cn/study/compilerclass/
│   │       │   ├── conf/                   # 配置文件 (如token定义)
│   │       │   ├── css/                    # CSS样式文件
│   │       │   ├── font/                   # 字体文件
│   │       │   └── my-compiler.fxml      # FXML界面定义文件
│   └── test/                           # 测试代码 (暂未详细列出)
├── .gitattributes
├── .gitignore
├── CONTRIBUTING.md
├── LICENSE
├── README.md                       # 本文件
├── mvnw                            # Maven Wrapper (Linux/macOS)
├── mvnw.cmd                        # Maven Wrapper (Windows)
└── pom.xml                         # Maven项目配置文件
```

## 运行要求

*   JDK 21 或更高版本
*   Maven 3.6 或更高版本

## 使用指南

### 基本界面操作

1.  **启动应用**: 通过 `mvn javafx:run` 命令或直接运行打包后的jar文件启动编译器。
2.  **代码输入**: 在主界面的代码编辑区域输入或粘贴您的源代码。
3.  **执行分析**: 使用工具栏提供的按钮执行词法分析、语法分析等操作。
4.  **查看结果**: 在对应的面板查看分析结果，例如Token序列、抽象语法树等。

### 语言特性与示例

本编译器支持一种简约的类C语言，包含以下基本特性：

*   **数据类型**: `int`, `float`, `bool`, `char` （其中 `float` 仅支持到语义阶段）
*   **控制流**: `if-elif-else`, `while`, `do-while` （其中循环仅支持到语法阶段）
*   **函数**: 支持函数定义和调用（仅支持到语法阶段）
*   **常量**: 支持 `const` 常量定义
*   **基本运算符**: 算术运算符, 关系运算符, 逻辑运算符

## 许可证

本项目使用 MIT 许可证，详情请见 [LICENSE](LICENSE) 文件。