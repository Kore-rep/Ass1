import java.util.Map;
import java.util.HashMap;
import java.util.Timer;
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

class State {
  public DatagramSocket socket;
  public String token;
  public Map<String, Timer> timers = new ConcurrentHashMap<>();
  public Map<String, String> conversations = new ConcurrentHashMap<>();
  public Map<String, Map<String, String>> sends = new ConcurrentHashMap<>();
  public InetAddress serverAddress;
  public int serverPort;

  public void send(Map<String, String> m) {
    // middleware to attach token
    if (token != null) {
      m.put("token", token);
    }

    byte[] buffer = Message.encode(m);

    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, serverPort);
    
    try {
      socket.send(packet);
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

public class Client {

  public static void main (String[] args) {
    State state = new State();
    
    try {
      state.serverAddress = InetAddress.getByName("127.0.0.1");
      state.serverPort = 8888;
    } catch (UnknownHostException e) {
      System.out.println("E: failed to resolve server address");
      return;
    }

    try {
      state.socket = new DatagramSocket();
    } catch(SocketException e) {
      System.out.println("E: failed to bind socket to local ephemeral port");
      return;
    }

    Frame f = new Frame(state);

    JFrame frame = new JFrame("Chatter");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.getContentPane().add(f);
    frame.pack();
    frame.setVisible(true);

    new Thread(new Handler(state, f)).start();
  }
}

class Frame extends JPanel {

  private State state;
  
  private JLabel title;
  private JTextField registerInput;
  private JButton registerButton;
  private JList<String> contactList;
  private JTextField addContactInput;
  private JButton addContactButton;
  private JTextArea conversationArea;
  private JTextField sendMessageInput;
  private JButton sendMessageButton;

  public Frame(State state) {
    this.state = state;

    init();
  }

  public void refreshContactsList() {
    DefaultListModel<String> model = new DefaultListModel<>();
    List<String> list = new ArrayList<>(state.conversations.keySet());
    for (int i = 0; i < list.size(); i++) {
      model.add(i, list.get(i));
    }
    contactList.setModel(model);
  }

  public void init() {
    setPreferredSize(new Dimension(550, 340));
    setLayout(null);

    // title
    title = new JLabel ("Chatter", JLabel.CENTER);
    title.setFont(new Font("Verdana", Font.PLAIN, 20));

    // register
    registerInput = new JTextField();
    registerButton = new JButton("reg");
    registerButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        if (registerInput.getText().equals("")) {
          return;
        }

        Map<String, String> request = new HashMap<>();
        request.put("id", Hashing.generateRandomHash());
        request.put("type", "register");
        request.put("username", registerInput.getText());

        state.send(request);

        state.conversations.clear();
        registerInput.setText("");
      }
    });
    
    // contact list
    JScrollPane contactListScroll = new JScrollPane();
    contactList = new JList<>();
    contactListScroll.setViewportView(contactList);
    contactList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent listSelectionEvent) {
        String s = contactList.getSelectedValuesList().get(0);
        conversationArea.setText(state.conversations.get(s));
      }
    });

    // add contact
    addContactInput = new JTextField();
    addContactButton = new JButton("add");
    addContactButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (addContactInput.getText().equals("") || state.conversations.containsKey(addContactInput.getText())) {
          return;
        }

        state.conversations.put(addContactInput.getText(), "");
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
    registerButton.addActionListener(new ActionListener() {
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

        state.send(request);
        state.resend(request);

        state.sends.put(id, request);

        sendMessageInput.setText("");
      }
    });

    add(title);
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
    registerInput.setBounds          (270, 10,  200, 25);
    registerButton.setBounds         (480, 10,  60,  25);
    contactListScroll.setBounds      (10,  45,  170, 250);
    addContactInput.setBounds        (10,  305, 100, 25);
    addContactButton.setBounds       (120, 305, 60,  25);
    conversationAreaScroll.setBounds (190, 45,  350, 250);
    sendMessageInput.setBounds       (190, 305, 280, 25);
    sendMessageButton.setBounds      (480, 305, 60,  25);
  }
}

class Handler implements Runnable {

  private State state;
  private Frame frame;

  public Handler(State state, Frame frame) {
    this.state = state;
    this.frame = frame;
  }

  @Override
  public void run() {
    System.out.println("I: handler thread started");

    while (true) {
      byte[] buffer = new byte[512];
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
      
      try {
        state.socket.receive(packet);
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
        case "ack":
          String id = request.get("target");
          if (state.timers.containsKey(id)) {
            state.timers.get(id).cancel();
            state.timers.remove(id);
          }

          if (state.sends.containsKey(id)) {
            // TODO: update relevant record with first tick [You] (**) Hello
            state.sends.remove(id);
          }
          break;
        
        case "error":
          JOptionPane.showMessageDialog(null, request.get("text"), "Error", JOptionPane.INFORMATION_MESSAGE);
          break;
        
        case "credentials":
          state.token = request.get("token");
          Map<String, String> response = new HashMap<>();
          response.put("id", Hashing.generateRandomHash());
          response.put("type", "ack");
          response.put("target", request.get("id"));
          state.send(response);
          JOptionPane.showMessageDialog(null, "Registration successful.", "Info", JOptionPane.INFORMATION_MESSAGE);
          break;
        
        case "forward":
          if (state.conversations.get(request.get("sender")) == null) {
            state.conversations.put(request.get("sender"), "");  // TODO: List
            frame.refreshContactsList();
          }

          state.conversations.put(request.get("sender"), state.conversations.get(request.get("sender")) + "\n" + request.get("text"));
          
          Map<String, String> ack = new HashMap<>();
          ack.put("id", Hashing.generateRandomHash());
          ack.put("type", "ack");
          ack.put("target", request.get("id"));
          state.send(ack);

        case "received":
          System.out.println("received");

          // TODO: loop through messages and update received

          Map<String, String> ack = new HashMap<>();
          ack.put("id", Hashing.generateRandomHash());
          ack.put("type", "ack");
          ack.put("target", request.get("id"));
          state.send(ack);

        default:
          System.out.println("E: unknown message received");
      }
    }
  }
}


