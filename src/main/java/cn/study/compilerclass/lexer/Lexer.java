package cn.study.compilerclass.lexer;

import cn.study.compilerclass.utils.OutInfo;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;

/**
 * 词法分析器，负责将源代码解析成token序列
 */
@Slf4j
public class Lexer {

  private final String sourceCode;
  private final TokenManager tokenManager;
  private final String src = "词法分析";
  private ErrorProcess errorProcess = ErrorProcess.SKIP;
  private int currentPos;
  private int currentLine;
  private int currentColumn;
  private char currentChar;
  private OutInfo outInfos;

  public Lexer(String sourceCode, OutInfo outInfos) {
    this.sourceCode = sourceCode;
    this.tokenManager = new TokenManager();
    this.currentPos = 0;
    this.currentLine = 1;
    this.currentColumn = 0;
    this.outInfos = outInfos;
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

  public List<Token> analyze() {
    List<Token> tokens = new ArrayList<>();

    try {
      info("开始分析...");
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
            token = scanOperatorAndOther();
          }
        } else if (tokenManager.isDelimiter(String.valueOf(currentChar))) {
          token = Token.builder()
                       .value(String.valueOf(currentChar))
                       .type(tokenManager.getType(String.valueOf(currentChar)))
                       .line(currentLine)
                       .column(currentColumn)
                       .build();
          moveNext();
        } else if (isOperator()) {
          token = scanOperatorAndOther();
        } else {
          error(String.format("不支持的字符'%s'-[r: %d, c: %d]", StringEscapeUtils.escapeJava(String.valueOf(currentChar)), currentLine, currentColumn));
          token = Token.builder()
                       .value(StringEscapeUtils.escapeJava(String.valueOf(currentChar)))
                       .type(tokenManager.getType("_ILLEGAL_"))
                       .line(currentLine)
                       .column(currentColumn)
                       .build();
          moveNext();
        }
        tokens.add(token);
      }
      info("分析完成！");
    } catch (Exception e) {
      errorProcess = ErrorProcess.SKIP;
      error("分析失败！", e);
      errorProcess = ErrorProcess.ERROR;
    }

    return tokens;
  }

  private void error(String msg, Exception e) {
    outInfos.error(src, msg, e);
    if (errorProcess != ErrorProcess.SKIP) {
      throw new RuntimeException(msg);
    }
  }

  private void info(String msg) {
    outInfos.info(src, msg);
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
            // 如果文件结束但未找到多行注释的闭合符号
            error(String.format("多行注释未正确闭合，可能缺少 '*/'-[r: %d, c: %d]", currentLine, currentColumn));
            break;
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
    StringBuilder sb = new StringBuilder(16);
    int startColumn = currentColumn;
    boolean isFloat = false;
    Radix radix = Radix.DECIMAL;

    if (currentChar == '0') {
      // 处理可能的进制前缀
      char nextChar = peekNextChar();
      if (nextChar == 'x' || nextChar == 'X') {
        // 处理十六进制数
        radix = Radix.HEXADECIMAL;
        sb.append(currentChar).append(nextChar);
        moveNext(); // 跳过0
        moveNext(); // 跳过x/X
        while (Character.digit(currentChar, 16) != -1) {
          sb.append(currentChar);
          moveNext();
        }
        if (Character.isLetter(currentChar)) { // 不必要检查currentChar是否能被识别为十六进制数字，因为Character.digit()已经检查过了
          error(String.format("非法的十六进制数格式-[r: %d, c: %d]", currentLine, currentColumn));
          Token token = getErrorToken(sb.append(currentChar).toString());
          moveNext();
          return token;
        } else if (currentChar == '.') {
          error(String.format("十六进制数不支持浮点数写法-[r: %d, c: %d]", currentLine, currentColumn));
          Token token = getErrorToken(sb.append(currentChar).toString());
          moveNext();
          return token;
        } else if (sb.length() == 2) {
          error(String.format("十六进制数缺少有效数字-[r: %d, c: %d]", currentLine, currentColumn));
          return getErrorToken(sb.toString());
        }
      } else if (nextChar == 'b' || nextChar == 'B') {
        // 处理二进制数
        radix = Radix.BINARY;
        sb.append(currentChar).append(nextChar);
        moveNext(); // 跳过0
        moveNext(); // 跳过b/B
        while (currentChar == '0' || currentChar == '1') {
          sb.append(currentChar);
          moveNext();
        }
        if (Character.isDigit(currentChar) || Character.isLetter(currentChar)) {
          error(String.format("非法的二进制数格式-[r: %d, c: %d]", currentLine, currentColumn));
          Token token = getErrorToken(sb.append(currentChar).toString());
          moveNext();
          return token;
        } else if (currentChar == '.') {
          error(String.format("二进制数不支持浮点数写法-[r: %d, c: %d]", currentLine, currentColumn));
          Token token = getErrorToken(sb.append(currentChar).toString());
          moveNext();
          return token;
        } else if (sb.length() == 2) {
          error(String.format("二进制数缺少有效数字-[r: %d, c: %d]", currentLine, currentColumn));
          return getErrorToken(sb.toString());
        }
      } else if (Character.isDigit(nextChar)) {
        // 处理八进制数
        radix = Radix.OCTAL;
        do {
          sb.append(currentChar);
          moveNext();
        } while (Character.digit(currentChar, 8) != -1);
        if (Character.isLetter(currentChar) || Character.isDigit(currentChar)) {
          error(String.format("非法的八进制数格式-[r: %d, c: %d]", currentLine, currentColumn));
          Token token = getErrorToken(sb.append(currentChar).toString());
          moveNext();
          return token;
        } else if (currentChar == '.') {
          error(String.format("八进制数不支持浮点数写法-[r: %d, c: %d]", currentLine, currentColumn));
          Token token = getErrorToken(sb.append(currentChar).toString());
          moveNext();
          return token;
        } else if (sb.length() == 1) {
          error(String.format("八进制数缺少有效数字-[r: %d, c: %d]", currentLine, currentColumn));
          return getErrorToken(sb.toString());
        }
      } else {
        // 视为十进制 0
        sb.append(currentChar);
        moveNext();
      }
    }
    if (radix == Radix.DECIMAL) {
      // 处理十进制数
      while (Character.isDigit(currentChar) || currentChar == '.') {
        if (currentChar == '.') {
          if (isFloat) {
            error(String.format("非法的浮点数格式-[r: %d, c: %d]", currentLine, currentColumn));
            Token token = getErrorToken(sb.append(currentChar).toString());
            moveNext();
            return token;
          }
          char nextChar = peekNextChar();
          if (!Character.isDigit(nextChar)) {
            error(String.format("小数点后缺少有效数字-[r: %d, c: %d]", currentLine, currentColumn));
            Token token = getErrorToken(sb.append(currentChar).toString());
            moveNext();
            return token;
          }
          isFloat = true;
        }
        sb.append(currentChar);
        moveNext();
      }
      // 处理科学计数法
      if (currentChar == 'e' || currentChar == 'E') {
        sb.append(currentChar);
        moveNext();
        if (currentChar == '+' || currentChar == '-') {
          sb.append(currentChar);
          moveNext();
        }
        if (!Character.isDigit(currentChar)) {
          error(String.format("科学计数法缺少有效数字-[r: %d, c: %d]", currentLine, currentColumn));
        } else {
          while (Character.isDigit(currentChar)) {
            sb.append(currentChar);
            moveNext();
          }
          isFloat = true;
        }
      }
    }
    if (isUnSupportedType()) {
      error(String.format("不支持的数字格式-[r: %d, c: %d]", currentLine, currentColumn));
      Token token = getErrorToken(sb.append(currentChar).toString());
      moveNext();
      return token;
    }

    int type = isFloat ? tokenManager.getType("_FLOAT_") : tokenManager.getType("_INTEGER_");
    return Token.builder().value(sb.toString()).type(type).line(currentLine).column(startColumn).build();
  }

  private Token getErrorToken(String value) {
    return Token.builder()
                .value(value)
                .type(tokenManager.getType("_ILLEGAL_"))
                .line(currentLine)
                .column(currentColumn)
                .build();
  }

  private boolean isUnSupportedType() {
    // 判断是否为文件结束符
    if (currentChar == '\0') {
      return false;
    }
    // 判断是否为空白字符
    if (currentChar == ' ' || currentChar == '\t' || currentChar == '\n' || currentChar == '\r') {
      return false;
    }
    // 判断是否为界符
    if (tokenManager.isDelimiter(String.valueOf(currentChar))) {
      return false;
    }
    // 判断是否为运算符
    return !isOperator();
  }

  private boolean isOperator() {
    return isSingleCharOperator() || isDoubleCharOperator();
  }

  private boolean isSingleCharOperator() {
    return tokenManager.isOperator(String.valueOf(currentChar));
  }

  private boolean isDoubleCharOperator() {
    return tokenManager.isOperator(String.valueOf(currentChar) + peekNextChar());
  }

  private void error(String msg) {
    outInfos.error(src, msg);
    if (errorProcess != ErrorProcess.SKIP) {
      throw new RuntimeException(msg);
    }
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
      type = tokenManager.getType("_IDENTIFIER_");
    }

    return Token.builder().value(value).type(type).line(currentLine).column(startColumn).build();
  }

  private Token scanOperatorAndOther() {
    StringBuilder sb = new StringBuilder();
    int startColumn = currentColumn;
    int type;
    String value;
    if (isDoubleCharOperator()) {
      sb.append(currentChar).append(peekNextChar());
      moveNext();
      moveNext();
      value = sb.toString();
      type = tokenManager.getType(value);
    } else if (isSingleCharOperator()) {
      sb.append(currentChar);
      moveNext();
      value = sb.toString();
      type = tokenManager.getType(value);
    } else {
      error(String.format("非法运算符'%s'-[r: %d, c: %d]", StringEscapeUtils.escapeJava(String.valueOf(currentChar)), currentLine, currentColumn));
      Token token = Token.builder()
                         .value(String.valueOf(currentChar))
                         .type(tokenManager.getType("_ILLEGAL_"))
                         .line(currentLine)
                         .column(currentColumn)
                         .build();
      moveNext();
      return token;
    }
    return Token.builder().value(value).type(type).line(currentLine).column(startColumn).build();
  }

  private Token scanString() {
    StringBuilder sb = new StringBuilder();
    int startColumn = currentColumn;
    moveNext(); // 跳过开始的引号

    while (currentChar != '"' && currentChar != '\0' && currentChar != '\n' && currentChar != '\r') {
      if (currentChar == '\\') {
        moveNext();
        switch (currentChar) {
          case 'n' -> sb.append('\n');
          case 't' -> sb.append('\t');
          case 'r' -> sb.append('\r');
          case '\\' -> sb.append('\\');
          case '"' -> sb.append('"');
          case '\'' -> {
            sb.append('\'');
            warn(String.format("不必要的转义字符'%c'-[r: %d, c: %d]", currentChar, currentLine, currentColumn));
          }
          case '0' -> sb.append('\0');
          default -> {
            error(String.format("非法的转义字符'%c'-[r: %d, c: %d]", currentChar, currentLine, currentColumn));
            sb.append(currentChar);
          }
        }
      } else {
        sb.append(currentChar);
      }
      moveNext();
    }

    if (currentChar == '\0' || currentChar == '\n' || currentChar == '\r') {
      error(String.format("未闭合的字符串-[r: %d, c: %d]", currentLine, startColumn));
    }

    moveNext(); // 跳过结束的引号
    return Token.builder()
                .value(sb.toString())
                .type(tokenManager.getType("_STRING_"))
                .line(currentLine)
                .column(startColumn)
                .build();
  }

  private void warn(String msg) {
    outInfos.warn(src, msg);
    if (errorProcess == ErrorProcess.WARN) {
      throw new RuntimeException(msg);
    }
  }

  private Token scanChar() {
    int startColumn = currentColumn;
    moveNext(); // 跳过开始的单引号

    char value;
    if (currentChar == '\\') {
      moveNext();
      switch (currentChar) {
        case 'n' -> value = '\n';
        case 't' -> value = '\t';
        case 'r' -> value = '\r';
        case '\\' -> value = '\\';
        case '\'' -> value = '\'';
        case '0' -> value = '\0';
        case '"' -> {
          value = '"';
          warn(String.format("不必要的转义字符'%c'-[r: %d, c: %d]", currentChar, currentLine, currentColumn));
        }
        default -> {
          error(String.format("非法的转义字符'%c'-[r: %d, c: %d]", currentChar, currentLine, currentColumn));
          value = currentChar;
        }
      }
      moveNext();
    } else {
      value = currentChar;
      moveNext();
    }

    if (currentChar != '\'') {
      error(String.format("字符常量必须是单个字符-[r: %d, c: %d]", currentLine, startColumn));
    }

    while (currentChar != '\'' && currentChar != '\0' && currentChar != '\n' && currentChar != '\r') {
      moveNext();
    }
    if (currentChar != '\'') {
      error(String.format("未闭合的字符常量-[r: %d, c: %d]", currentLine, startColumn));
    }

    moveNext(); // 跳过结束的引号
    return Token.builder()
                .value(String.valueOf(value))
                .type(tokenManager.getType("_CHAR_"))
                .line(currentLine)
                .column(startColumn)
                .build();
  }

  private enum ErrorProcess {
    SKIP, ERROR, WARN
  }

  private enum Radix {
    DECIMAL, HEXADECIMAL, BINARY, OCTAL
  }
}