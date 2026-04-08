package ai.agent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * AuthorizationManager is a basic authorization system. It's memory is volatile
 * i.e when program exits, all data is lost.
 */
public class AuthorizationManager {
    private static final Semaphore lock = new Semaphore(1, true);
    private static Map<Long, Long> authorized;
    private static String password;
    private static long expiryTime;
    private static boolean initialized = false;

    /**
     * Initializes the AuthorizationManager with given configuration.
     *
     * @param config Configuration of AuthorizationManager.
     */
    public static void init(Configuration config) {
        if (initialized) {
            return;
        }
        authorized = new ConcurrentHashMap<>();
        password = config.password();
        expiryTime = config.expiry_time();
        initialized = true;
    }

    private static void assertInitialization() {
        if (!initialized) {
            throw new IllegalStateException("Authorization manager not initialized.");
        }
    }

    private static void printException(String msg) {
        Logger.log("Exception in AuthorizationManager: ".concat(msg), Logger.Type.ERROR);
    }

    private static synchronized boolean setLock() {
        try {
            lock.acquire();
            return true;
        } catch (Exception e) {
            printException(e.toString());
            return false;
        }
    }

    private static synchronized void unlock() {
        lock.release();
    }

    /**
     * Checks if user with chat ID [cid] is authorized or not.
     *
     * @param cid Chat ID of the user.
     * @return Returns true if the user is authorized, false otherwise.
     */
    public static boolean isAuthorized(Long cid) {
        assertInitialization();
        if (!setLock()) {
            return false;
        }
        try {
            if (expiryTime <= 0) {
                return authorized.containsKey(cid);
            }
            Long expiry = authorized.get(cid);
            if (expiry == null) {
                return false;
            }
            if (expiry >= System.currentTimeMillis()) {
                return true;
            }
            authorized.remove(cid);
            return false;
        } catch (Exception e) {
            printException(e.toString());
            return false;
        } finally {
            unlock();
        }
    }

    /**
     * Tries to authorize the user with chat ID [cid].
     *
     * @param cid  Chat ID of the user.
     * @param pass Password.
     * @return True if user is successfully authorized, false otherwise.
     */
    public static boolean authorize(Long cid, String pass) {
        assertInitialization();
        if (!setLock()) {
            return false;
        }
        try {
            boolean result = password.equals(pass);
            if (result) {
                authorized.put(cid, System.currentTimeMillis() + expiryTime);
            }
            return result;
        } catch (Exception e) {
            printException(e.toString());
            return false;
        } finally {
            unlock();
        }
    }

    /**
     * Deauthorizes the user.
     *
     * @param cid Chat ID [cid] of the user to be deauthorized.
     */
    public static void deauthorize(Long cid) {
        assertInitialization();
        if (!setLock()) {
            return;
        }
        try {
            authorized.remove(cid);
        } catch (Exception e) {
            printException(e.toString());
        } finally {
            unlock();
        }
    }

    /**
     * Configuration for AuthorizationManager.
     *
     * @param password    Authorization password.
     * @param expiry_time Authorization expiry time of authorized users, -1 means authorization never expires.
     */
    public record Configuration(String password, long expiry_time) {
    }
}
