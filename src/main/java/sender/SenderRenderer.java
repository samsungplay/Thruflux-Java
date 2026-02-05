package sender;

import common.Renderer;
import common.Utils;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SenderRenderer extends Renderer {

    private static ScheduledFuture<?> renderLoop;


    public static void startRenderLoop() {
        start();
        renderLoop = SenderWorker.getRenderWorker().scheduleAtFixedRate(SenderRenderer::renderSenderUI,0,250, TimeUnit.MILLISECONDS);
    }


    public static void stopRenderLoop() {
        if(renderLoop != null) {
            renderLoop.cancel(true);
        }
        SenderWorker.getRenderWorker().shutdownNow();
    }


    public static void renderSenderUI() {
        StringBuilder sb = new StringBuilder();

        int[] w = {30, 15, 11, 7, 9, 5, 12, 12};
        String[] headers = {"peer", "status", "link", "files", "resumed", "%", "rate", "ETA"};

        sb.append(">>.. May your sockets stay open.\n\n");

        int boxWidth = 74;
        sb.append("+").append("-".repeat(boxWidth + 2)).append("+\n");
        sb.append("| ").append(padRight("🔑  JOIN CODE", boxWidth)).append(" |\n");
        sb.append("| ").append(padRight(SenderStateHolder.getJoinCode(), boxWidth)).append(" |\n");
        String receiverLine = "👥  Receiver: run `thru join " + SenderStateHolder.getJoinCode() + " --out <dir>`";
        sb.append("| ").append(padRight(receiverLine, boxWidth)).append(" |\n");
        sb.append("+").append("-".repeat(boxWidth + 2)).append("+\n\n");

        sb.append("📦  Sharing the following paths:\n");
        for (String p : SenderStateHolder.getManifest().getPaths()) {
            sb.append(" - ").append(p).append("\n");
        }
        sb.append("\n");

        StringBuilder borderSb = new StringBuilder();
        for (int width : w) borderSb.append("+").append("-".repeat(width + 2));
        String tableBorder = borderSb.append("+\n").toString();

        sb.append(tableBorder).append("|");
        for (int i = 0; i < headers.length; i++) {
            sb.append(" ").append(center(headers[i], w[i])).append(" |");
        }
        sb.append("\n").append(tableBorder);

        for (SenderStateHolder.ReceiverInfo info : SenderStateHolder.getReceivers()) {
            Duration d = Duration.ofSeconds(info.getEta().get());
            String eta = String.format("%02d:%02d:%02d", d.toHours(), (d.toMinutes() % 60), (d.getSeconds() % 60));
            String[] data = {
                    info.getReceiverId().get(),
                    info.getStatus().get(),
                    info.getLink().get(),
                    info.getFiles() + "/" + SenderStateHolder.getManifest().getFilesCount(),
                    String.valueOf(info.getResumed()),
                    info.getPercent() + "%",
                    Utils.sizeToReadableFormat(info.getRatePerSecond().get()) + "/s",
                    eta
            };
            sb.append("|");
            for (int i = 0; i < data.length; i++) {
                sb.append(" ").append(center(trim(data[i], w[i]), w[i])).append(" |");
            }
            sb.append("\n");
        }

        sb.append(tableBorder);
        render(sb.toString());
    }

    private static String center(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s.substring(0, n);
        int left = (n - s.length()) / 2;
        int right = n - s.length() - left;
        return " ".repeat(left) + s + " ".repeat(right);
    }


    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String padRight(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s.substring(0, n);
        return s + " ".repeat(n - s.length());
    }

    private static String padLeft(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s.substring(0, n);
        return " ".repeat(n - s.length()) + s;
    }
}
