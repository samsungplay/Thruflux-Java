package sender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SenderLogger {
    private static final Logger logger = LoggerFactory.getLogger(SenderLogger.class);
    public static void info(String message) {
        logger.info(message);
    }

    public static void debug(String message) {
        logger.debug(message);
    }

    public static void error(String message) {
        logger.error(message);
    }



}
