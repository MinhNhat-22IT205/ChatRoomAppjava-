import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import MultiCastServer.ClientHandler;

public class MultiCastClient {
    private String userName;
    private JFrame loginFrame;
    private JFrame roomFrame;
    private JFrame chatFrame;

    private Socket serverSocket;
    private BufferedReader in;
    private PrintWriter out;

    private List<Room> rooms = new ArrayList<>();

    private JTable roomTable;
    private DefaultTableModel roomTableModel;

    private Room currentRoom;
    private MulticastSocket multicastSocket;
    private InetAddress groupAddress;
    private int groupPort;

    private JTextArea chatArea;
    private JTextField messageField;

    private Thread serverListenerThread;
    private volatile boolean initialRoomListLoaded = false;
    private final Object roomListLock = new Object();
    private volatile Room createdRoom = null;
    private final Object createRoomLock = new Object();

    public static final byte[] BUFFER = new byte[4096];

    public static void main(String[] args) {
        new MultiCastClient().showLoginInterface();
    }

    private void showLoginInterface() {
        loginFrame = new JFrame("Đăng nhập");
        JTextField nameField = new JTextField(20);
        JButton loginButton = new JButton("Đăng nhập");
        loginFrame.setLayout(new FlowLayout());
        loginFrame.add(new JLabel("Nhập tên của bạn:"));
        loginFrame.add(nameField);
        loginFrame.add(loginButton);
        loginFrame.setSize(300, 150);
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setLocationRelativeTo(null);
        loginFrame.setVisible(true);

        loginButton.addActionListener(e -> {
            userName = nameField.getText().trim();
            if (!userName.isEmpty()) {
                loginFrame.dispose();
                connectToServer();
                startServerListener();
                showRoomInterface();
            } else {
                JOptionPane.showMessageDialog(loginFrame, "Vui lòng nhập tên của bạn.");
            }
        });
    }

    private void connectToServer() {
        String serverHost = "192.168.1.8";
        int serverPort = 12345;

        try {
            serverSocket = new Socket(serverHost, serverPort);
            in = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
            out = new PrintWriter(serverSocket.getOutputStream(), true);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Không thể kết nối đến server.");
            System.exit(1);
        }
    }

