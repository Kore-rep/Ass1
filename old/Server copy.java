import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.net.InetAddress;

import java.io.IOException;

import java.security.MessageDigest;

public class Server {

  private static final int PORT = 8888;

  private static Map<String, Session> sessions = new ConcurrentHashMap<>();
  private static Map<String, Timer> timers = new ConcurrentHashMap<>();
  private static Map<String,  Map<String, String>> forwards = new ConcurrentHashMap<>();

  public static void main(String[] args) {
    System.out.println("I: server starting");

    DatagramSocket socket;

    try {
      socket = new DatagramSocket(PORT);
    } catch(SocketException e) {
      System.out.println("E: failed to bind socket to local port " + PORT);
      return;
    }

    while (true) {
      byte[] buffer = new byte[512];
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
      
      try {
        socket.receive(packet);
      } catch(IOException e) {
        System.out.println("failed to receive from socket");
        continue;
      }

      new Thread(new RequestHandler(socket, sessions, timers, forwards, packet)).start();
    }
  }
}

class RequestHandler implements Runnable {

  private static int TIMEOUT = 3000;  // ms

  private DatagramSocket socket;
  private Map<String, Session> sessions;
  private Map<String, Timer> timers;
  private DatagramPacket packet;

  public RequestHandler(DatagramSocket socket, Map<String, Session> sessions, Map<String, Timer> timers, Map<String,  Map<String, String>> forwards, DatagramPacket packet) {
    this.socket = socket;
    this.sessions = sessions;
    this.timers = timers;
    this.packet = packet;
  }

  @Override
  public void run() {
    Map<String, String> request = Message.decode(packet.getData(), packet.getLength());

    if (!Message.validate(request)) {
      return;
    }

    if (!request.get("type").equals("register") && (request.get("token") == null || !sessions.containsKey(request.get("token")))) {
      Map<String, String> response = new HashMap<>();
      response.put("type", "error");
      response.put("text", "Invalid or missing authentication token.");
      send(response);
      return;
    }

    switch (request.get("type")) {
      case "ack":
        String target = request.get("target");
        if (timers.containsKey(id)) {
          timers.get(id).cancel();
          timers.remove(id);
        }

        // if the ack was about a forward then signal a receipt
        if (forwards.containsKey(target)) {
          Map<String, String> received = new HashMap<>();
          received.put("id", Hashing.generateRandomHash());
          received.put("sender", forwards.get(target).get("sender"));
          received.put("target", forwards.get(target).get("id"))
          send(received);
          resend(received);
          forwards.remove(target);
        }

        break;

      case "register":
        // check if username is valid
        for (Session s : sessions.values()) {
          if (s.getUsername().equals(request.get("username") && s.getActive())) {
            Map<String, String> response = new HashMap<>();
            response.put("type", "error");
            response.put("text", "Username already in use.");
            send(response);
            return;
          }
        }

        // add to sessions
        Session session = new Session(request.get("username"), packet.getAddress(), packet.getPort());
        String token = Hashing.generateRandomHash();
        sessions.put(token, session);

        Map<String, String> response = new HashMap<>();
        response.put("id", Hashing.generateRandomHash());
        response.put("type", "credentials");
        response.put("token", token);
        send(response);
        resend(response);
        break;
      
      case "send":
        // check if valid recipient
        Session recipient;

        for (Session s : sessions.values()) {
          if (s.getUsername().equals(request.get("recipient"))) {
            recipient = s;
          }
        }

        if (!recipient) {
          Map<String, String> response = new HashMap<>();
          response.put("id", Hashing.generateRandomHash());
          response.put("type", "error");
          response.put("text", "Invalid recipient.");
          send(response);
        }

        // forward message to recipient
        Map<String, String> forward = new HashMap<>();
        String id = Hashing.generateRandomHash();
        forward.put("id", id);
        forward.put("type", "forward");
        forward.put("sender", sessions.get(request.get("token")).getUsername());
        forward.put("text", request.get("text"));
        send(forward);
        resend(forward);

        forwards.put(id, request);

        // send acknowledgement
        Map<String, String> ack = new HashMap<>();
        ack.put("id", Hashing.generateRandomHash());
        ack.put("type", "ack");
        ack.put("target", request.get("id"));
        send(ack);

        break;
    }
  }

  private void send(Map<String, String> map) {
    byte[] buffer = Message.encode(map);
    DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length, packet.getAddress(), packet.getPort());
    
    try {
      socket.send(responsePacket);
    } catch (IOException e) {
      System.out.println("E: failed to sent packet");
    }
  }

  private void resend(Map<String, String> map) {
    Timer timer = new Timer();
        timer.schedule(new TimerTask() {
          @Override
          public void run() {       
            send(map);
            resend(map);  // recurse
          }
        }, 3000);
        timers.put(map.get("id"), timer);
  }
} 

class Session {
  private String username;
  private InetAddress address;
  private int port;
  private boolean active;

  public Session(String username, InetAddress address, int port) {
    this.username = username;
    this.address = address;
    this.port = port;
  }

  public String getUsername() {
    return username;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}

// packet.getAddress(), packet.getPort(), packet.getData(), packet.getLength()
