import java.util.Formatter;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hashing {

  public static String generateRandomHash() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  public static String generateHash(String body) {
    MessageDigest md;

    try {
      md = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      return null;
    } 

    md.reset();
    md.update(body.getBytes(StandardCharsets.UTF_8));

    byte[] raw = md.digest();

    // convert to hex string
    Formatter formatter = new Formatter();
    
    for (byte b : raw) {
      formatter.format("%02x", b);
    }
    
    String hash = formatter.toString();

    formatter.close();

    return hash;
  }

  public static boolean checkHash(String hash, String body) {
    return hash.equals(generateHash(body));
  }
}
