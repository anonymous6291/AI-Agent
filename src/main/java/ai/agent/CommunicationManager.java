package ai.agent;

import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages communication with the users. Handles requests and files. It also has blacklist
 * feature which blacklists users after certain number of failed password tries.
 */
public class CommunicationManager {
    private static final String FILE_API_URL = "https://api.telegram.org/file/bot";
    private static final String START_BOT_COMMAND = "/start";
    private static final String START_AGENT_COMMAND = "/startagent";
    private static final String STOP_AGENT_COMMAND = "/stopagent";
    private static final String DEAUTHORIZE_COMMAND = "/deauth";
    private static final String MESSAGE = """
            You are now authorized to send commands and control the agent. The list of valid commands are:
            
            1) /startagent  => Start agent
            2) /stopagent   => Stop agent (Forcibly stops the currently running agent)
            3) /deauth      => Deauthorize
            """;
    private static final int MAX_TRIES = 5;
    private static final Duration MESSAGE_RECHECK_DURATION = Duration.ofSeconds(2);
    private static final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final Map<Long, Data> clients = new ConcurrentHashMap<>();
    private static final Map<Long, Integer> passwordTries = new ConcurrentHashMap<>();
    private static TelegramClient telegramClient;
    private static volatile boolean initialized = false;
    private static String botToken;

    /**
     * Initializes the CommunicationManager with given configuration.
     *
     * @param config Configuration of the CommunicationManager.
     */
    public static void init(Configuration config) {
        if (initialized) {
            return;
        }
        try {
            FileInputStream botTokenFile = new FileInputStream(config.bot_token_file());
            botToken = new String(botTokenFile.readAllBytes());
            botTokenFile.close();
            telegramClient = new OkHttpTelegramClient(botToken);
            TelegramBotsLongPollingApplication telegramBotsLongPollingApplication = new TelegramBotsLongPollingApplication();
            telegramBotsLongPollingApplication.registerBot(botToken, new LongPollingUpdateConsumer() {
                @Override
                public void consume(List<Update> updates) {
                    for (Update update : updates) {
                        handleUpdate(update);
                    }
                }

                private Data registerUser(long chatId) {
                    Data data = new Data(new Agent(new User(chatId)), new ArrayDeque<>());
                    clients.put(chatId, data);
                    return data;
                }

                private void handleFile(Update update) throws Exception {
                    Document document = update.getMessage().getDocument();
                    String fileId = document.getFileId();
                    String fileName = document.getFileName();
                    sendMessage(update.getMessage().getChatId(), "Downloading file [".concat(fileName).concat("] ."));
                    var file = telegramClient.execute(GetFile.builder().fileId(fileId).build());
                    String fileUrl = FILE_API_URL.concat(botToken).concat("/").concat(file.getFilePath());
                    HttpRequest request = HttpRequest.newBuilder(URI.create(fileUrl)).GET().build();
                    HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    BufferedInputStream bis = new BufferedInputStream(response.body());
                    FileOutputStream fos = new FileOutputStream("./".concat(fileName));
                    bis.transferTo(fos);
                    fos.close();
                    bis.close();
                    sendMessage(update.getMessage().getChatId(), "File [".concat(fileName).concat("] saved."));
                    Logger.log("[" + update.getMessage().getChatId() + "] => File [".concat(fileName).concat("] saved."), Logger.Type.WARN);
                }

                private void handleAuthorizedUpdate(Update update) throws Exception {
                    Message message = update.getMessage();
                    long chatId = message.getChatId();
                    if (message.hasText()) {
                        String text = message.getText();
                        switch (text) {
                            case START_BOT_COMMAND ->
                                    telegramClient.execute(SendMessage.builder().chatId(chatId).text("You are already authorized.").build());
                            case START_AGENT_COMMAND -> {
                                Data data = clients.get(chatId);
                                if (data == null) {
                                    data = registerUser(chatId);
                                }
                                data.messages().clear();
                                data.agent().start();
                            }
                            case STOP_AGENT_COMMAND -> {
                                Data data = clients.get(chatId);
                                if (data != null) {
                                    SendMessage msg = SendMessage.builder().chatId(chatId).text("Stopping agent.").build();
                                    telegramClient.execute(msg);
                                    data.agent().stop();
                                }
                            }
                            case DEAUTHORIZE_COMMAND -> {
                                try {
                                    Data data = clients.remove(chatId);
                                    if (data != null) {
                                        data.agent().stop();
                                    }
                                } catch (Exception _) {
                                }
                                AuthorizationManager.deauthorize(chatId);
                                telegramClient.execute(SendMessage.builder().chatId(chatId).text("You are deauthorized.").build());
                            }
                            default -> {
                                Data data = clients.get(chatId);
                                if (data == null) {
                                    data = registerUser(chatId);
                                    data.agent().start();
                                }
                                data.messages().offer(text);
                            }
                        }
                    } else if (message.hasDocument()) {
                        handleFile(update);
                    }
                }

                private void handleUnauthorizedUpdate(Update update) throws Exception {
                    Message message = update.getMessage();
                    long chatId = message.getChatId();
                    int tries = passwordTries.getOrDefault(chatId, 0);
                    if (tries >= MAX_TRIES) {
                        SendMessage sendMessage = SendMessage.builder().chatId(chatId).text("Number of password tries exceeded. You are blocked.").build();
                        telegramClient.execute(sendMessage);
                        return;
                    }
                    String reply;
                    if (message.hasText()) {
                        switch (message.getText()) {
                            case START_BOT_COMMAND -> reply = "Enter the password:";
                            case START_AGENT_COMMAND, STOP_AGENT_COMMAND, DEAUTHORIZE_COMMAND ->
                                    reply = "You aren't authorized to perform the action.";
                            default -> {
                                if (AuthorizationManager.authorize(chatId, message.getText())) {
                                    Agent agent = new Agent(new User(chatId));
                                    clients.put(chatId, new Data(agent, new ArrayDeque<>()));
                                    passwordTries.remove(chatId);
                                    Logger.log("[" + chatId + "] is now authorized.", Logger.Type.INFO);
                                    reply = MESSAGE;
                                } else {
                                    tries++;
                                    reply = "Incorrect password. Remaining tries [" + (MAX_TRIES - tries) + "] .";
                                    if (tries == MAX_TRIES) {
                                        reply += "\nYou are now blocked.";
                                        Logger.log("[" + chatId + "] is blocked due to excess number of password tries.", Logger.Type.WARN);
                                    }
                                    passwordTries.put(chatId, tries);
                                }
                            }
                        }
                    } else {
                        reply = "You aren't authorized. Please enter the password.";
                    }
                    SendMessage response = SendMessage.builder().chatId(chatId).text(reply).build();
                    telegramClient.execute(response);
                }


                private void handleUpdate(Update update) {
                    try {
                        if (AuthorizationManager.isAuthorized(update.getMessage().getChatId())) {
                            handleAuthorizedUpdate(update);
                        } else {
                            handleUnauthorizedUpdate(update);
                        }
                    } catch (Exception e) {
                        Logger.log("Exception occurred while handling the Telegram update: " + e, Logger.Type.ERROR);
                    }
                }
            });
        } catch (Exception e) {
            Logger.log("Exception [" + e + "] occurred while starting the Telegram handler. Shutting down the system now.", Logger.Type.SEVERE);
            System.exit(0);
        }
        initialized = true;
    }

