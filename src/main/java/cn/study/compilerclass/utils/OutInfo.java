package cn.study.compilerclass.utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OutInfo {

  private final StringBuilder outInfos;
  private boolean hasError;

  private enum OutType {
    ERROR, WARN, INFO
  }

  public OutInfo() {
    outInfos = new StringBuilder();
    hasError = false;
  }

  public boolean hasError() {
    return hasError;
  }

  private void add(String src, OutType outType, String msg) {
    outInfos.append(String.format("[%s]-[%s] %s%n", src, outType, msg));
  }

  public void error(String src, String msg) {
    hasError = true;
    add(src, OutType.ERROR, msg);
    log.error("[{}]-[{}] {}", src, OutType.ERROR, msg);
  }

  public void error(String src, String msg, Exception e) {
    hasError = true;
    add(src, OutType.ERROR, msg);
    log.error("[{}]-[{}] {}", src, OutType.ERROR, msg, e);
  }

  public void warn(String src, String msg) {
    add(src, OutType.WARN, msg);
    log.warn("[{}]-[{}] {}", src, OutType.WARN, msg);
  }

  public void info(String src, String msg) {
    add(src, OutType.INFO, msg);
    log.info("[{}]-[{}] {}", src, OutType.INFO, msg);
  }

  public void clear() {
    outInfos.delete(0, outInfos.length());
  }

  public String toString() {
    return outInfos.toString();
  }

  public boolean isEmpty() {
    return outInfos.isEmpty();
  }
}
