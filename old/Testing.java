import java.io.IOException;

import java.nio.charset.StandardCharsets;

import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.UnknownHostException;
import java.net.SocketException;

import java.security.MessageDigest;

import javax.swing.*;
import java.awt.*;





// GUI CLIENT

public class Client {
  public static void main ( String[] args ) {
    JPanel middlePanel = new JPanel ();

    // middlePanel.setBorder ( new TitledBorder ( new EtchedBorder (), "Display Area" ) );

    // contacts
    JPanel contactsPanel = new JPanel();
    // contactsPanel.setMaximumSize(new Dimension(60, 32767));
    contactsPanel.setMinimumSize(new Dimension(60, 100));
    contactsPanel.setPreferredSize(new Dimension(60, 300));

    // contacts
    JList contactsList = new JList(new DefaultListModel());
    contactsList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    contactsList.setLayoutOrientation(JList.VERTICAL);
    contactsList.setVisibleRowCount(-1);

    JScrollPane contactsScroll = new JScrollPane(contactsList);
    // contactsScroll.setPreferredSize(new Dimension(250, 80));
    contactsScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

    contactsPanel.add(contactsScroll);

    // conversation
    JPanel conversationPanel = new JPanel();
    // conversationPanel.setMinimumSize(new Dimension(60, 100));
    // conversationPanel.setPreferredSize(new Dimension(60, 300));
    conversationPanel.setMaximumSize(new Dimension(60, 32767));

    // conversation area
    JTextArea conversationArea = new JTextArea(16, 58);
    conversationArea.setEditable(false); // set textArea non-editable
    
    JScrollPane conversationAreaScroll = new JScrollPane(conversationArea);
    conversationAreaScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    conversationPanel.add(conversationAreaScroll);


    // window
    JFrame frame = new JFrame();
    frame.add(contactsPanel, java.awt.BorderLayout.WEST);
    frame.add(conversationPanel, java.awt.BorderLayout.EAST);
    // frame.setPreferredSize(new Dimension(400, 300));
    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
    frame.setTitle("Chat Application");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
  }
}


// public class Client {

//   public static void main(String[] args) {
//     DatagramSocket socket;
    
//     try {
//       socket = new DatagramSocket();  // bind to any port
//     } catch(SocketException e) {
//       System.out.println("failed to create socket");
//       return;
//     }

//     byte[] localhost = new byte[] { 127, 0, 0, 1 };
//     int port = 8888;

//     InetAddress address;

//     try {
//       address = InetAddress.getByAddress(localhost);
//     } catch (UnknownHostException e) {
//       System.out.println("failed to resolve remote host");
//       return;
//     }

//     String message = "Hello!";
//     byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
  
//     DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);

//     try {
//       socket.send(packet);
//     } catch(IOException e) {
//       System.out.println("failed to send message");
//     }
//   }
// }
