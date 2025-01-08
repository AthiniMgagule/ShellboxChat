import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;

public class SecureShellboxChat {
    private static final String CHAT_FILE = "secure_chat.txt";
    private static final String USER_FILE = "users.txt";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        SecretKey secretKey = null;
        String displayName = null;

        // Ensure files exist
        createFileIfNotExists(CHAT_FILE);
        createFileIfNotExists(USER_FILE);

        System.out.println("Welcome to Secure Shellbox Chat!");
        System.out.println("1. Create a new chat");
        System.out.println("2. Join an existing chat");
        System.out.print("Choose an option (1/2): ");
        int choice = scanner.nextInt();
        scanner.nextLine(); // Consume the newline

        if (choice == 1) {
            System.out.print("Enter a username: ");
            String username = scanner.nextLine();
            System.out.print("Create a password: ");
            String password = scanner.nextLine();

            try {
                saveUser(username, password);
                secretKey = generateKeyFromPassword(password);
                System.out.println("Chat created successfully. Share your username and password securely!");
            } catch (IOException e) {
                System.err.println("Error creating user: " + e.getMessage());
                scanner.close();
                return;
            }

        } else if (choice == 2) {
            System.out.print("Enter your username: ");
            String username = scanner.nextLine();
            System.out.print("Enter your password: ");
            String password = scanner.nextLine();

            try {
                if (authenticateUser(username, password)) {
                    secretKey = generateKeyFromPassword(password);
                    System.out.println("Authentication successful. Joining chat...");
                } else {
                    System.out.println("Invalid username or password.");
                    scanner.close();
                    return;
                }
            } catch (IOException e) {
                System.err.println("Error authenticating user: " + e.getMessage());
                scanner.close();
                return;
            }

        } else {
            System.out.println("Invalid option. Exiting...");
            scanner.close();
            return;
        }

        // Set display name for the chat
        System.out.print("Enter your display name: ");
        displayName = scanner.nextLine();

        System.out.println("Type your messages below. Type '/exit' to leave the chat. Type '/history' to view chat history.");

        // Reader thread to display messages
        SecretKey finalSecretKey = secretKey;
        Thread readerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new FileReader(CHAT_FILE))) {
                String line;
                while (true) {
                    while ((line = reader.readLine()) != null) {
                        System.out.println(decrypt(line, finalSecretKey));
                    }
                    Thread.sleep(1000); // Polling interval
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("Error reading chat file: " + e.getMessage());
            }
        });

        readerThread.setDaemon(true); // Allow program to exit when main thread exits
        readerThread.start();

        // Writer loop
        try (PrintWriter writer = new PrintWriter(new FileWriter(CHAT_FILE, true))) {
            // Notify that the user has joined the chat
            String joinMessage = String.format("[%s] %s has joined the chat.", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), displayName);
            writer.println(encrypt(joinMessage, secretKey));
            writer.flush();

            while (true) {
                String message = scanner.nextLine();

                if (message.equalsIgnoreCase("/exit")) {
                    // Notify that the user has left the chat
                    String leaveMessage = String.format("[%s] %s has left the chat.", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), displayName);
                    writer.println(encrypt(leaveMessage, secretKey));
                    writer.flush();
                    System.out.println("Exiting Secure Shellbox Chat. Goodbye!");
                    break;
                } else if (message.equalsIgnoreCase("/history")) {
                    displayChatHistory(secretKey);
                } else {
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    String formattedMessage = String.format("[%s] %s: %s", timestamp, displayName, message);
                    writer.println(encrypt(formattedMessage, secretKey));
                    writer.flush();
                }
            }
        } catch (IOException e) {
            System.err.println("Error writing to chat file: " + e.getMessage());
        }
        scanner.close();
    }

    private static void createFileIfNotExists(String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                System.err.println("Error creating file " + fileName + ": " + e.getMessage());
            }
        }
    }

    private static void saveUser(String username, String password) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(USER_FILE, true))) {
            writer.println(username + ":" + hashPassword(password));
        }
    }

    private static boolean authenticateUser(String username, String password) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(USER_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts[0].equals(username) && parts[1].equals(hashPassword(password))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static SecretKey generateKeyFromPassword(String password) throws IOException {
        try {
            byte[] key = password.getBytes("UTF-8");
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16); // Use first 128 bits
            return new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            throw new IOException("Error generating encryption key", e);
        }
    }

    private static String encrypt(String data, SecretKey secretKey) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes("UTF-8")));
        } catch (Exception e) {
            throw new IOException("Error encrypting data", e);
        }
    }

    private static String decrypt(String encryptedData, SecretKey secretKey) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedData)), "UTF-8");
        } catch (Exception e) {
            throw new IOException("Error decrypting data", e);
        }
    }

    private static void displayChatHistory(SecretKey secretKey) {
        try (BufferedReader reader = new BufferedReader(new FileReader(CHAT_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(decrypt(line, secretKey));
            }
        } catch (IOException e) {
            System.err.println("Error reading chat history: " + e.getMessage());
        }
    }

    private static String hashPassword(String password) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IOException("Error hashing password", e);
        }
    }
}