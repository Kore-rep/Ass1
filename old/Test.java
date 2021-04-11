
public class Test {

  private static String token;

  public static void main(String[] args) {
    new B(token);

    System.out.println(token);
  }
}

class A {

  class B {
    
  }
}
