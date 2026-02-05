package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerLogger {
    private static final Logger logger = LoggerFactory.getLogger(ServerLogger.class);

    public static void info(String message) {
        logger.info(message);
    }

    public static void debug(String message) {
        logger.debug(message);
    }

    public static void error(String message) {
        logger.error(message);
    }

    public static void warn(String message) {
        logger.warn(message);
    }

}
