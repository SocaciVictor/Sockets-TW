package run_time_db;

import packet.Command;
import packet.Packet;
import packet.User;
import run_time_db.UserManagement;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class ClientThread extends Thread {
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private HashMap<String, Set<ClientThread>> chatRooms;

    public ClientThread(Socket clientConnection, HashMap<String, Set<ClientThread>> chatRooms) {
        this.socket = clientConnection;
        this.chatRooms = chatRooms;
        try {
            this.out = new ObjectOutputStream(this.socket.getOutputStream());
            this.in = new ObjectInputStream(this.socket.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Error initializing streams", e);
        }
    }

    @Override
    public void run() {
        try {
            boolean isRunning = true;
            while (isRunning) {
                Packet receivedPacket = (Packet) in.readObject();
                System.out.println("Received: " + receivedPacket);

                execute(receivedPacket);
            }
        } catch (ClassNotFoundException | IOException e) {
            System.out.println("Client disconnected or error: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void execute(Packet receivedPacket) {
        Packet responsePacket = null;

        switch (receivedPacket.getCommand()) {
            case LOGIN -> {
                Optional<User> optionalUser = UserManagement.INSTANCE.login(receivedPacket.getUser());

                if (optionalUser.isPresent()) {
                    User loggedInUser = optionalUser.get();
                    loggedInUser.setSocket(this.socket);
                    loggedInUser.setOutStream(this.out);

                    responsePacket = Packet.builder()
                            .message("Success")
                            .user(loggedInUser)
                            .command(Command.LOGIN)
                            .build();
                } else {
                    responsePacket = Packet.builder().message("User not found").build();
                }
            }
            case REGISTER -> {
                Optional<User> optionalUserRegistry = UserManagement.INSTANCE.register(receivedPacket.getUser());

                if (optionalUserRegistry.isPresent()) {
                    User registrerInUser = optionalUserRegistry.get();
                    registrerInUser.setSocket(this.socket);
                    registrerInUser.setOutStream(this.out);

                    responsePacket = Packet.builder()
                            .message("Success")
                            .user(registrerInUser)
                            .command(Command.REGISTER)
                            .build();
                } else {
                    responsePacket = Packet.builder().message("User not registered").build();
                }
            }
            case MESSAGE_ALL -> {
                UserManagement.INSTANCE.broadcastMessage(receivedPacket);
            }
            case MESSAGE_INDIVIDUAL -> {
                UserManagement.INSTANCE.individualMessage(receivedPacket);
            }
            case JOIN_ROOM -> {
                User user = receivedPacket.getUser();
                String roomName = receivedPacket.getRoomName();
                UserManagement.INSTANCE.joinRoom(roomName, user);
                chatRooms.computeIfAbsent(roomName, k -> new HashSet<>()).add(this);
                responsePacket = Packet.builder()
                        .message("Joined room: " + roomName)
                        .command(Command.JOIN_ROOM)
                        .build();
            }
            case CREATE_ROOM -> {
                String roomName = receivedPacket.getRoomName();
                UserManagement.INSTANCE.createRoom(roomName);
                chatRooms.putIfAbsent(roomName, new HashSet<>());
                responsePacket = Packet.builder()
                        .message("Room created: " + roomName)
                        .command(Command.CREATE_ROOM)
                        .build();
            }
            case MESSAGE_ROOM -> {
                String roomName = receivedPacket.getRoomName();
                Set<ClientThread> roomClients = chatRooms.get(roomName);
                if (roomClients != null) {
                    for (ClientThread client : roomClients) {
                        if (!client.equals(this)) {
                            client.sendPacket(receivedPacket);
                        }
                    }
                }
            }
            default -> {
                responsePacket = Packet.builder().message("Invalid command").build();
            }
        }

        if (responsePacket != null) {
            sendPacket(responsePacket);
        }
    }

    private void sendPacket(Packet packet) {
        try {
            out.writeObject(packet);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException("Error sending packet", e);
        }
    }

    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Error closing connection.");
        }
    }
}
