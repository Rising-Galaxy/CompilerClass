package test;

import org.apache.commons.text.StringEscapeUtils;

public class Test {

  public static String escapeString(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      switch(c) {
        case '\n': sb.append("\\n"); break;
        case '\t': sb.append("\\t"); break;
        case '\r': sb.append("\\r"); break;
        case '\b': sb.append("\\b"); break;
        case '\f': sb.append("\\f"); break;
        case '\"': sb.append("\\\""); break;
        case '\\': sb.append("\\\\"); break;
        default:
          if (c < 32 || c > 126) {
            sb.append("\\u" + String.format("%04x", (int)c));
          } else {
            sb.append(c);
          }
      }
    }
    return sb.toString();
  }

  public static void main(String[] args) {
    char[] chars = {'A', '\n', '\t', '\uFEFF'};
    for (char c : chars) {
      System.out.println(StringEscapeUtils.escapeJava(String.valueOf(c)));
    }
  }
}
