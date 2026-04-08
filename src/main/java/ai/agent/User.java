package ai.agent;

/**
 * Represents a single user with chat ID.
 */
public class User {
    private final long chatID;

    /**
     * Create a user with given chat ID.
     *
     * @param chatID Chat ID of the user.
     */
    User(long chatID) {
        this.chatID = chatID;
    }

    /**
     * Returns chat ID of the user.
     *
     * @return Chat ID of the user.
     */
    public long getChatID() {
        return chatID;
    }

    /**
     * Sends message to the user.
     *
     * @param message Message to be sent.
     * @throws Exception Thrown if exception occurs during the process.
     */
    public void sendMessage(String message) throws Exception {
        CommunicationManager.sendMessage(chatID, message);
    }

    /**
     * Sends file to the user.
     *
     * @param path Path of file to be sent.
     * @return True if file was sent successfully, false otherwise.
     * @throws Exception Thrown if exception occurs during the process.
     */
    public boolean sendFile(String path) throws Exception {
        return CommunicationManager.sendFile(chatID, path);
    }

    /**
     * Reads message from the user. It is a non-blocking.
     *
     * @return Null if no message was sent by the user, or else the message sent by the user.
     * @throws Exception Thrown if exception occurs during the process.
     */
    public String readMessage() throws Exception {
        return CommunicationManager.readMessage(chatID);
    }

    /**
     * Waits for the message from the user. It is a blocking method.
     *
     * @return Message sent by the user.
     * @throws Exception Thrown if exception occurs during the process.
     */
    public String waitForMessage() throws Exception {
        return CommunicationManager.waitForMessage(chatID);
    }

    /**
     * Checks if there is unseen messages available or not.
     *
     * @return True if there is any unseen messages, false otherwise.
     * @throws Exception Thrown if exception occurs during the process.
     */
    public boolean hasMessage() throws Exception {
        return CommunicationManager.hasMessage(chatID);
    }
}
