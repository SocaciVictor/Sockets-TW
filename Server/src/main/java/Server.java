import run_time_db.ClientThread;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class Server {
    /* Arbitrary port number */
    public static final int PORT = 6543;

    // HashMap to manage chat rooms
    private final HashMap<String, Set<ClientThread>> chatRooms = new HashMap<>();

    public void start() {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            Socket clientConnection = null;

            boolean isRunning = true;

            System.out.println("Server is running");
            /* An infinite loop is necessary in order keep the server awake */
            while (isRunning) {
                /* Accepts connections */
                clientConnection = serverSocket.accept();
                /* Make a new thread for each client */
                new Thread(new ClientThread(clientConnection, chatRooms)).start();
            }

        } catch (IOException e) {
            /* Failed to create the Terminal */
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        new Server().start();
    }
}
