import java.util.Map;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;

import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.io.IOException;

/**
 * Client represents the graphical client program that communicates with a server process via the chatter application-level protocol.
 *
 * @version     1.0
 */
public class Client {

  /**
   * IPv4 network address of chatter server.
   */
  private static final String SERVER_ADDRESS = "127.0.0.1";

  /**
   * Port number of chatter server.
   */
  private static final int SERVER_PORT = 8888;

  DatagramSocket socket;
  InetAddress serverAddress;  
  String token;
  String username;
  
  Map<String, Timer> timers = new ConcurrentHashMap<>();
  Map<String, List<ChatLine>> conversations = new ConcurrentHashMap<>();
  Map<String, Map<String, String>> sends = new ConcurrentHashMap<>();
  
  /**
   * Default constructor for Client that resolves the server IP, binds the client to a socket, initializes the GUI and starts a RequestHandler Thread.
   */
  public Client() {
    // resolve server address
    System.out.println("I: resolving server address");

    try {
      serverAddress = InetAddress.getByName(SERVER_ADDRESS);
    } catch (UnknownHostException e) {
      System.out.println("E: failed to resolve server address");
      System.exit(1);
    }

    // bind to local ephemeral port
    System.out.println("I: binding socket to ephemeral port");

    try {
      Client.this.socket = new DatagramSocket();
    } catch(SocketException e) {
      System.out.println("E: failed to bind socket to local ephemeral port");
      return;
    }

    // initialize window and start graphical user interface
    System.out.println("I: starting gui application");

    Frame frame = new Frame();
    

    JFrame window = new JFrame("Chatter");
    window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    window.getContentPane().add(frame);
    window.pack();
    window.setVisible(true);

    // start thread to handle messages from server
    System.out.println("I: starting request handler thread");
    
    new Thread(new RequestHandler(frame)).start();
  }

  /**
   * Frame represents the graphical window along with the various graphical components that make up the user interface.
   *
   * @version     1.0
   */
  class Frame extends JPanel {
    
    private JLabel title;
    private JLabel username;
    private JTextField registerInput;
    private JButton registerButton;
    private JList<String> contactList;
    private JTextField addContactInput;
    private JButton addContactButton;
    private JTextArea conversationArea;
    private JTextField sendMessageInput;
    private JButton sendMessageButton;

