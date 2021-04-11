import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.net.InetAddress;

import java.io.IOException;

import java.security.MessageDigest;


/**
 * Server represents the terminal program that communicates with client processes via the chatter application-level protocol.
 * @version 1.0
 */
public class Server {

  private static final int PORT = 8888;
  private static final int TIMEOUT = 3000;  // ms

  DatagramSocket socket;

  private Map<String, Session> sessions = new ConcurrentHashMap<>();
  private Map<String, Timer> timers = new ConcurrentHashMap<>();
  private Map<String,  Map<String, String>> forwards = new ConcurrentHashMap<>();

  private Map<String,  Long> elapsed = new ConcurrentHashMap<>();
  private AtomicLong estimated_rtt = new AtomicLong(1000);  // ms
  private AtomicLong dev_rtt = new AtomicLong(1000);

  /**
   * Default constructor for Server that binds the server to a port, and starts a loop to listen for requests.
   */
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

  /**
   * Server's RequestHandler is run on a seperate thread to deal with a single request, after which it terminates.
   * Dealing with requests may include sending packets.
   * 
   * @version 1.0
   */
  class RequestHandler implements Runnable {

    /**
     * 
     */
    private DatagramSocket socket;

    /**
     * 
     */
    private DatagramPacket packet;

    /**
     * Custom constructor for Request Handler.
     * @param socket Socket that this thread recieved its request from.
     * @param packet Packet of data in the request.
     */
    public RequestHandler(DatagramSocket socket, DatagramPacket packet) {
      this.socket = socket;
      this.packet = packet;
    }

    /**
     * Called when this object is created.
     * Once this method has finished the thread terminates.
     */
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

          // ELAPSED
          if (Server.this.elapsed.containsKey(target)) {
            long sample_rtt = System.currentTimeMillis() - Server.this.elapsed.get(target);
            
            long estimated_rtt = Server.this.estimated_rtt.getAndUpdate((x) -> (long) ((1 - 0.125) * x + 0.125 * sample_rtt));
            
            Server.this.dev_rtt.getAndUpdate((x) -> (long) ((1 - 0.25) * x + 0.25 * Math.abs(sample_rtt - estimated_rtt)));
            
            Server.this.elapsed.remove(target);
          }

          // if the ack was about a forward then signal a receipt
          if (Server.this.forwards.containsKey(target)) {
            System.out.println("Here A");
            
            Map<String, String> received = new HashMap<>();
            received.put("id", Hashing.generateRandomHash());
            received.put("type", "received");
            received.put("recipient", Server.this.forwards.get(target).get("recipient"));
            received.put("target", Server.this.forwards.get(target).get("id"));

            // send RECEIVED to sender
            for (Session s : Server.this.sessions.values()) {
              System.out.println(s.getUsername());
              System.out.println(Server.this.forwards.get(target).get("sender"));

              if (s.getUsername().equals(Server.this.forwards.get(target).get("sender"))) {
                // System.out.println("SENDING RECEIVED ~~");

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
  
    /**
     * Overloaded send(m) method.
     * Attempts to send a message to the specified address:port combo.
     * @param m Message to send.
     * @param address Address of the recipient.
     * @param port Port to use.
     */
    public void send(Map<String, String> m, InetAddress address, int port) {
      // ELAPSED
      Server.this.elapsed.put(m.get("id"), System.currentTimeMillis());

      byte[] buffer = Message.encode(m);

      try {
        socket.send(new DatagramPacket(buffer, buffer.length, address, port));
      } catch (IOException e) {
        System.out.println("E: failed to sent packet");
      }
    }


    /**
     * Attempts to send a message.
     * @param m Message to send.
     */
    public void send(Map<String, String> m) {
      byte[] buffer = Message.encode(m);

      try {
        socket.send(new DatagramPacket(buffer, buffer.length, packet.getAddress(), packet.getPort()));
      } catch (IOException e) {
        System.out.println("E: failed to sent packet");
      }
    }

    /**
     * Attempts to resend a message.
     * @param m Message to resend.
     */
    public void resend(Map<String, String> m) {
      Timer timer = new Timer();
          timer.schedule(new TimerTask() {
            @Override
            public void run() {       
              send(m);
              resend(m);  // recurse
            }
          }, get_timeout());
          timers.put(m.get("id"), timer);
    }

    /**
     * Overloaded resend(m) method.
     * Attempts to resend a message to a specified address:port combo.
     * @param m Message to resend
     * @param address Address of recipient
     * @param port Port of recipient
     */
    public void resend(Map<String, String> m, InetAddress address, int port) {
      Timer timer = new Timer();
          timer.schedule(new TimerTask() {
            @Override
            public void run() {       
              send(m, address, port);
              resend(m, address, port);  // recurse
            }
          }, get_timeout());
          timers.put(m.get("id"), timer);
    }

    /**
     * Calculates a new timeout value based on sample sending and reciving times.
     * @return Timeout value.
     */
    private long get_timeout() {
      return estimated_rtt.get() + 4 * dev_rtt.get();
    }
  }

  public static void main(String[] args) {
    // entry point
    new Server();
  }
}

/**
 * Session represents a single user session along with relevant identifiers and network information.
 *
 * @version     1.0
 */
class Session {
  private String username;
  private InetAddress address;
  private int port;
  // private boolean active;

  /**
   * Custom constructor for Session
   * @param username User name
   * @param address Address of user
   * @param port Port of user
   */
  public Session(String username, InetAddress address, int port) {
    this.username = username;
    this.address = address;
    this.port = port;
    // this.active = false;
  }

  /**
   * 
   * @return User's name.
   */
  public String getUsername() {
    return username;
  }

  /**
   * 
   * @return User's address.
   */
  public InetAddress getAddress() {
    return address;
  }

  /**
   * 
   * @return Users port.
   */
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
