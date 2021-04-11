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
  private static final int TIMEOUT = 3000;  // ms

  DatagramSocket socket;

  private Map<String, Session> sessions = new ConcurrentHashMap<>();
  private Map<String, Timer> timers = new ConcurrentHashMap<>();
  private Map<String,  Map<String, String>> forwards = new ConcurrentHashMap<>();

  public Server() {
    System.out.println("I: binding server to wildcard address");

    DatagramSocket socket;

    try {
      socket = new DatagramSocket(PORT);
    } catch(SocketException e) {
      System.out.println("E: failed to bind socket to local port");
      return;
    }

    System.out.println("I: listening for requests");

    while (true) {
      byte[] buffer = new byte[512];
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
      
      try {
        socket.receive(packet);
      } catch(IOException e) {
        System.out.println("E: failed to receive from socket");
        continue;
      }

      new Thread(new RequestHandler(socket, packet)).start();
    }
  }

  class RequestHandler implements Runnable {

    private DatagramSocket socket;
    private DatagramPacket packet;

    public RequestHandler(DatagramSocket socket, DatagramPacket packet) {
      this.socket = socket;
      this.packet = packet;
    }

    @Override
    public void run() {
      Map<String, String> request = Message.decode(packet.getData(), packet.getLength());

      if (!Message.validate(request)) {
        return;
      }

      if (!request.get("type").equals("register") && (request.get("token") == null || !Server.this.sessions.containsKey(request.get("token")))) {
        Map<String, String> response = new HashMap<>();
        response.put("type", "error");
        response.put("text", "Invalid or missing authentication token.");
        send(response);
        return;
      }

      switch (request.get("type")) {
        case "ack": {
          String target = request.get("target");
          if (Server.this.timers.containsKey(target)) {
            Server.this.timers.get(target).cancel();
            Server.this.timers.remove(target);
          }

          // if the ack was about a forward then signal a receipt
          if (Server.this.forwards.containsKey(target)) {
            System.out.println("Here A");
            
            Map<String, String> received = new HashMap<>();
            received.put("id", Hashing.generateRandomHash());
            received.put("type", "received");
            received.put("recipient", Server.this.forwards.get(target).get("recipient"));
            received.put("target", Server.this.forwards.get(target).get("id"));

            // TODO: send RECEIVED to sender
            for (Session s : Server.this.sessions.values()) {
              System.out.println(s.getUsername());
              System.out.println(Server.this.forwards.get(target).get("sender"));

              if (s.getUsername().equals(Server.this.forwards.get(target).get("sender"))) {
                System.out.println("SENDING RECEIVED ~~");

                send(received, s.getAddress(), s.getPort());
                resend(received, s.getAddress(), s.getPort());
                break;
              }
            }

            Server.this.forwards.remove(target);
          }

          break;
        }

        case "register": {
          // check if username is valid
          for (Session s : Server.this.sessions.values()) {
            if (s.getUsername().equals(request.get("username"))) {
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
          Server.this.sessions.put(token, session);

          // marshal and send CREDENTIALS response
          Map<String, String> response = new HashMap<>();
          response.put("id", Hashing.generateRandomHash());
          response.put("type", "credentials");
          response.put("token", token);

          send(response);
          resend(response);

          break;
        }

        case "send": {
          // check if valid recipient
          Session recipient = null;

          for (Session s : Server.this.sessions.values()) {
            if (s.getUsername().equals(request.get("recipient"))) {
              recipient = s;
            }
          }

          if (recipient == null) {
            Map<String, String> response = new HashMap<>();
            response.put("id", Hashing.generateRandomHash());
            response.put("type", "error");
            response.put("text", "Invalid recipient.");
            send(response);
            return;
          }

          // forward message to recipient
          Map<String, String> forward = new HashMap<>();
          String id = Hashing.generateRandomHash();
          forward.put("id", id);
          forward.put("type", "forward");
          forward.put("sender", Server.this.sessions.get(request.get("token")).getUsername());
          forward.put("text", request.get("text"));

          System.out.println(">>>>>> " + Server.this.sessions.get(request.get("token")).getUsername());
          
          send(forward, recipient.getAddress(), recipient.getPort());
          resend(forward, recipient.getAddress(), recipient.getPort());

          HashMap<String, String> f = new HashMap<>();
          f.put("id", request.get("id"));
          f.put("recipient", request.get("recipient"));
          f.put("sender", Server.this.sessions.get(request.get("token")).getUsername());

          Server.this.forwards.put(id, f);
          System.out.println("ADDED TO FORWARDS");

          // send acknowledgement
          Map<String, String> ack = new HashMap<>();
          ack.put("id", Hashing.generateRandomHash());
          ack.put("type", "ack");
          ack.put("target", request.get("id"));
          send(ack);

          break;
        }

        default: {
          System.out.println("E: unknown request type");
          return;
        }
      }
    }
  
    public void send(Map<String, String> m, InetAddress address, int port) {
      byte[] buffer = Message.encode(m);

      try {
        socket.send(new DatagramPacket(buffer, buffer.length, address, port));
      } catch (IOException e) {
        System.out.println("E: failed to sent packet");
      }
    }

    public void send(Map<String, String> m) {
      byte[] buffer = Message.encode(m);

      try {
        socket.send(new DatagramPacket(buffer, buffer.length, packet.getAddress(), packet.getPort()));
      } catch (IOException e) {
        System.out.println("E: failed to sent packet");
      }
    }

    public void resend(Map<String, String> m) {
      Timer timer = new Timer();
          timer.schedule(new TimerTask() {
            @Override
            public void run() {       
              send(m);
              resend(m);  // recurse
            }
          }, 3000);
          timers.put(m.get("id"), timer);
    }

    public void resend(Map<String, String> m, InetAddress address, int port) {
      Timer timer = new Timer();
          timer.schedule(new TimerTask() {
            @Override
            public void run() {       
              send(m, address, port);
              resend(m, address, port);  // recurse
            }
          }, 3000);
          timers.put(m.get("id"), timer);
    }
  }

  public static void main(String[] args) {
    new Server();
  }
}

class Session {
  private String username;
  private InetAddress address;
  private int port;
  // private boolean active;

  public Session(String username, InetAddress address, int port) {
    this.username = username;
    this.address = address;
    this.port = port;
    // this.active = false;
  }

  public String getUsername() {
    return username;
  }

  public InetAddress getAddress() {
    return address;
  }

  public int getPort() {
    return port;
  }

  // public boolean getActive() {
  //   return active;
  // }

  // public void setActive(boolean active) {
  //   this.active = active;
  // }
}
