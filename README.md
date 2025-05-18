# 景明编译器 (Jingming Compiler)

## 项目简介

景明编译器是一个基于JavaFX开发的编译器教学工具，旨在展示编译原理的核心概念和流程。该项目实现了完整的编译过程，包括词法分析、语法分析和语义分析等阶段。

## 功能特点

- **词法分析**：将源代码转换为标记（Token）序列
- **语法分析**：构建抽象语法树（AST）
- **语义分析**：进行类型检查和符号表管理
- **符号表管理**：跟踪变量和函数定义
- **现代化UI界面**：基于JavaFX的直观用户界面
- **语法高亮**：支持代码语法高亮显示

## 技术栈

- Java 21
- JavaFX 21
- Maven
- Lombok
- SLF4J & Logback (日志管理)
- GSON (JSON处理)

## 项目结构

```
src/main/java/cn/study/compilerclass/
├── CompilerApp.java              # 应用程序入口
├── controller/
│   └── CompilerController.java   # 主控制器
├── lexer/                        # 词法分析相关
│   ├── Lexer.java                # 词法分析器
│   ├── Token.java                # 标记定义
│   ├── TokenManager.java         # 标记管理
│   └── TokenView.java            # 标记视图
├── model/                        # 数据模型
│   ├── FunctionTableEntry.java   # 函数表项
│   ├── SymbolTableEntry.java     # 符号表项
│   └── VariableTableEntry.java   # 变量表项
├── parser/                       # 语法分析相关
│   ├── Parser.java               # 语法分析器
│   └── TokenTreeView.java        # 语法树视图
├── syntax/                       # 语义分析相关
│   └── SemanticAnalyzer.java     # 语义分析器
└── utils/                        # 工具类
    ├── Debouncer.java            # 防抖动工具
    └── OutInfo.java              # 输出信息工具
```

## 安装与运行

### 前提条件

- JDK 21 或更高版本
- Maven 3.6 或更高版本

### 构建与运行

1. 克隆仓库
   ```bash
   git clone https://github.com/yourusername/CompilerClass.git
   cd CompilerClass
   ```

2. 使用Maven构建项目
   ```bash
   mvn clean package
   ```

3. 运行应用程序
   ```bash
   java -jar target/CompilerClass-1.0-SNAPSHOT.jar
   ```

或者使用Maven插件直接运行：

```bash
mvn javafx:run
```

## 使用指南

1. 启动应用程序后，您将看到主界面，其中包含代码编辑区域和多个功能面板
2. 在代码编辑区域输入或加载源代码
3. 使用工具栏上的按钮执行词法分析、语法分析和语义分析
4. 查看各个分析阶段的结果，包括标记列表、语法树和符号表

## 贡献指南

欢迎对本项目进行贡献！请遵循以下步骤：

1. Fork 本仓库
2. 创建您的特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交您的更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 打开一个 Pull Request

## 许可证

本项目采用 MIT 许可证 - 详情请参阅 [LICENSE](LICENSE) 文件

## 联系方式

如有任何问题或建议，请通过以下方式联系我们：

- 项目维护者：[RisingGalaxy](mailto:galaxy4rising@gmail.com)
- 项目仓库：[GitHub](https://github.com/Rising-Galaxy/CompilerClass)