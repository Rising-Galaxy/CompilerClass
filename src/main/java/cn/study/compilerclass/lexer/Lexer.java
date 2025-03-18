package cn.study.compilerclass.lexer;

import java.util.ArrayList;
import java.util.List;

/**
 * 词法分析器，负责将源代码解析成token序列
 */
public class Lexer {

  private final String sourceCode;
  private final TokenManager tokenManager;
  private int currentPos;
  private int currentLine;
  private int currentColumn;
  private char currentChar;

  public Lexer(String sourceCode) {
    this.sourceCode = sourceCode;
    this.tokenManager = new TokenManager();
    this.currentPos = 0;
    this.currentLine = 1;
    this.currentColumn = 0;
    moveNext();
  }

  private void moveNext() {
    if (currentPos < sourceCode.length()) {
      currentChar = sourceCode.charAt(currentPos++);
      currentColumn++;
      if (currentChar == '\n') {
        currentLine++;
        currentColumn = 0;
      }
    } else {
      currentChar = '\0';
    }
  }

  private void skipWhitespace() {
    while (currentChar == ' ' || currentChar == '\t' || currentChar == '\n' || currentChar == '\r') {
      moveNext();
    }
  }

  private void skipComment() {
    // 判断当前字符是否为斜杠 '/'
    if (currentChar == '/') {
      moveNext(); // 移动到下一个字符
      if (currentChar == '/') {
        // 单行注释处理逻辑
        while (currentChar != '\n' && currentChar != '\0') {
          moveNext(); // 跳过单行注释的内容，直到遇到换行符或文件结束
        }
        moveNext(); // 消耗换行符
      } else if (currentChar == '*') {
        // 多行注释处理逻辑
        moveNext();
        while (true) {
          if (currentChar == '\0') {
            // 如果文件结束但未找到多行注释的闭合符号，则抛出异常
            throw new RuntimeException("多行注释未正确闭合，可能缺少 '*/'");
          }
          if (currentChar == '*') {
            moveNext();
            if (currentChar == '/') {
              moveNext(); // 找到闭合符号 '*/'，结束多行注释
              break;
            }
          } else {
            moveNext(); // 继续查找闭合符号
          }
        }
      }
    }
  }

  private Token scanNumber() {
    StringBuilder sb = new StringBuilder();
    int startColumn = currentColumn;
    boolean isFloat = false;

    // 识别整数和浮点数
    while (Character.isDigit(currentChar) || currentChar == '.') {
      if (currentChar == '.') {
        if (isFloat) {
          throw new RuntimeException(String.format("非法的浮点数格式，行 %d 列 %d", currentLine, currentColumn));
        }
        // 检查后续是否有数字，确保小数点后有有效数字
        if (!Character.isDigit(peekNextChar())) {
          break;
        }
        isFloat = true; // 标记为浮点数
      }
      sb.append(currentChar); // 将当前字符加入结果
      moveNext(); // 移动到下一个字符
    }

    // 处理科学计数法
    if (currentChar == 'e' || currentChar == 'E') {
      sb.append(currentChar); // 添加科学计数法的标志符 'e' 或 'E'
      moveNext();

      // 处理正负号
      if (currentChar == '+' || currentChar == '-') {
        sb.append(currentChar); // 添加正负号
        moveNext();
      }

      // 确保科学计数法后跟随有效数字
      while (Character.isDigit(currentChar)) {
        sb.append(currentChar);
        moveNext();
      }
      isFloat = true; // 科学计数法属于浮点数
    }

    String value = sb.toString();
    int type = isFloat ? tokenManager.getType("float") : tokenManager.getType("integer");
    return new Token(value, type, currentLine, startColumn);
  }

  /**
   * 预览下一个字符，但不移动当前指针位置
   *
   * @return 下一个字符，如果已到达文件末尾则返回 '\0'
   */
  private char peekNextChar() {
    if (currentPos < sourceCode.length()) {
      return sourceCode.charAt(currentPos);
    }
    return '\0';
  }

  private Token scanIdentifier() {
    StringBuilder sb = new StringBuilder();
    int startColumn = currentColumn;

    // 匹配标识符规则：字母或下划线开头，后接字母/数字/下划线
    while (Character.isLetterOrDigit(currentChar) || currentChar == '_') {
      sb.append(currentChar);
      moveNext();
    }

    String value = sb.toString();
    int type;

    // 先检查是否是关键字，否则作为标识符
    if (tokenManager.isKeyword(value)) {
      type = tokenManager.getType(value);
    } else {
      type = tokenManager.getType("identifier");
    }

    return new Token(value, type, currentLine, startColumn);
  }

