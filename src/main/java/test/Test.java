package test;

public class Test {

  public static void main(String[] args) {
    String str = "hello\'world";
    str = str.replace("\0", "666");
    System.out.println(str);
  }
}
