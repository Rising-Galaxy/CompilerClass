package cn.study.compilerclass.lexer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Token类型管理器，负责加载和管理token种别码
 */
@Slf4j
public class TokenManager {

  // Token类型范围常量定义
  private static int KEYWORD_START;
  private static int KEYWORD_END;
  private static int OPERATOR_START;
  private static int OPERATOR_END;
  private static int DELIMITER_START;
  private static int DELIMITER_END;

  private final Map<String, Integer> tokenTypes;
  private final Gson gson;

  public TokenManager() {
    this.tokenTypes = new HashMap<>();
    this.gson = new Gson();
    loadTokenTypes();
  }

  private void loadTokenTypes() {
    // 获取资源流并检查是否为 null
    try (Reader reader = getResourceAsReader("/cn/study/compilerclass/conf/token_types.json")) {
      // 使用 Gson 解析 JSON 文件
      JsonObject root = gson.fromJson(reader, JsonObject.class);

      // 加载关键字、操作符、分隔符和特殊类型的定义和范围
      loadTokenGroup(root.getAsJsonObject("keywords"), "keywords"); // 关键字类型
      loadTokenGroup(root.getAsJsonObject("operators"), "operators"); // 操作符类型
      loadTokenGroup(root.getAsJsonObject("delimiters"), "delimiters"); // 分隔符类型
      loadTokenGroup(root.getAsJsonObject("special"), "special"); // 特殊类型

      // 记录加载成功的 token 类型数量
      log.info("成功加载 {} 个 token 类型定义", tokenTypes.size());
    } catch (IOException e) {
      // 如果加载过程中发生 IO 异常，则记录错误日志并抛出异常
      log.error("加载 token 类型定义失败", e);
      throw new RuntimeException("加载 token 类型定义失败，请检查配置文件", e);
    }
  }

  /**
   * 获取资源文件并封装为 Reader，若资源不存在则抛出异常
   *
   * @param resourcePath 资源路径
   * @return Reader 对象
   * @throws RuntimeException 如果资源路径无效或文件不存在
   */
  private Reader getResourceAsReader(String resourcePath) {
    var inputStream = getClass().getResourceAsStream(resourcePath);
    if (inputStream == null) {
      throw new RuntimeException("无法找到资源文件: " + resourcePath + "，请检查资源路径");
    }
    return new InputStreamReader(inputStream);
  }

  private void loadTokenGroup(JsonObject group, String groupType) {
    if (group != null) {
      int min = Integer.MAX_VALUE;
      int max = Integer.MIN_VALUE;

      for (Map.Entry<String, JsonElement> entry : group.entrySet()) {
        int value = entry.getValue().getAsInt();
        tokenTypes.put(entry.getKey(), value);

        // 动态计算极值
        if (value < min) {
          min = value;
        }
        if (value > max) {
          max = value;
        }
      }

      // 按分组类型设置范围
      switch (groupType) {
        case "keywords":
          KEYWORD_START = min;
          KEYWORD_END = max;
          break;
        case "operators":
          OPERATOR_START = min;
          OPERATOR_END = max;
          break;
        case "delimiters":
          DELIMITER_START = min;
          DELIMITER_END = max;
          break;
      }
    }
  }

  public int getType(String token) {
    return tokenTypes.getOrDefault(token, 65);
  }

  public boolean isKeyword(String token) {
    Integer type = tokenTypes.get(token);
    return type != null && type >= KEYWORD_START && type <= KEYWORD_END;
  }

  public boolean isOperator(String token) {
    Integer type = tokenTypes.get(token);
    return type != null && type >= OPERATOR_START && type <= OPERATOR_END;
  }

  public boolean isDelimiter(String token) {
    Integer type = tokenTypes.get(token);
    return type != null && type >= DELIMITER_START && type <= DELIMITER_END;
  }
}