    /**
     * Default constructor for Frame that creates required GUI and binds functionality to it.
     */
    public Frame() {
      setPreferredSize(new Dimension(550, 340));
      setLayout(null);

      // create title
      title = new JLabel("Chatter", JLabel.CENTER);
      title.setFont(new Font("Verdana", Font.PLAIN, 20));

      username = new JLabel("", JLabel.CENTER);
      username.setFont(new Font("Verdana", Font.PLAIN, 14));
      username.setVisible(false);

      // create register text field and button
      registerInput = new JTextField();
      registerButton = new JButton("reg");
      registerButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent event) {
          // skip if there is no username
          if (registerInput.getText().equals("")) {
            return;
          }

          // marshal and send REGISTER request
          Map<String, String> request = new HashMap<>();
          request.put("id", Hashing.generateRandomHash());
          request.put("type", "register");
          request.put("username", registerInput.getText());

          Client.this.send(request);

          // clear application state
          clearState();

          // clear register input
          registerInput.setText("");
        }
      });
      
      // create contact list with scrollbar
      JScrollPane contactListScroll = new JScrollPane();
      contactList = new JList<>();
      contactListScroll.setViewportView(contactList);
      contactList.addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent listSelectionEvent) {
          // allow user to select conversation by username
          refreshConversations();
        }
      });

      // add contact
      addContactInput = new JTextField();
      addContactButton = new JButton("+");
      addContactButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (addContactInput.getText().equals("") || Client.this.conversations.containsKey(addContactInput.getText())) {
            return;
          }

          Client.this.conversations.put(addContactInput.getText(), new ArrayList<>());
          addContactInput.setText("");

          refreshContactsList();
        }
      });

      // conversation area
      conversationArea = new JTextArea();
      conversationArea.setEditable(false);

      JScrollPane conversationAreaScroll = new JScrollPane();
      conversationAreaScroll.setViewportView(conversationArea);

      // send
      sendMessageInput = new JTextField();
      sendMessageButton = new JButton("snd");
      sendMessageButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent event) {
          if (sendMessageInput.getText().equals("")) {
            return;
          }

          Map<String, String> request = new HashMap<>();
          String id = Hashing.generateRandomHash();
          request.put("id", id);
          request.put("type", "send");
          request.put("recipient", contactList.getSelectedValuesList().get(0));
          request.put("text", sendMessageInput.getText());

          ChatLine m = new ChatLine(id, Client.this.username, sendMessageInput.getText());
          Client.this.conversations.get(contactList.getSelectedValuesList().get(0)).add(m);

          Client.this.send(request);
          Client.this.resend(request);

          Client.this.sends.put(id, request);

          sendMessageInput.setText("");

          refreshConversations();
        }
      });

      add(title);
      add(username);
      add(registerInput);
      add(registerButton);
      add(contactListScroll);
      add(addContactInput);
      add(addContactButton);
      add(conversationAreaScroll);
      add(sendMessageInput);
      add(sendMessageButton);

      // set component bounds
      title.setBounds                  (10,  10,  230, 25);
      username.setBounds               (270, 10,  200, 25);
      registerInput.setBounds          (270, 10,  200, 25);
      registerButton.setBounds         (480, 10,  60,  25);
      contactListScroll.setBounds      (10,  45,  170, 250);
      addContactInput.setBounds        (10,  305, 100, 25);
      addContactButton.setBounds       (120, 305, 60,  25);
      conversationAreaScroll.setBounds (190, 45,  350, 250);
      sendMessageInput.setBounds       (190, 305, 280, 25);
      sendMessageButton.setBounds      (480, 305, 60,  25);
    }

    /**
     * Empties all containers of their contents and clears timers.
     */

    public void clearState() {
      Client.this.conversations.clear();
      Client.this.sends.clear();
      for (Timer t : Client.this.timers.values()) {
        t.cancel();
      }
      Client.this.timers.clear();
      Client.this.username = registerInput.getText();
    }

    /**
     * Loops through the current list of conversations, build a model list of contacts.
     * who have conversations and finally set the display contact list to this model.
     */
    public void refreshContactsList() {
      DefaultListModel<String> model = new DefaultListModel<>();
      List<String> list = new ArrayList<>(Client.this.conversations.keySet());
      for (int i = 0; i < list.size(); i++) {
        model.add(i, list.get(i));
      }
      contactList.setModel(model);
    }

    /**
     * Refreshes the contents of the currently selected contact's conversation by looping through stored ChatLines.
     */
    public void refreshConversations() {
      if (contactList.getSelectedValuesList().size() > 0) {
        String s = contactList.getSelectedValuesList().get(0);

        StringBuilder builder = new StringBuilder();

        for (ChatLine m : Client.this.conversations.get(s)) {
          System.out.println("TRACE!!");
          builder.append("(" + (m.isReceivedByServer() ? "*" : "?") + (m.isReceivedByClient() ? "*" : "?") + ") " + "[" + m.getSender() + "] " + m.getText() + "\n");
        }

        conversationArea.setText("");
        conversationArea.setText(builder.toString());
      }
    }
  }

  /**
   * Client.java's RequestHandler runs on a seperate thread, and waits for datagrams, then deals with them according to the type recieved.
   * It can deal with multiple requests in a row.
   * 
   * @version 1.0
   */
  class RequestHandler implements Runnable {

    private Frame frame;

    /**
     *  Custom constructor for Client's Request Handler.
     * @param frame A frame to push GUI changes (like new messages) to.
     */
    public RequestHandler(Frame frame) {
      this.frame = frame;
    }

    /**
     * Called when this object is created.
     * Once this method has finished the thread terminates.
     */
    @Override
    public void run() {
      while (true) {
        byte[] buffer = new byte[512];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        
        try {
          Client.this.socket.receive(packet);
        } catch(IOException e) {
          System.out.println("E: failed to receive from socket");
          continue;
        }

        Map<String, String> request = Message.decode(buffer, packet.getLength());

        if (!Message.validate(request)) {
          System.out.println("E: invalid message received");
          continue;
        }

        switch(request.get("type")) {
          case "ack": {
            String id = request.get("target");

            if (Client.this.timers.containsKey(id)) {
              Client.this.timers.get(id).cancel();
              Client.this.timers.remove(id);
            }

            if (Client.this.sends.containsKey(id)) {
              // TODO: update relevant record with first tick [You] (**) Hello
              Map<String, String> s = Client.this.sends.get(id);
              if (Client.this.conversations.containsKey(s.get("recipient"))) {
                for (ChatLine m : Client.this.conversations.get(s.get("recipient"))) {
                  if (id.equals(m.getId())) {
                    m.setReceivedByServer(true);
                    break;
                  }
                }
              }
              frame.refreshConversations();
              Client.this.sends.remove(id);
            }

            break;
          }

          case "error": {
            JOptionPane.showMessageDialog(null, request.get("text"), "Error", JOptionPane.INFORMATION_MESSAGE);
            break;
          }

          case "credentials": {
            Client.this.token = request.get("token");
            Map<String, String> response = new HashMap<>();
            response.put("id", Hashing.generateRandomHash());
            response.put("type", "ack");
            response.put("target", request.get("id"));
            Client.this.send(response);

            frame.registerInput.setVisible(false);
            frame.registerButton.setVisible(false);
            frame.username.setText("Logged in as: " + Client.this.username);
            frame.username.setVisible(true);
            
            JOptionPane.showMessageDialog(null, "Registration successful.", "Info", JOptionPane.INFORMATION_MESSAGE);

            break;
          }
          
          case "forward": {
            if (Client.this.conversations.get(request.get("sender")) == null) {
              Client.this.conversations.put(request.get("sender"), new ArrayList<>());  // TODO: List
              frame.refreshContactsList();
            }

            Client.this.conversations.get(request.get("sender")).add(new ChatLine(request.get("id"), request.get("sender"), request.get("text"), true, true));
            
            Map<String, String> ack = new HashMap<>();
            ack.put("id", Hashing.generateRandomHash());
            ack.put("type", "ack");
            ack.put("target", request.get("id"));
            Client.this.send(ack);

            frame.refreshConversations();
            break;
          }

          case "received": {
            // System.out.println("TRACE: GOT RECEIVED");

            // TODO: loop through messages and update received
            if (Client.this.conversations.containsKey(request.get("recipient"))) {
              // System.out.println(Client.this.conversations.get(request.get("recipient")));

              for (ChatLine m : Client.this.conversations.get(request.get("recipient"))) {
                // System.out.println(m.getId());

                if (request.get("target").equals(m.getId())) {
                  // System.out.println("TRACE: GOT RECEIVED 2");
                  m.setReceivedByClient(true);
                  // m.setReceivedByServer(true);
                  break;
                }
              }

              frame.refreshConversations();
            }

            Map<String, String> ack = new HashMap<>();
            ack.put("id", Hashing.generateRandomHash());
            ack.put("type", "ack");
            ack.put("target", request.get("id"));
            Client.this.send(ack);

            frame.refreshConversations();
            break;
          }

          default: {
            System.out.println("E: unknown message received");
            break;
          }
        }
      }
    }
  }

  /**
   * Attempts to send a given message to the client's server.
   * @param m The message to be sent.
   */
  public void send(Map<String, String> m) {
    // middleware to attach token
    if (token != null) {
      m.put("token", token);
    }

    byte[] buffer = Message.encode(m);

    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, SERVER_PORT);
    
    try {
      socket.send(packet);
    } catch (IOException e) {
      System.out.println("E: failed to sent packet");
    }
  }

  /**
   * Attempts to resend a given message.
   * @param m Message to be resent.
   * 
   */
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

  public static void main (String[] args) {
    new Client();
  }
}

