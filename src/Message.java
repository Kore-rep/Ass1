import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;

public class Message {

  private final static String VERSION = "1.0";

  public static byte[] encode(Map<String, String> map) {
    if (map.get("type") == null) {
      return null;
    }

    if (map.get("id") == null) {
      map.put("id", Hashing.generateRandomHash());
    }

    StringBuilder builder = new StringBuilder();

    builder.append("CHATTER/" + VERSION + "\r\n");

    List<String> keys = new ArrayList<>(map.keySet());
    Collections.sort(keys);

    for (String key : keys) {
      builder.append(key + ":" + map.get(key) + "\r\n");
    }

    String hash = Hashing.generateHash(builder.toString());

    builder.append("hash:" + hash);

    // DEBUG
    System.out.println("SENDING\n");
    System.out.println(builder.toString());
    System.out.println("\n");

    return builder.toString().getBytes(StandardCharsets.UTF_8);
  }

  public static Map<String, String> decode(byte[] bytes, int length) {
    // DEBUG
    System.out.println("RECEIVED\n");
    System.out.println(new String(bytes, 0, length, StandardCharsets.UTF_8));
    System.out.println("\n");

    String[] lines = new String(bytes, 0, length, StandardCharsets.UTF_8).split("\r\n");;

    if (lines.length < 3) {
      return null;
    }

    if (!VERSION.equals(lines[0].split("/")[1])) {
      return null;
    }

    Map<String, String> map = new HashMap<>();

    for (int i = 1; i < lines.length; i++) {
      String[] line = lines[i].split(":");
      map.put(line[0], line[1]);
    }

    return map;
  }

  public static boolean validate(Map<String, String> map) {
    if (map.get("type") == null) {
      return false;
    }

    String hash = map.remove("hash");

    StringBuilder builder = new StringBuilder();

    builder.append("CHATTER/" + VERSION + "\r\n");

    List<String> keys = new ArrayList<>(map.keySet());
    Collections.sort(keys);

    for (String key : keys) {
      builder.append(key + ":" + map.get(key) + "\r\n");
    }

    return Hashing.checkHash(hash, builder.toString());
  }

  public static void main(String[] args) {
    Map<String, String> m = new HashMap<>();

    m.put("type", "1234");
    m.put("hello", "adasdads");

    byte[] b = Message.encode(m);

    System.out.println(b);

    Map<String, String> n = Message.decode(b, b.length);

    System.out.println(n.get("id"));
    System.out.println(n.get("type"));
    System.out.println(n.get("hello"));
    System.out.println(n.get("hash"));

    System.out.println(Message.validate(n));
  }
}
