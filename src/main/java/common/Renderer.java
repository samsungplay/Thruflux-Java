package common;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;

import java.io.IOException;

public class Renderer {
    private static Terminal terminal;

    private static void createTerminalIfNotExists() throws IOException {
        if (terminal == null) {
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .jna(false)
                    .jansi(true)
                    .build();
        }
    }

    public static void start() {
        try {
            createTerminalIfNotExists();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.puts(InfoCmp.Capability.clear_screen);
        terminal.puts(InfoCmp.Capability.cursor_address, 0, 0);
        terminal.flush();
    }

    public static void stop() {
        try {
            createTerminalIfNotExists();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        terminal.puts(InfoCmp.Capability.cursor_visible);
        terminal.flush();
    }

    public static void render(String frame) {
        try {
            createTerminalIfNotExists();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        terminal.puts(InfoCmp.Capability.cursor_address, 0, 0);
        terminal.puts(InfoCmp.Capability.clear_screen);
        terminal.writer().println(frame);
        terminal.flush();
    }

    public static void close() throws IOException {
        if(terminal != null)
            terminal.close();
    }

}