/**
  * ChatLine represents a message in a conversation between two clients. 
  *
  * @version     1.0
  */
class ChatLine {

  private String id;
  private String text;
  private String sender;
  private boolean receivedByServer;
  private boolean receivedByClient;

  /**
   * Custom constructor for a Chatline.
   * @param id Message ID.
   * @param sender Name of message sender.
   * @param text Contents of message.
   */
  public ChatLine(String id, String sender, String text) {
    this.id = id;
    this.sender = sender;
    this.text = text;
    this.receivedByServer = false;
    this.receivedByClient = false;
  }


  /**
   * Overloaded constructor for a ChatLine.
   * @param id ChatLine ID.
   * @param sender Name of ChatLine sender.
   * @param text Contents of ChatLine.
   * @param receivedByServer Boolean indicating if the server has recieved this ChatLine.
   * @param receivedByClient Boolean indicating if the client has recieved this ChatLine.
   */
  public ChatLine(String id, String sender, String text, boolean receivedByServer, boolean receivedByClient) {
    this.id = id;
    this.sender = sender;
    this.text = text;
    this.receivedByServer = receivedByServer;
    this.receivedByClient = receivedByClient;
  }

  /**
   * ID get() method.
   * @return ChatLine ID.
   */
  public String getId() {
    return id;
  }

  /**
   * Sender get() method.
   * @return ChatLine Sender.
   */
  public String getSender() {
    return sender;
  }

  /**
   * Message contents get() method.
   * @return ChatLine contents.
   */
  public String getText() {
    return text;
  }

  /**
   * Server recieved get() method.
   * @return True if this ChatLine has been recieved by the server, otherwise false.
   */
  public boolean isReceivedByServer() {
    return receivedByServer;
  }

  /**
   * Server Recived set() method.
   * @param receivedByServer A boolean of whether or not this ChatLine has been recieved by the server.
   */
  public void setReceivedByServer(boolean receivedByServer) {
    this.receivedByServer = receivedByServer;
  }

  /**
   * Client recieved get() method.
   * @return True if this ChatLine has been recieved by the client, otherwise false.
   */
  public boolean isReceivedByClient() {
    return receivedByClient;
  }

  /**
   * Client recieved set() method.
   * @param receivedByClient boolean of whether or not this ChatLine has been recieved by the server.
   */
  public void setReceivedByClient(boolean receivedByClient) {
    this.receivedByClient = receivedByClient;
  }
}
