package ai.agent;

import java.io.FileOutputStream;
import java.io.PrintStream;

/**
 * A logger for logging purpose. It is capable of saving logs to a file.
 * Supports four type of logs SEVERE, ERROR, WARN, and INFO.
 */
public class Logger {
    private static PrintStream outputStream;
    private static volatile boolean initialized = false;

    /**
     * Initialized the logger with given configuration.
     *
     * @param configuration Configuration of the Logger.
     * @throws Exception Thrown if mode is logging to file and log file's parent folder doesn't exist.
     */
    public static void init(Configuration configuration) throws Exception {
        if (initialized) {
            return;
        }
        if (configuration.file) {
            outputStream = new PrintStream(new FileOutputStream(configuration.file_name));
        } else {
            outputStream = System.out;
        }
        initialized = true;
    }

    /**
     * Logs the data.
     *
     * @param value Log data.
     * @param type  Type of log.
     */
    public static synchronized void log(String value, Type type) {
        outputStream.printf("%s [%s] : %s\n", System.currentTimeMillis(), type.toString(), value);
    }

    /**
     * Defines type of the log.
     */
    public enum Type {
        SEVERE, ERROR, WARN, INFO
    }

    /**
     * Configuration of the Logger.
     *
     * @param file      True if output should be logged to a file, false otherwise.
     * @param file_name Name of the log file if logs should be saved to a file. Parent folder of the file must exist.
     */
    public record Configuration(boolean file, String file_name) {
    }
}
