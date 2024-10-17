import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class MultiCastServer {
    private static List<Room> rooms = new ArrayList<>();
    private static int roomIdCounter=0;

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
                        String[] tokens = request.split(" ", 2);
                        if (tokens.length == 2) {
                            String roomName = tokens[1];
                            createRoom(roomName);
                        }
                    } else if (request.startsWith("JoinRoom")) {
                        String[] tokens = request.split(" ", 4);
                        if (tokens.length == 4) {
                        	String roomId=tokens[1];
                            String roomName = tokens[2];
                            String userName = tokens[3];
                            int intergerRoomId = Integer.valueOf(roomId);
                            Room room = getRoomById(intergerRoomId);
                            room.add
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
                out.println("Room " + room.getId() + " " + room.getName() + " " + room.getCreator());
            }
            out.println("EndOfRoomList");
            System.out.println(getCurrentTimeStamp() + " Room list sent");
        }

        private void createRoom(String roomName) {
            try {
                
                Room room = new Room(roomIdCounter++, roomName,this);
                rooms.add(room);
                out.println("RoomCreated " + room.getId() + " " + room.getName());

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
            String message = "NewRoom " + room.getId() + " " + room.getName();
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
    
    private static Room getRoomById (int id) {
    	Room resultRoom=null;
    	for (Room room : rooms) {
            if(room.getId()==id) {
            	resultRoom=room;
            }
        }
    	return resultRoom;
    }

    // Room class
    private static class Room {
        private int id;
        private String name;
        private ClientHandler creator;
        private ArrayList<ClientHandler> members;

        public Room(int id, String name, ClientHandler creator) {
            this.id = id;
            this.name = name;
            this.creator = creator;
            this.members = new ArrayList<>();
            this.members.add(creator);
        }

        // Getter methods
        public int getId() { return id; }
        public String getName() { return name; }
        public ClientHandler getCreator() { return creator; }

        public void addMember(ClientHandler member) {
            if (!members.contains(member)) {
                members.add(member);
            }
        }

        public void removeMember(ClientHandler member) {
            members.remove(member);
        }

        public void sendMessageToMembers(String message) {
            for (ClientHandler member : members) {
                member.out.println(message);
            }
        }

        public void broadcastMessage(String senderName, String message) {
            String fullMessage = senderName + ": " + message;
            sendMessageToMembers(fullMessage);
        }
    }
}
