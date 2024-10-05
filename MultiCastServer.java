import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class MultiCastServer {
    private static List<Room> rooms = new ArrayList<>();
    private static int roomIdCounter = 1;
    private static int multicastAddressCounter = 1;
    private static final String BASE_MULTICAST_ADDRESS = "230.0.0.";

    // List to keep track of all connected clients
    private static List<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        int serverPort = 12345;

        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            System.out.println("MultiCast Server is running on port " + serverPort);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clientHandlers.add(clientHandler); // Add client to the list
                clientHandler.start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Handles client connections
    private static class ClientHandler extends Thread {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        public ClientHandler(Socket clientSocket) {
            this.socket = clientSocket;
        }

        public void run() {
            try {
                in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String request;
                while ((request = in.readLine()) != null) {
                    if (request.startsWith("GetRooms")) {
                        sendRoomList();
                    } else if (request.startsWith("CreateRoom")) {
                        String[] tokens = request.split(" ", 3);
                        if (tokens.length == 3) {
                            String roomName = tokens[1];
                            String creatorName = tokens[2];
                            createRoom(roomName, creatorName);
                        }
                    } else if (request.startsWith("JoinRoom")) {
                        String[] tokens = request.split(" ", 3);
                        if (tokens.length == 3) {
                            String roomName = tokens[1];
                            String userName = tokens[2];
                            System.out.println(getCurrentTimeStamp() + " - User '" + userName + "' joined room '" + roomName + "'");
                        }
                    } else if (request.startsWith("LeaveRoom")) {
                        String[] tokens = request.split(" ", 3);
                        if (tokens.length == 3) {
                            String roomName = tokens[1];
                            String userName = tokens[2];
                            System.out.println(getCurrentTimeStamp() + " - User '" + userName + "' left room '" + roomName + "'");
                        }
                    } else {
                        out.println("UnknownCommand");
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                clientHandlers.remove(this); // Remove client when disconnected
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }

        private void sendRoomList() {
        	System.out.println(getCurrentTimeStamp() + "User request to send room list ");
            for (Room room : rooms) {
                out.println("Room " + room.getId() + " " + room.getName() + " " + room.getCreator()
                        + " " + room.getMulticastAddress().getHostAddress() + " " + room.getPort());
            }
            out.println("EndOfRoomList");
            System.out.println(getCurrentTimeStamp() + " Room list sent");
        }

        private void createRoom(String roomName, String creatorName) {
            try {
                String multicastAddress = BASE_MULTICAST_ADDRESS + multicastAddressCounter++;
                InetAddress group = InetAddress.getByName(multicastAddress);
                int port = 5000 + roomIdCounter;
                Room room = new Room(roomIdCounter++, roomName, creatorName, group, port);
                rooms.add(room);
                out.println("RoomCreated " + room.getId() + " " + room.getName() + " "
                        + room.getCreator() + " " + room.getMulticastAddress().getHostAddress()
                        + " " + room.getPort());

                // Log the room creation with timestamp
                System.out.println(getCurrentTimeStamp() + " - User '" + creatorName + "' created room '" + roomName + "'");

                // Notify all clients about the new room
                broadcastNewRoom(room);

            } catch (UnknownHostException e) {
                e.printStackTrace();
                out.println("Error Creating Room");
            }
        }

        private void broadcastNewRoom(Room room) {
            String message = "NewRoom " + room.getId() + " " + room.getName() + " "
                    + room.getCreator() + " " + room.getMulticastAddress().getHostAddress()
                    + " " + room.getPort();
            for (ClientHandler clientHandler : clientHandlers) {
                if (clientHandler != this) { // Avoid sending to the creator again
                    clientHandler.out.println(message);
                }
            }
        }
    }

    // Helper method to get current timestamp
    private static String getCurrentTimeStamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    // Room class
    private static class Room {
        private int id;
        private String name;
        private String creator;
        private InetAddress multicastAddress;
        private int port;

        public Room(int id, String name, String creator, InetAddress multicastAddress, int port) {
            this.id = id;
            this.name = name;
            this.creator = creator;
            this.multicastAddress = multicastAddress;
            this.port = port;
        }

        // Getter methods
        public int getId() { return id; }
        public String getName() { return name; }
        public String getCreator() { return creator; }
        public InetAddress getMulticastAddress() { return multicastAddress; }
        public int getPort() { return port; }
    }
}