  private Token scanOperator() {
    StringBuilder sb = new StringBuilder();
    int startColumn = currentColumn;

    // 处理可能的双字符运算符
    sb.append(currentChar);
    char nextChar = currentPos < sourceCode.length() ? sourceCode.charAt(currentPos) : '\0';
    moveNext();

    if ((sb.charAt(0) == '=' && nextChar == '=') || (sb.charAt(0) == '!' && nextChar == '=') || (sb.charAt(0) == '<' && nextChar == '=') || (sb.charAt(0) == '>' && nextChar == '=')) {
      sb.append(nextChar);
      moveNext();
    }

    String value = sb.toString();
    int type = tokenManager.getType(value);
    if (type == -1) {
      throw new RuntimeException(String.format("未知的运算符'%s'，行%d列%d", value, currentLine, startColumn));
    }

    return new Token(value, type, currentLine, startColumn);
  }

  private Token scanString() {
    StringBuilder sb = new StringBuilder();
    int startColumn = currentColumn;
    moveNext(); // 跳过开始的引号

    while (currentChar != '"' && currentChar != '\0') {
      if (currentChar == '\\') {
        moveNext();
        switch (currentChar) {
          case 'n' -> sb.append('\n');
          case 't' -> sb.append('\t');
          case 'r' -> sb.append('\r');
          case '\\' -> sb.append('\\');
          case '"' -> sb.append('"');
          default ->
              throw new RuntimeException(String.format("非法的转义字符'%c'，行%d列%d", currentChar, currentLine, currentColumn));
        }
      } else {
        sb.append(currentChar);
      }
      moveNext();
    }

    if (currentChar == '\0') {
      throw new RuntimeException(String.format("未闭合的字符串，行%d列%d", currentLine, startColumn));
    }

    moveNext(); // 跳过结束的引号
    return new Token(sb.toString(), tokenManager.getType("string"), currentLine, startColumn);
  }

  private Token scanChar() {
    int startColumn = currentColumn;
    moveNext(); // 跳过开始的单引号

    char value;
    if (currentChar == '\\') {
      moveNext();
      value = switch (currentChar) {
        case 'n' -> '\n';
        case 't' -> '\t';
        case 'r' -> '\r';
        case '\\' -> '\\';
        case '\'' -> '\'';
        default ->
            throw new RuntimeException(String.format("非法的转义字符'%c'，行%d列%d", currentChar, currentLine, currentColumn));
      };
      moveNext();
    } else {
      value = currentChar;
      moveNext();
    }

    if (currentChar != '\'') {
      throw new RuntimeException(String.format("字符常量必须是单个字符，行%d列%d", currentLine, startColumn));
    }

    moveNext(); // 跳过结束的单引号
    return new Token(String.valueOf(value), tokenManager.getType("char"), currentLine, startColumn);
  }

  public List<Token> analyze() {
    List<Token> tokens = new ArrayList<>();

    while (currentChar != '\0') {
      skipWhitespace();

      if (currentChar == '\0') {
        break;
      }

      Token token;
      if (Character.isLetter(currentChar) || currentChar == '_') {
        token = scanIdentifier();
      } else if (Character.isDigit(currentChar)) {
        token = scanNumber();
      } else if (currentChar == '"') {
        token = scanString();
      } else if (currentChar == '\'') {
        token = scanChar();
      } else if (currentChar == '/') {
        int startColumn = currentColumn;
        moveNext();
        if (currentChar == '/' || currentChar == '*') {
          currentColumn = startColumn;
          currentPos--;
          currentChar = '/';
          skipComment();
          continue;
        } else {
          currentColumn = startColumn;
          currentPos--;
          currentChar = '/';
          token = scanOperator();
        }
      } else if (tokenManager.isOperator(String.valueOf(currentChar)) || tokenManager.isDelimiter(String.valueOf(currentChar))) {
        token = scanOperator();
      } else {
        throw new RuntimeException(String.format("非法字符'%c'，行%d列%d", currentChar, currentLine, currentColumn));
      }

      tokens.add(token);
    }

    return tokens;
  }
}