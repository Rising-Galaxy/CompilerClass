# 贡献指南

感谢您考虑为景明编译器项目做出贡献！以下是一些指导原则，帮助您更有效地参与项目开发。

## 开发环境设置

1. 确保您已安装以下软件：
   - JDK 21 或更高版本
   - Maven 3.6 或更高版本
   - 支持Java和JavaFX的IDE（如IntelliJ IDEA、Eclipse或VS Code）

2. Fork并克隆仓库：
   ```bash
   git clone https://github.com/your-username/CompilerClass.git
   cd CompilerClass
   ```

3. 设置开发分支：
   ```bash
   git checkout -b feature/your-feature-name
   ```

## 代码风格指南

- 遵循Java标准编码规范
- 使用4个空格进行缩进（不使用制表符）
- 类名使用PascalCase命名法（如`CompilerController`）
- 方法和变量名使用camelCase命名法（如`parseExpression`）
- 常量使用全大写下划线分隔命名法（如`MAX_TOKEN_LENGTH`）
- 添加适当的注释，特别是对于复杂的算法和逻辑
- 使用Lombok注解减少样板代码

## 提交规范

提交消息应遵循以下格式：

```
<类型>(<范围>): <描述>

[可选的详细描述]

[可选的脚注]
```

类型可以是：
- `feat`：新功能
- `fix`：错误修复
- `docs`：文档更改
- `style`：不影响代码含义的更改（空格、格式等）
- `refactor`：既不修复错误也不添加功能的代码更改
- `perf`：提高性能的代码更改
- `test`：添加或修正测试
- `chore`：对构建过程或辅助工具的更改

示例：
```
feat(lexer): 添加对多行注释的支持

实现了对多行注释的词法分析，使用/**/格式。
添加了相关测试用例。

Resolves: #123
```

## 测试指南

- 为新功能和修复的bug添加单元测试
- 确保所有测试都能通过
- 使用JUnit 5进行测试

## Pull Request流程

1. 确保您的代码符合项目的代码风格指南
2. 确保所有测试都能通过
3. 更新文档（如有必要）
4. 提交Pull Request到`main`分支
5. 在PR描述中清晰地说明您的更改内容和目的
6. 等待代码审查并根据反馈进行修改

## 报告Bug

如果您发现了bug，请创建一个issue并包含以下信息：

- 问题的简要描述
- 重现步骤
- 预期行为
- 实际行为
- 截图（如适用）
- 您的环境信息（操作系统、Java版本等）

## 功能请求

如果您想请求新功能，请创建一个issue并包含以下信息：

- 功能的简要描述
- 为什么这个功能对项目有价值
- 可能的实现方式（如果您有想法）

## 联系方式

如有任何问题，请通过以下方式联系项目维护者：

- 电子邮件：[项目维护者邮箱]
- GitHub Issues

感谢您的贡献！