    private static void assertAuthorization(long chatId) throws Exception {
        if (AuthorizationManager.isAuthorized(chatId)) {
            return;
        }
        Logger.log("[" + chatId + "] isn't authorized to perform the necessary actions.", Logger.Type.ERROR);
        throw new IllegalAccessException(chatId + " is not authorized.");
    }

    /**
     * Sends message to the user.
     *
     * @param chatId  Chat ID of the user.
     * @param message Message to be sent.
     * @throws Exception Thrown if exception occurs during the process.
     */
    public static void sendMessage(long chatId, String message) throws Exception {
        assertAuthorization(chatId);
        SendMessage sendMessage = SendMessage.builder().chatId(chatId).text(message).build();
        telegramClient.execute(sendMessage);
    }

    /**
     * Sends file to the user.
     *
     * @param chatId Chat ID of the user.
     * @param path   Path of the file.
     * @return True if file is sent, false otherwise.
     * @throws Exception Thrown if exception occurs during the process.
     */
    public static boolean sendFile(long chatId, String path) throws Exception {
        assertAuthorization(chatId);
        File file = new File(path);
        if (!file.exists()) {
            return false;
        }
        SendDocument document = SendDocument.builder().chatId(chatId).document(new InputFile(file)).build();
        telegramClient.execute(document);
        return true;
    }

    /**
     * Reads the message of the user. It a non-blocking method.
     *
     * @param chatId Chat ID of the user.
     * @return Message of the user.
     * @throws Exception Thrown if exception occurs during the process.
     */
    public static String readMessage(long chatId) throws Exception {
        assertAuthorization(chatId);
        Data data = clients.get(chatId);
        if (data == null) {
            return null;
        }
        return data.messages().poll();
    }

    /**
     * Waits for the user message. It is a blocking method.
     *
     * @param chatId Chat ID of the user.
     * @return Message from the user.
     * @throws Exception Thrown if exception occurs during the process.
     */
    public static String waitForMessage(long chatId) throws Exception {
        String reply;
        while ((reply = readMessage(chatId)) == null) {
            Thread.sleep(MESSAGE_RECHECK_DURATION);
        }
        return reply;
    }

    /**
     * Reads all unseen messages from the user.
     *
     * @param chatId Chat ID of the user.
     * @return Queue of all messages from the user.
     * @throws Exception Thrown if exception occurs during the process.
     */
    public static Queue<String> readAllMessages(long chatId) throws Exception {
        assertAuthorization(chatId);
        Data current = clients.get(chatId);
        Queue<String> messages = current.messages();
        clients.put(chatId, new Data(current.agent(), new ArrayDeque<>()));
        return messages;
    }

    /**
     * Checks if there is any unseen messages from the user.
     *
     * @param chatId Chat ID of the user.
     * @return True if there is any unseen message, false otherwise.
     * @throws Exception Thrown if exception occurs during the process.
     */
    public static boolean hasMessage(long chatId) throws Exception {
        assertAuthorization(chatId);
        return clients.containsKey(chatId) && !clients.get(chatId).messages().isEmpty();
    }

    record Data(Agent agent, Queue<String> messages) {
    }

    /**
     * Configuration of the CommunicationManager.
     *
     * @param bot_token_file File which contains bot token of the bot.
     */
    public record Configuration(String bot_token_file) {
    }
}

