package run_time_db;

import packet.Command;
import packet.Packet;
import packet.User;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.*;

public enum UserManagement {
    INSTANCE;

    private List<User> users;
    private Map<String, Set<User>> chatRooms;

    UserManagement() {
        this.users = new ArrayList<>(List.of(
                User.builder().nickname("Mock 1").password("1234").build(),
                User.builder().nickname("Mock 2").password("1234").build(),
                User.builder().nickname("Mock 3").password("1234").build()
        ));
        this.chatRooms = new HashMap<>();
    }

    public List<User> getAuthenticatedUsers() {
        return this.users
                .stream()
                .filter(user -> Objects.nonNull(user.getSocket()))
                .toList();
    }

    public Optional<User> register(User userToRegister) {
        if (userToRegister.getNickname().isEmpty() || userToRegister.getPassword().isEmpty()) {
            throw new IllegalArgumentException("User must have a nickname and a password");
        }

        this.users.add(userToRegister);
        return Optional.of(userToRegister);
    }

    public Optional<User> login(User userToLogin) {
        return this.users
                .stream()
                .filter(user -> user.equals(userToLogin))
                .findFirst();
    }

    public void createRoom(String roomName) {
        this.chatRooms.putIfAbsent(roomName, new HashSet<>());
    }

    public void joinRoom(String roomName, User user) {
        this.chatRooms.computeIfAbsent(roomName, k -> new HashSet<>()).add(user);
    }

    public void broadcastMessage(Packet packet) {
        for (User user : users) {
            boolean isNotSameUser = !packet.getUser().getNickname().equals(user.getNickname());

            if (Objects.nonNull(user.getSocket()) && isNotSameUser) {
                sendMessageToUser(packet, user, Command.MESSAGE_ALL);
            }
        }
    }

    public void roomMessage(Packet packet) {
        Set<User> roomUsers = chatRooms.get(packet.getRoomName());
        if (roomUsers != null) {
            for (User user : roomUsers) {
                boolean isNotSameUser = !packet.getUser().getNickname().equals(user.getNickname());

                if (Objects.nonNull(user.getSocket()) && isNotSameUser) {
                    sendMessageToUser(packet, user, Command.MESSAGE_ROOM);
                }
            }
        }
    }

    public void individualMessage(Packet receivedPacket) {
        for (User user : users) {
            boolean isRecipientUser = receivedPacket.getUserRecipient().getNickname().equals(user.getNickname());

            if (Objects.nonNull(user.getSocket()) && isRecipientUser) {
                sendMessageToUser(receivedPacket, user, Command.MESSAGE_INDIVIDUAL);
            }
        }
    }

    private void sendMessageToUser(Packet packet, User user, Command command) {
        User cleanUser = User.builder()
                .nickname(packet.getUser().getNickname())
                .build();

        Packet messagePacket = Packet.builder()
                .userRecipient(user)
                .user(cleanUser)
                .message(packet.getMessage())
                .command(command)
                .build();

        try {
            ObjectOutputStream userOutStream = user.getOutStream();
            if (userOutStream != null) {
                userOutStream.writeObject(messagePacket);
                userOutStream.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException("Error sending message to user: " + user.getNickname(), e);
        }
    }
}
