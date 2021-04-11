import java.nio.charset.StandardCharsets;

public abstract class Message {

  private static int LEN_DIGITS = 8;

  private String key;

  public Message(String key) {
    this.key = key;
  }

  public String getKey() {
    return key;
  }

  public String encodeStringLength(String s) {
    return String.format("%0" + LEN_DIGITS + "d", s.length());
  }

  abstract byte[] encode();

  public static Message decode(byte[] buffer) {
    String raw = new String(buffer, StandardCharsets.UTF_8);
    int current = 0;

    int keyLength = Integer.parseInt(raw.substring(current, current + LEN_DIGITS));
    current += LEN_DIGITS;

    System.out.println(keyLength);

    String key = raw.substring(current, current + keyLength);
    current += keyLength;

    System.out.println(key);

    switch (key) {
      case "register":
        RegisterMessage m = new RegisterMessage();

        int hashLength = Integer.parseInt(raw.substring(current, current + LEN_DIGITS));
        current += LEN_DIGITS;
        String hash = raw.substring(current, current + hashLength);
        current += hashLength;
        m.setHash(hash);

        int usernameLength = Integer.parseInt(raw.substring(current, current + LEN_DIGITS));
        current += LEN_DIGITS;
        String username = raw.substring(current, current + usernameLength);
        m.setUsername(username);

        return m;
    }

    return null;
  }

  public static void main(String[] args) {
    RegisterMessage m = new RegisterMessage();
    m.setUsername("joe");

    byte[] b = m.encode();

    Message n = Message.decode(b);
  }
}

// sent from client to server to register new client
class RegisterMessage extends Message {

  private String username;
  private String hash;

  public RegisterMessage() {
    super("register");
  }

  // LEN register LEN hash LEN username
  public byte[] encode() {    
    StringBuilder body = new StringBuilder();

    body.append(super.encodeStringLength(username));
    body.append(username);
    
    String hash = Hashing.generateHash(body.toString());

    StringBuilder header = new StringBuilder();

    header.append(super.encodeStringLength(super.getKey()));
    header.append(super.getKey());
    header.append(super.encodeStringLength(hash));
    header.append(hash);
    header.append(body.toString());

    return header.toString().getBytes(StandardCharsets.UTF_8);
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getUsername() {
    return username;
  }

  public void setHash(String hash) {
    this.hash = hash;
  }

  public String getHash() {
    return hash;
  }
}


