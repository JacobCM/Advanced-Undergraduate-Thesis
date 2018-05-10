
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.*;

/**
 * A simple Java socket program for sending and receiving messages
 */
public class SocketCommunicator {

    private BufferedReader in;
    private PrintWriter out;
    private Socket socket = null;

    public SocketCommunicator() {

    }

    public void connectToServer(String IP, int port) throws IOException {

        // Make connection and initialize streams
            socket = new Socket(IP, port);
            in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            out = new PrintWriter(socket.getOutputStream(), true);
    }

    // Send command for sending a message through the socket and returning the reply
    public String send(String message) {
        String reply = null;
        if (message.contains("setLRPower") == false){
            try {
            out.println(message);
            reply = in.readLine();
            }
            catch(java.io.IOException e){ e.printStackTrace();}
        }
        else {
            out.println(message);
        }
        return reply;
    }

    // Closes the connection on the socket
    public void close(){
        try {
            socket.close();
        }
        catch(java.io.IOException e){ e.printStackTrace();}

    }
}
