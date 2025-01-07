import java.io.*;
import java.nio.channels.FileLock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class Shellboxchat {
    private static final String CHAT_FILE = "chat.txt";
    private static final String TIME_FILE = "time.txt";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Prompt for username
        System.out.print("Enter your username: ");
        String username = scanner.nextLine();

        System.out.println("Welcome to Shellbox Chat, " + username + "!");
        System.out.println("Type your messages below. Type '/exit' to leave the chat. Type '/history' to view chat history.");

        // Initialize chat file and time file
        try {
            new File(CHAT_FILE).createNewFile();
            new File(TIME_FILE).createNewFile();
            try (PrintWriter chatWriter = new PrintWriter(new FileWriter(CHAT_FILE, true));
                 PrintWriter timeWriter = new PrintWriter(new FileWriter(TIME_FILE, true))) {
                String joinMessage = username + " has joined the chat. [" + getCurrentTime() + "]";
                chatWriter.println(joinMessage);
                timeWriter.println(joinMessage);
            }
        } catch (IOException e) {
            System.err.println("Error initializing files: " + e.getMessage());
            scanner.close();
            return;
        }

        // Reader thread to display messages
        Thread readerThread = new Thread(() -> {
            try (RandomAccessFile reader = new RandomAccessFile(CHAT_FILE, "r")) {
                long lastKnownPosition = 0;
                String line;

                while (true) {
                    reader.seek(lastKnownPosition); // Move to the last known position
                    while ((line = reader.readLine()) != null) {
                        // Check if the line was sent by the current user (filter it out)
                        if (!line.contains(username + ":")) {
                            // Display the line as it is
                            System.out.println(line);
                        }
                        lastKnownPosition = reader.getFilePointer(); // Update last known position
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
        try (RandomAccessFile file = new RandomAccessFile(CHAT_FILE, "rw")) {
            while (true) {
                // System.out.print(username + ": ");
                String message = scanner.nextLine();

                if (message.equalsIgnoreCase("/exit")) {
                    String exitMessage = username + " has left the chat. [" + getCurrentTime() + "]";
                    appendToFile(file, exitMessage);
                    appendToTimeFile(exitMessage);
                    System.out.println("Exiting Shellbox Chat. Goodbye!");
                    break;
                } else if (message.equalsIgnoreCase("/history")) {
                    displayChatHistory();
                } else {
                    appendToFile(file, username + ": " + message + " [" + getCurrentTime() + "]");
                }
            }
        } catch (IOException e) {
            System.err.println("Error writing to chat file: " + e.getMessage());
        }

        scanner.close();
    }

    private static void appendToFile(RandomAccessFile file, String message) throws IOException {
        file.seek(file.length()); // Move to end of file
        try (FileLock lock = file.getChannel().lock()) { // Lock file for writing
            file.write((message + System.lineSeparator()).getBytes());
        }
    }

    private static void appendToTimeFile(String message) {
        try (PrintWriter timeWriter = new PrintWriter(new FileWriter(TIME_FILE, true))) {
            timeWriter.println(message);
        } catch (IOException e) {
            System.err.println("Error writing to time file: " + e.getMessage());
        }
    }

    private static void displayChatHistory() {
        try (BufferedReader reader = new BufferedReader(new FileReader(CHAT_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            System.err.println("Error reading chat history: " + e.getMessage());
        }
    }

    private static String getCurrentTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        return LocalDateTime.now().format(formatter);
    }
}
