	import java.io.*;
	import java.net.*;
	import java.util.Scanner;
	import java.util.concurrent.atomic.AtomicBoolean;
	import java.util.LinkedList;
	import java.util.Queue;
	public class FileExchangeClient {
	    private Socket socket;
	    private PrintWriter out;
	    private BufferedReader in;
	    private final Scanner scanner = new Scanner(System.in);
	    private final AtomicBoolean awaitingResponse = new AtomicBoolean(false);
	    private boolean justConnected = true; 
	    private boolean expectingResponse = false;
	    private final Object responseLock = new Object();
	    private DataOutputStream dataOutputStream;
	    private DataInputStream dataInputStream;
	    public String username = null;
	    public String oldusername = null;
	    private boolean isRegistered = false; 
		public boolean successful = false;
	    Queue<String> filesBeingPrinted = new LinkedList<>();
	    public static void main(String[] args) {
	        FileExchangeClient client = new FileExchangeClient();
	        client.startClient();
	    }
	
		
	    public void startClient() {
	        while (true) {
				if (successful == false)
					System.out.print("Input Command / Response - \n");
	            String input = scanner.nextLine();
	            awaitingResponse.set(true);
	            handleCommand(input);
	            
	            synchronized (responseLock) {
	                while (awaitingResponse.get() || expectingResponse) {
	                    try {
	                        responseLock.wait();
	                    } catch (InterruptedException e) {
	                        // Handle interruption appropriately
	                    }  
	                }
	            }
	        }
	    }

	
	    private void handleCommand(String input) {
			String[] tokens = input.split(" ");
			String command = tokens[0];
		
			try {
				switch (command.toLowerCase()) {
					case "/join":
						if (tokens.length > 2) {
							if (!isServerConnected()) {
								successful = true;
								connectToServer(tokens[1], Integer.parseInt(tokens[2]));
								awaitingResponse.set(false);
								
							} else {
								System.out.println("Already connected to the server.");
								successful = false;
							}
						} else {
							System.out.println("Error: Command parameters do not match or are not allowed.");
							successful = false;
						}
						break;
		
					case "/leave":
						if (isServerConnected()) {
							sendCommandToServer(input);
							awaitingResponse.set(true);
							synchronized (responseLock) {
								while (awaitingResponse.get() || expectingResponse) {
									responseLock.wait();
								}
							}
							isRegistered = false;
							oldusername = null;
							username = null;
							successful = true;
							disconnectFromServer();
						} else {
							System.out.println("Error: Disconnection failed. Please connect to the server first.");
							successful = false;
						}
						successful = false;
						break;
		
					case "/store":
						if (!isServerConnected()) {
							System.out.println("Error: Not connected to the server.");
							successful = false;
						} else if (!isRegistered) {
							System.out.println("Error: User not registered.");
							successful = false;
						} else {
							if (tokens.length > 1) {
								String filename = tokens[1];
								File fileToSend = new File(filename);
		
								if (!fileToSend.exists()) {
									System.out.println("Error: File not found - " + filename);
									successful = false;
								} else {
									sendCommandToServer(input);
									sendFileToServer(dataOutputStream, filename);
									awaitingResponse.set(true);
									synchronized (responseLock) {
										while (awaitingResponse.get() || expectingResponse) {
											try {
												responseLock.wait();
											} catch (InterruptedException e) {
												Thread.currentThread().interrupt(); 
												System.out.println("Thread was interrupted.");
											}
										}
									}
									successful = true;
								}
							} else {
								System.out.println("Error: Command parameters do not match or are not allowed.");
								successful = false;
							}
						}
						awaitingResponse.set(false);
						expectingResponse = false;
						synchronized (responseLock) {
							responseLock.notifyAll();
						}
						break;
		
					case "/register":
	                	 if (isServerConnected()) {
	                		 if (tokens.length > 1) {
	 	                        username = tokens[1];       
	 	                        if (!isRegistered) {
	 	                        	System.out.println("Welcome " + username + "!");
	 	                        	registerUser(username);
	 	 	                        oldusername = username;
	 	                            isRegistered = true;
	 	                            oldusername = username;
	 	                           successful = true; 
	 	                          
	 	                        } else if (username.equals(oldusername)) {
	 	                        	System.out.println("Error: Registration failed. Handle or alias already exists.");
	 	                        	successful = false;
	 	                        } else {
	 	                        	System.out.println("Error: You are already registered with the handle " + oldusername);
	 	                        	successful = false;
	 	                        }
	 	                    } else {
	 	                        System.out.println("Error: Username not provided.");
	 	                       successful = false;
	 	                    } }else {
	 	                    	 System.out.println("Error: Not connected to the server.");
	 	                    	successful = false;
		                    } 
	                	 break;
		
					case "/dir":
						if (!isServerConnected()) {
							System.out.println("Error: Not connected to the server.");
							successful = false;
						} else if (!isRegistered) {
							System.out.println("Error: User not registered.");
							successful = false;
						} else {
							sendCommandToServer(input);
							awaitingResponse.set(true);
							synchronized (responseLock) {
								while (awaitingResponse.get() && !filesBeingPrinted.isEmpty()) {
									try {
										responseLock.wait();
									} catch (InterruptedException e) {
										Thread.currentThread().interrupt();
										System.out.println("Thread was interrupted.");
										successful = false; 
									}
								}
							}
							successful = true;
						}
						awaitingResponse.set(false);
						expectingResponse = false;
						synchronized (responseLock) {
							responseLock.notifyAll();
						}
						break;
		
					case "/get":
						if (!isServerConnected()) {
							System.out.println("Error: Not connected to the server.");
							successful = false;
							resetCommunicationState();
						} else if (!isRegistered) {
							System.out.println("Error: User not registered.");
							successful = false;
							resetCommunicationState();
						} else {
							if (tokens.length > 1) {
								String filename = tokens[1];
								receiveFileFromServer(dataInputStream, "received_files", filename);
								sendCommandToServer(input);	
								successful = true;
							} else {
								System.out.println("Error: Invalid command parameters.");
								successful = false;
							}
							resetCommunicationState();
						}
						break;
		
					case "/?":
						System.out.println("\n--- Available Commands ---");
						System.out.println("/join [server] [port]    - Connect to a server at the specified address and port.");
						System.out.println("/leave                  - Disconnect from the server.");
						System.out.println("/register [username]    - Register your username on the server.");
						System.out.println("/store [filename]       - Store a file on the server.");
						System.out.println("/dir                    - List files stored on the server.");
						System.out.println("/get [filename]         - Retrieve a file from the server.");
						System.out.println("/?                      - Display available commands.");
						System.out.println("--- End of Commands ---\n");

						successful = false;
						break;
		
					default:
						System.out.println("Error: Command not found.");
						successful = false;
						break;
				}
			} catch (Exception e) {
				successful = false;
			} finally {
				awaitingResponse.set(false);
				expectingResponse = false;
				synchronized (responseLock) {
					responseLock.notifyAll();
				}
	        }
	    }
	    private void registerUser(String username) {
	        if (isServerConnected()) {
	            sendCommandToServer("/register " + username);
	            awaitingResponse.set(true);
	            synchronized (responseLock) {
	                while (awaitingResponse.get() || expectingResponse) {
	                    try {
							responseLock.wait();
						} catch (InterruptedException e) {
						}
	                }
	            }
	        } else {
	            System.out.println("Error: Not connected to the server.");
	        }
	    }

	    private void connectToServer(String serverAddress, int port) {
	        if ("127.0.0.1".equals(serverAddress)) {
	            try {
	                socket = new Socket(serverAddress, port);
	                out = new PrintWriter(socket.getOutputStream(), true);
	                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
	                dataOutputStream = new DataOutputStream(socket.getOutputStream()); 

	                new Thread(() -> {
	                    try {
	                        String fromServer;
	                        while ((fromServer = in.readLine()) != null) {
	                            System.out.println(fromServer);
	                            synchronized (responseLock) {
	                                awaitingResponse.set(false);
	                                expectingResponse = false;
	                                responseLock.notifyAll();
	                            }
	                        }
	                    } catch (IOException e) {

	                    }
	                }).start();

	                justConnected = true;
	                System.out.println("Connection to the File Exchange Server is successful!");
					successful = true;
	            } catch (ConnectException e) {
	                System.out.println("Error: Wrong port or server not running.");
					successful = false;
	            } catch (IOException e) {
	                System.out.println("Error: " + e.getMessage());
					successful = false;
	            } finally {
	                synchronized (responseLock) {
	                    responseLock.notifyAll();
	                }
	            }
	        } else {
	            System.out.println("Error: Wrong port or server not running.");
	        }
	        synchronized (responseLock) {
	            responseLock.notifyAll();
	        }
	    }

	
	    	    private void disconnectFromServer() throws IOException {
	        if (socket != null && !socket.isClosed()) {
	            socket.close();
	        } else {
	            System.out.println("Error: Not currently connected to a server.");
	            
	        }
	    }
	    private void sendFileToServer(DataOutputStream outputStream, String filename) {
	        File fileToSend = new File(filename);

	        if (!fileToSend.exists()) {
	            System.out.println("Error: File not found - " + filename);
	            awaitingResponse.set(false);
	            expectingResponse = false;
	            synchronized (responseLock) {
	                responseLock.notifyAll();
	            }
	            return;
	        }

	        try {
	            outputStream.writeUTF(fileToSend.getName());
	            outputStream.writeLong(fileToSend.length());
	            outputStream.flush();

	            try (FileInputStream fileInputStream = new FileInputStream(fileToSend)) {
	                byte[] buffer = new byte[1024];
	                int bytesRead;

	                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
	                    outputStream.write(buffer, 0, bytesRead);
	                }
	            }
				successful = true;
	            System.out.println("File sent successfully: " + filename);
	        } catch (IOException e) {
	            System.out.println("Error sending file: " + e.getMessage());
				successful = false;
	        } finally {
	            awaitingResponse.set(false);
	            expectingResponse = false;
	            synchronized (responseLock) {
	                responseLock.notifyAll();
	            }
	        }
	    }


	    private void resetCommunicationState() {
	        awaitingResponse.set(false);
	        expectingResponse = false;
	        synchronized (responseLock) {
	            responseLock.notifyAll();
	        }
	    }

	    private void receiveFileFromServer(DataInputStream inputStream, String destinationPath, String filename) throws IOException {
	        File destinationDir = new File(destinationPath);
	        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
	            System.out.println("Error: Unable to create directory " + destinationPath);
	            return;
	        }
	        File sourceFile = new File("server_storage", filename);
	        if (!sourceFile.exists()) {
                return;
            }
	        File receivedFile = new File(destinationDir, filename);
	        if (receivedFile.exists() && receivedFile.isFile()) {
	            if (!receivedFile.delete()) {
	                return;
	            }
	        }
			System.out.println("File received from Server: " + filename);
	        try {
	            FileInputStream fileInputStream = new FileInputStream(sourceFile);
	            int data;
	            try (FileWriter fileWriter = new FileWriter(receivedFile, true)) {
	                while ((data = fileInputStream.read()) != -1) {
	                    fileWriter.write((char) data);
	                }
	            } catch (IOException e) {
	                e.printStackTrace();
	            }
	            fileInputStream.close();
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
			successful = true;
	        
	    }


	    void fileStartedPrinting(String filename) {
	        synchronized (filesBeingPrinted) {
	            filesBeingPrinted.add(filename);
	        }
	    }

	    void fileFinishedPrinting(String filename) {
	        synchronized (filesBeingPrinted) {
	            filesBeingPrinted.remove(filename);
	            if (filesBeingPrinted.isEmpty()) {
	                synchronized (responseLock) {
	                    responseLock.notify();
	                }
	            }
	        }
	    }



	    private boolean isServerConnected() {
	        return socket != null && socket.isConnected() && !socket.isClosed();
	    }

	
	    private void sendCommandToServer(String command) {
	        if (socket != null && socket.isConnected() && !socket.isClosed()) {
	            out.println(command);
	            out.flush();
	            if (out.checkError()) {
	                System.out.println("Error: Failed to send command. Connection lost.");
	                successful = false;

	                try {
	                    socket.close();
	                } catch (IOException e) {
	                    System.out.println("Error: Failed to close the socket properly.");
	                }
	            }
	        } else {
	            if (command.equals("/leave")) {
	                System.out.print("Error: Disconnection failed. Please connect to the server first.\n");
	                successful = false;
	            } else {
	                System.out.println("Error: Not connected to the server.");
	                successful = false;
	            }
	        }
	    }

	}