    private void startServerListener() {
        serverListenerThread = new Thread(() -> {
            try {
                String response;
                while ((response = in.readLine()) != null) {
                    if (response.startsWith("NewRoom")) {
                        // Process new room information
                        String[] tokens = response.split(" ", 6);
                        int id = Integer.parseInt(tokens[1]);
                        String name = tokens[2];
                        String creator = tokens[3];
                        String multicastAddress = tokens[4];
                        int port = Integer.parseInt(tokens[5]);
                        Room room = new Room(id, name, creator, InetAddress.getByName(multicastAddress), port);
                        rooms.add(room);

                        // Update the room table in the Event Dispatch Thread
                        SwingUtilities.invokeLater(() -> {
                            roomTableModel.addRow(new Object[]{id, name, creator});
                        });
                    } else if (response.startsWith("RoomCreated")) {
                        String[] tokens = response.split(" ", 6);
                        int id = Integer.parseInt(tokens[1]);
                        String name = tokens[2];
                        String creator = tokens[3];
                        String multicastAddress = tokens[4];
                        int port = Integer.parseInt(tokens[5]);
                        Room room = new Room(id, name);
                        rooms.add(room);
                        SwingUtilities.invokeLater(() -> {
                            roomTableModel.addRow(new Object[]{id, name, creator});
                        });
                        if (creator.equals(userName)) {
                            synchronized (createRoomLock) {
                                createdRoom = room;
                                createRoomLock.notifyAll();
                            }
                        }
                    } else if (response.startsWith("Room")) {
                        // This is part of the initial room list
                        String[] tokens = response.split(" ", 3);
                        int id = Integer.parseInt(tokens[1]);
                        String name = tokens[2];
                        Room room = new Room(id, name);
                        rooms.add(room);
                        SwingUtilities.invokeLater(() -> {
                            roomTableModel.addRow(new Object[]{id, name});
                        });
                    } else if (response.equals("EndOfRoomList")) {
                        // End of initial room list
                        synchronized (roomListLock) {
                            initialRoomListLoaded = true;
                            roomListLock.notifyAll();
                        }
                    } else {
                        // Handle other messages if needed
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverListenerThread.start();
    }

    private void showRoomInterface() {
        roomFrame = new JFrame("Danh sách phòng - " + userName); // Updated title
        roomFrame.setLayout(new BorderLayout());

        roomTableModel = new DefaultTableModel(new Object[]{"ID Phòng", "Tên phòng"}, 0);
        roomTable = new JTable(roomTableModel);
        JScrollPane scrollPane = new JScrollPane(roomTable);

        JButton createRoomButton = new JButton("Tạo phòng");
        JButton joinRoomButton = new JButton("Vào phòng");

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(createRoomButton);
        buttonPanel.add(joinRoomButton);

        roomFrame.add(scrollPane, BorderLayout.CENTER);
        roomFrame.add(buttonPanel, BorderLayout.SOUTH);

        roomFrame.setSize(500, 300);
        roomFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        roomFrame.setLocationRelativeTo(null);
        roomFrame.setVisible(true);

        loadRoomList();

        createRoomButton.addActionListener(e -> {
            String roomName = JOptionPane.showInputDialog(roomFrame, "Nhập tên phòng:");
            if (roomName != null && !roomName.trim().isEmpty()) {
                createRoom(roomName.trim());
            }
        });

        joinRoomButton.addActionListener(e -> {
            int selectedRow = roomTable.getSelectedRow();
            if (selectedRow >= 0) {
                Room room = rooms.get(selectedRow);
                joinRoom(room);
                roomFrame.dispose();
                showChatInterface();
            } else {
                JOptionPane.showMessageDialog(roomFrame, "Vui lòng chọn một phòng để vào.");
            }
        });
    }

    private void loadRoomList() {
        roomTableModel.setRowCount(0);
        rooms.clear();
        initialRoomListLoaded = false;

        out.println("GetRooms");

        // Wait until initial room list is loaded
        synchronized (roomListLock) {
            while (!initialRoomListLoaded) {
                try {
                    roomListLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void createRoom(String roomName) {
        out.println("CreateRoom " + roomName);

        // Wait until room is created and received in serverListenerThread
        synchronized (createRoomLock) {
            while (createdRoom == null) {
                try {
                    createRoomLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        // Proceed to join the room
        joinRoom(createdRoom);
        roomFrame.dispose();
        showChatInterface();
        createdRoom = null; // Reset for next time
    }

    private void joinRoom(Room room) {
        currentRoom = room;

        try {
            out.println("JoinRoom "+currentRoom.getId()+" " + currentRoom.getName() + " " + userName);

            // Notify other clients in the room
            sendSystemMessage("Người dùng " + userName + " đã tham gia phòng.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showChatInterface() {
        chatFrame = new JFrame("Phòng chat - " + currentRoom.getName() + " - " + userName); // Updated title
        chatFrame.setLayout(new BorderLayout());

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        messageField = new JTextField();
        JButton sendButton = new JButton("Gửi");
        JButton leaveButton = new JButton("Thoát phòng");

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(inputPanel, BorderLayout.CENTER);
        bottomPanel.add(leaveButton, BorderLayout.SOUTH);

        chatFrame.add(scrollPane, BorderLayout.CENTER);
        chatFrame.add(bottomPanel, BorderLayout.SOUTH);

        chatFrame.setSize(500, 400);
        chatFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        chatFrame.setLocationRelativeTo(null);
        chatFrame.setVisible(true);

        new Thread(new MessageReceiver()).start();

        sendButton.addActionListener(e -> {
            sendMessage();
        });

        messageField.addActionListener(e -> {
            sendMessage();
        });

        leaveButton.addActionListener(e -> {
            leaveRoom();
        });
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            String fullMessage = userName + ": " + message;
            //TODO: ADD LISTENER
        }
    }

    private void sendSystemMessage(String message) {
    	//TODO: ADD SEND LISTENER
        String fullMessage = "[SYSTEM]: " + message;
       
    }

    private void leaveRoom() {
        try {
            // Notify other clients in the room
            sendSystemMessage("Người dùng " + userName + " đã rời phòng.");

            // Notify server that user is leaving the room
            out.println("LeaveRoom " + currentRoom.getName() + " " + userName);

            multicastSocket.leaveGroup(groupAddress);
            multicastSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        chatFrame.dispose();
        showRoomInterface();
    }

    // Receives messages in the chat room
    private class MessageReceiver implements Runnable {
        public void run() {
            try {
                while (!multicastSocket.isClosed()) {
                    DatagramPacket inPacket = new DatagramPacket(BUFFER, BUFFER.length);
                    multicastSocket.receive(inPacket);
                    String message = new String(BUFFER, 0, inPacket.getLength());
                    InetAddress senderAddress = inPacket.getAddress();
                    System.out.println(message);
                    String displayMessage;
                    if (message.startsWith("[SYSTEM]:")) {
                        // System message, do not include IP
                        displayMessage = message;
                    } else {
                        displayMessage = "[" + senderAddress.getHostAddress() + "] " + message;
                    }
                    SwingUtilities.invokeLater(() -> {
                        chatArea.append(displayMessage + "\n");
                    });
                }
            } catch (IOException e) {
                // Socket closed, exit thread
            }
        }
    }

    // Room class
    private static class Room {
        private int id;
        private String name;

        public Room(int id, String name) {
            this.id = id;
            this.name = name;
        }

        // Getter methods
        public int getId() { return id; }
        public String getName() { return name; }

       
    }
}
