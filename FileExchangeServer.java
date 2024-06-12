import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.text.SimpleDateFormat;
import java.util.Date;
public class FileExchangeServer {
    private static final int PORT = 12345;
    private final ExecutorService clientPool = Executors.newCachedThreadPool();
    private final Map<String, Socket> registeredClients = new ConcurrentHashMap<>();
    private final String storageDir = "server_storage";
    private final Map<String, PrintWriter> clientWriters = new ConcurrentHashMap<>();
    private DataOutputStream dataOutputStream;
    
    public static void main(String[] args) {
        new FileExchangeServer().startServer();
    }
    public void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("File Exchange Server is running on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                clientPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.out.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private PrintWriter out;
        private String clientHandle = null; 
        public String anonymousIdentifier;
        public String handle;
        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
            new File(storageDir).mkdir();
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                anonymousIdentifier = "Anonymous@" + socket.getRemoteSocketAddress();
                System.out.println("A user has joined");
                dataOutputStream = new DataOutputStream(socket.getOutputStream());
                clientWriters.put(anonymousIdentifier, out);
                broadcastMessage("A user has joined", handle);        
            } catch (IOException e) {
                System.out.println("Error obtaining output stream: " + e.getMessage());
            }
        }

        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            	
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    String[] tokens = inputLine.split(" ");
                    String command = tokens[0];

                    switch (command.toLowerCase()) {
                        case "/join":
                            if (tokens.length > 1) {
                                String handle = tokens[1];
                            }
                            out.println("Connection to the File Exchange Server is successful!");
                            break;
                        case "/leave":
                            out.println("Connection closed. Thank you!");
                            clientSocket.close();
                            return;
                        case "/register":
                            if (tokens.length > 1) {
                                handle = tokens[1].trim();
                                if ("null".equalsIgnoreCase(handle) || handle.isEmpty()) {
                                    out.println("Error: Invalid handle.");
                                } else if (registeredClients.containsKey(handle)) {
                                    out.println("Error: Handle already in use.");
                                } else {
                                    clientHandle = handle;
                                    registeredClients.put(handle, clientSocket);
                                    clientWriters.remove(anonymousIdentifier);
                                    clientWriters.put(handle, out);
                                    broadcastMessage(handle + " successfully registered!", handle);
                                }
                            } else {
                                out.println("Error: No handle provided.");
                            }
                            break;

                        case "/dir":
                            File storageDirectory = new File(storageDir);
                            File[] files = storageDirectory.listFiles();
                            
                            if (files != null) {
                            	out.println("\n--- Server Directory Listing ---");
                                for (File file : files) {
                                    out.println(file.getName());
                                } 
                                out.println("--- End of Directory Listing ---\n");
                            } else {
                                out.println("No files in the directory.");
                            }
                            broadcastMessage("Requested for server directory", handle);  
                            break;
                        case "/store":
                            if (clientSocket != null && !clientSocket.isClosed()) {
                                DataInputStream dataInputStream = new DataInputStream(clientSocket.getInputStream());
                                receiveFileFromClient(dataInputStream);
                                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                String timestamp = dateFormat.format(new Date());
                                tokens = inputLine.split(" ");
                                String filename = tokens.length > 1 ? tokens[1] : "Unknown File"; 
                                String message =null;
                                if (handle == null || handle.isEmpty() || "null".equalsIgnoreCase(handle)) {
                                	 message = "Anonymous" + "<" + timestamp + ">: Uploaded " + filename;
                                } else  message = handle + "<" + timestamp + ">: Uploaded " + filename;
                                out.println("File stored successfully.");
                                broadcastMessage(message, handle);
                            } else {
                                out.println("Error: Client socket is closed or null.");
                            }
                            break;

                        case "/get":
                            if (clientSocket != null && !clientSocket.isClosed()) {
                                if (tokens.length > 1) {
                                    String filename = tokens[1];
                                    File fileToCheck = new File(storageDir + File.separator + filename);
                                    boolean isSent = true;
                                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                    String timestamp = dateFormat.format(new Date());
                                    String message;
                                    if (!fileToCheck.exists()) {
                                        isSent = false;
                                    }
                                    if (isSent) {
                                        if (handle == null || handle.isEmpty() || "null".equalsIgnoreCase(handle)) {
                                            message = "Anonymous" + "<" + timestamp + ">: Downloaded " + filename;
                                        } else {
                                            message = handle + "<" + timestamp + ">: Downloaded " + filename;
                                        }
                                    } else {
                                        message = "Error: File not found in the server.";
                                    }
                                    broadcastMessage(message, handle);
                                } else {
                                    out.println("Error: Invalid command parameters.");
                                }
                            } else {
                                out.println("Error: Client socket is closed or null.");
                            }
                            break;

                        case "/?":
                        	out.println("\n--- Available Commands ---");
                        	out.println("/join [server] [port]    - Connect to a server at the specified address and port.");
                        	out.println("/leave                  - Disconnect from the server.");
                        	out.println("/register [username]    - Register your username on the server.");
                        	out.println("/store [filename]       - Store a file on the server.");
                        	out.println("/dir                    - List files stored on the server.");
                        	out.println("/get [filename]         - Retrieve a file from the server.");
                        	out.println("/?                      - Display available commands.");
                        	out.println("--- End of Commands ---\n");
                            break;
                        default:
                            out.println("Error: Command not found.");
                            break;
                    }
                }
            } catch (IOException e) {
            	sendFileToClient(dataOutputStream, anonymousIdentifier, anonymousIdentifier);
                System.out.println("ClientHandler exception: " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (clientHandle != null) {
                    registeredClients.remove(clientHandle);
                    clientWriters.remove(clientHandle);
                    broadcastMessage("User " + clientHandle + " has left.", handle);
                    System.out.print("A user has left");
                } else {
                    broadcastMessage("An anonymous user has left.", handle);
                    System.out.print("A user has left\n");
                    clientWriters.remove(clientHandle != null ? clientHandle : "Anonymous@" + clientSocket.getRemoteSocketAddress());
                }
            }
        }
        private void receiveFileFromClient(DataInputStream inputStream) throws IOException {
            String fileName = inputStream.readUTF();
            long fileSize = inputStream.readLong();

            try (FileOutputStream fileOutputStream = new FileOutputStream(new File(storageDir, fileName))) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                long totalBytesRead = 0;

                while (totalBytesRead < fileSize && (bytesRead = inputStream.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }
            }
        }
        
        private boolean sendFileToClient(DataOutputStream outputStream, String filename, String senderHandle) {
            if (outputStream == null) {
                System.out.println("Error: Output stream is null.");
                return false;
            }
            File fileToCheck = new File(filename);

            if (!fileToCheck.exists()) {
                System.out.println("File does not exist: " + filename);
                return false;
            }
            try (FileInputStream fileInputStream = new FileInputStream(fileToCheck)) {
                byte[] buffer = new byte[1024];
                int bytesRead;

                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            } catch (IOException e) {
                System.err.println("Error sending file: " + e.getMessage());
                return false;
            }

            return true;
        }

        private void broadcastMessage(String message, String senderHandle) {
            for (Map.Entry<String, PrintWriter> entry : clientWriters.entrySet()) {
                String clientHandle = entry.getKey();
                    if (senderHandle == null || senderHandle.isEmpty() || "null".equalsIgnoreCase(senderHandle)) {
                    	entry.getValue().println("-----\nBroadcast - Anonymous user: " + message + "\n-----\nInput Command / Response -");
                    } else {
                    	entry.getValue().println("-----\nBroadcast - " + senderHandle + ": " + message + "\n-----\nInput Command / Response -");
                    }
            }
        }
    }
    
}
