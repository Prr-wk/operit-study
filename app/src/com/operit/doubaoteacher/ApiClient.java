package com.operit.doubaoteacher;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

/** OpenAI 兼容 /chat/completions 的流式调用（零第三方依赖） */
public class ApiClient {

    public interface StreamCb {
        /** 累计后的全文 */
        void onDelta(String full);

        void onDone(String full);

        void onError(String message);
    }

    public static Thread stream(final Cfg cfg, final List<Msg> history, final StreamCb cb) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    byte[] data = buildBody(cfg, history).getBytes("UTF-8");

                    conn = (HttpURLConnection) new URL(cfg.chatUrl()).openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(180000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setRequestProperty("Accept", "text/event-stream");
                    conn.setRequestProperty("Authorization", "Bearer " + cfg.key.trim());
                    conn.setFixedLengthStreamingMode(data.length);

                    OutputStream os = conn.getOutputStream();
                    os.write(data);
                    os.flush();
                    os.close();

                    int code = conn.getResponseCode();
                    if (code != 200) {
                        cb.onError("HTTP " + code + " " + readAll(conn.getErrorStream()));
                        return;
                    }

                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder full = new StringBuilder();
                    String line;
                    boolean gotSse = false;

                    while ((line = br.readLine()) != null) {
                        if (line.length() == 0 || !line.startsWith("data:")) {
                            continue;
                        }
                        gotSse = true;
                        String payload = line.substring(5).trim();
                        if ("[DONE]".equals(payload)) {
                            break;
                        }
                        String piece = extractDelta(payload);
                        if (piece != null && piece.length() > 0) {
                            full.append(piece);
                            cb.onDelta(full.toString());
                        }
                    }
                    br.close();

                    if (gotSse && full.length() == 0) {
                        cb.onError("模型只返回了思考过程，没有正文（可能被截断），请重试");
                        return;
                    }
                    cb.onDone(full.toString());
                } catch (Exception e) {
                    cb.onError(String.valueOf(e));
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        });
        t.start();
        return t;
    }

    private static String extractDelta(String json) {
        try {
            Object root = MiniJson.parse(json);
            if (!(root instanceof Map)) {
                return null;
            }
            Object ch = ((Map<?, ?>) root).get("choices");
            if (!(ch instanceof List) || ((List<?>) ch).isEmpty()) {
                return null;
            }
            Object first = ((List<?>) ch).get(0);
            if (!(first instanceof Map)) {
                return null;
            }
            Map<?, ?> m0 = (Map<?, ?>) first;

            Object delta = m0.get("delta");
            if (delta instanceof Map) {
                // 只取正文。deepseek-flash 默认开思考模式，reasoning_content 是
                // 模型的碎碎念，不能当板书内容显示出来。
                Object c = ((Map<?, ?>) delta).get("content");
                if (c instanceof String) {
                    return (String) c;
                }
                return null;
            }
            Object msg = m0.get("message");
            if (msg instanceof Map) {
                Object c = ((Map<?, ?>) msg).get("content");
                if (c instanceof String) {
                    return (String) c;
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private static String buildBody(Cfg cfg, List<Msg> history) throws Exception {
        StringBuilder sb = new StringBuilder(2048);
        sb.append('{');
        sb.append("\"model\":").append(MiniJson.quote(cfg.model.trim())).append(',');
        sb.append("\"stream\":true,");
        sb.append("\"temperature\":").append(cfg.temperature()).append(',');
        sb.append("\"messages\":[");

        sb.append("{\"role\":\"system\",\"content\":")
                .append(MiniJson.quote(cfg.systemPrompt())).append('}');

        for (int i = 0; i < history.size(); i++) {
            Msg m = history.get(i);
            sb.append(',');
            sb.append("{\"role\":").append(MiniJson.quote(m.isUser() ? "user" : "assistant"));
            sb.append(",\"content\":");
            if (m.isUser() && m.dataUrls != null && m.dataUrls.size() > 0) {
                sb.append('[');
                sb.append("{\"type\":\"text\",\"text\":")
                        .append(MiniJson.quote(m.text.length() > 0 ? m.text : "请看看这道题"))
                        .append('}');
                for (int k = 0; k < m.dataUrls.size(); k++) {
                    sb.append(",{\"type\":\"image_url\",\"image_url\":{\"url\":")
                            .append(MiniJson.quote(m.dataUrls.get(k)));
                    // DeepSeek 支持 detail 级别；题目截图要看清小字，用 high
                    if (cfg.base != null && cfg.base.contains("deepseek")) {
                        sb.append(",\"detail\":\"high\"");
                    }
                    sb.append("}}");
                }
                sb.append(']');
            } else {
                sb.append(MiniJson.quote(m.text));
            }
            sb.append('}');
        }

        sb.append("]}");
        return sb.toString();
    }

    private static String readAll(InputStream in) {
        if (in == null) {
            return "";
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
                if (bos.size() > 8192) {
                    break;
                }
            }
            in.close();
            String s = new String(bos.toByteArray(), "UTF-8").replace('\n', ' ');
            return s.length() > 400 ? s.substring(0, 400) : s;
        } catch (Exception e) {
            return "";
        }
    }

    /** 连通测试：发一个 1 token 的请求，返回 ok / HTTP xxx / net: xxx */
    public static String ping(String url, String key, String model) {
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(20000);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Authorization", "Bearer " + key);
            c.setDoOutput(true);
            String body = "{\"model\":\"" + model + "\",\"messages\":"
                    + "[{\"role\":\"user\",\"content\":\"hi\"}],"
                    + "\"max_tokens\":1,\"stream\":false}";
            c.getOutputStream().write(body.getBytes("UTF-8"));
            int code = c.getResponseCode();
            if (code == 200) {
                return "ok";
            }
            String msg = "";
            try {
                java.io.InputStream es = c.getErrorStream();
                if (es != null) {
                    byte[] b = new byte[512];
                    int n = es.read(b);
                    if (n > 0) {
                        msg = new String(b, 0, n, "UTF-8");
                    }
                }
            } catch (Throwable ignore) {
            }
            if (msg.length() > 120) {
                msg = msg.substring(0, 120) + "...";
            }
            return "HTTP " + code + (msg.length() > 0 ? "\n" + msg : "");
        } catch (Throwable t) {
            return "net: " + t.getClass().getSimpleName() + " " + String.valueOf(t.getMessage());
        } finally {
            try {
                if (c != null) {
                    c.disconnect();
                }
            } catch (Throwable ignore) {
            }
        }
    }
}