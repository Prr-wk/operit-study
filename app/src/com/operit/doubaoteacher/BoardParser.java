package com.operit.doubaoteacher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把模型输出解析成「板书块」。
 * <p>
 * 协议（写在 system prompt 里）：
 * <pre>
 * [step]标题 / 正文 / [/step]      讲解步骤
 * [note]分析 / 依据：… / →答案：…  分析卡（键值行 + 结论行）
 * [key]你说得对！…[/key]            绿色确认块
 * [quiz]题干 / A.… / 答案：B / 解析：…[/quiz]
 * [talk]口语讲解原文…[/talk]        折叠的讲稿
 * [plot] func: y=x^2-2x-3, -3, 5
 * [plot] geo: A(-1,0) B(3,0) C(1,4); AB BC CA
 * </pre>
 * 模型不按协议输出时，退化成普通段落，不会崩。
 */
public class BoardParser {

    private static final Pattern P_NUM = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern P_POINT =
            Pattern.compile("([A-Z])\\s*\\(\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\)");
    private static final Pattern P_SEG = Pattern.compile("[A-Z][A-Z]");
    private static final Pattern P_CIRCLE =
            Pattern.compile("circle\\s*([A-Z])\\s*(?:r\\s*=\\s*)?(-?\\d+(?:\\.\\d+)?)");

    /** 支持的块类型 */
    public static final String T_STEP = "step";
    public static final String T_NOTE = "note";
    public static final String T_KEY = "key";
    public static final String T_QUIZ = "quiz";
    public static final String T_TALK = "talk";
    public static final String T_PLOT = "plot";

    public static class Block {
        public String type = T_STEP;
        public String no = "";
        public String title = "";
        public StringBuilder body = new StringBuilder();

        // plot
        public String kind = "";
        public String expr = "";
        public double xmin = -6, xmax = 6;
        public Map<String, double[]> points = new LinkedHashMap<String, double[]>();
        public List<String[]> segments = new ArrayList<String[]>();
        public List<Object[]> circles = new ArrayList<Object[]>();
        public String raw = "";
    }

    public static List<Block> parse(String text) {
        List<Block> blocks = new ArrayList<Block>();
        if (text == null) {
            return blocks;
        }
        String[] lines = text.replace("\r\n", "\n").split("\n");
        Block cur = null;
        int n = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String t = line.trim();

            // 行内/行尾出现 [/tag]：截断后面的内容，并记下"这一块到此结束"
            boolean endsHere = false;
            int ce = t.indexOf("[/");
            if (ce >= 0) {
                t = t.substring(0, ce).trim();
                endsHere = true;
            }

            if (t.startsWith("[")) {
                int close = t.indexOf(']');
                if (close > 1 && close <= 12) {
                    String tag = t.substring(1, close).toLowerCase();
                    if ("plot".equals(tag)) {
                        Block pb = parsePlot(t);
                        if (pb != null) {
                            blocks.add(pb);
                        }
                        if (endsHere) {
                            cur = null;
                        }
                        continue;
                    }
                    if (tag.matches("[a-z]+")) {
                        cur = new Block();
                        cur.type = tag;
                        n++;
                        cur.no = String.valueOf(n);
                        cur.title = t.substring(close + 1).trim();
                        blocks.add(cur);
                        if (endsHere) {
                            cur = null;
                        }
                        continue;
                    }
                }
            }

            if (t.length() > 0) {
                if (cur == null) {
                    cur = new Block();
                    n++;
                    cur.no = String.valueOf(n);
                    blocks.add(cur);
                }
                if (cur.body.length() > 0) {
                    cur.body.append('\n');
                }
                cur.body.append(t);
            }

            if (endsHere) {
                cur = null;
            }
        }
        return blocks;
    }

    private static Block parsePlot(String line) {
        String s = line.substring(6).trim();
        if (s.length() == 0) {
            return null;
        }
        Block b = new Block();
        b.type = T_PLOT;
        b.raw = s;

        int colon = s.indexOf(':');
        String head = colon > 0 ? s.substring(0, colon).trim().toLowerCase() : "func";
        String rest = colon > 0 ? s.substring(colon + 1).trim() : s;

        if (head.startsWith("geo")) {
            b.kind = "geo";
            Matcher m = P_POINT.matcher(rest);
            while (m.find()) {
                b.points.put(m.group(1), new double[]{
                        Double.parseDouble(m.group(2)),
                        Double.parseDouble(m.group(3))});
            }
            String segPart = rest;
            int sep = rest.indexOf(';');
            if (sep >= 0) {
                segPart = rest.substring(sep + 1);
            } else {
                int bar = rest.indexOf('|');
                if (bar >= 0) {
                    segPart = rest.substring(bar + 1);
                }
            }
            Matcher sm = P_SEG.matcher(segPart);
            while (sm.find()) {
                String pair = sm.group();
                String a = pair.substring(0, 1);
                String c = pair.substring(1, 2);
                if (b.points.containsKey(a) && b.points.containsKey(c)) {
                    b.segments.add(new String[]{a, c});
                }
            }
            Matcher cm = P_CIRCLE.matcher(rest);
            while (cm.find()) {
                if (b.points.containsKey(cm.group(1))) {
                    b.circles.add(new Object[]{cm.group(1),
                            Double.valueOf(Double.parseDouble(cm.group(2)))});
                }
            }
            if (b.points.isEmpty()) {
                return null;
            }
            return b;
        }

        b.kind = "func";
        String expr = rest;
        int eq = expr.indexOf('=');
        if (eq > 0 && eq < 6) {
            expr = expr.substring(eq + 1).trim();
        }
        List<Double> nums = new ArrayList<Double>();
        int cut = -1;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == ',' || c == ';' || c == '，' || c == '；') {
                cut = i;
                break;
            }
        }
        if (cut > 0) {
            expr = rest.substring(0, cut).trim();
            int eq2 = expr.indexOf('=');
            if (eq2 > 0 && eq2 < 6) {
                expr = expr.substring(eq2 + 1).trim();
            }
            Matcher rm = P_NUM.matcher(rest.substring(cut));
            while (rm.find()) {
                nums.add(Double.valueOf(Double.parseDouble(rm.group())));
            }
        }
        expr = expr.replace("，", ",").trim();
        if (expr.length() == 0) {
            return null;
        }
        b.expr = expr;
        if (nums.size() >= 2) {
            double a = nums.get(0).doubleValue();
            double c = nums.get(1).doubleValue();
            if (c > a) {
                b.xmin = a;
                b.xmax = c;
            } else if (a > c) {
                b.xmin = c;
                b.xmax = a;
            }
        }
        return b;
    }

    public static String toJson(List<Block> blocks) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append('[');
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (i > 0) {
                sb.append(',');
            }
            if (T_PLOT.equals(b.type)) {
                sb.append("{\"type\":\"plot\",\"kind\":").append(MiniJson.quote(b.kind));
                sb.append(",\"raw\":").append(MiniJson.quote(b.raw));
                if ("func".equals(b.kind)) {
                    sb.append(",\"spec\":{\"expr\":").append(MiniJson.quote(b.expr));
                    sb.append(",\"xmin\":").append(b.xmin);
                    sb.append(",\"xmax\":").append(b.xmax);
                    sb.append('}');
                } else {
                    sb.append(",\"spec\":{\"points\":{");
                    int k = 0;
                    for (Map.Entry<String, double[]> e : b.points.entrySet()) {
                        if (k++ > 0) {
                            sb.append(',');
                        }
                        sb.append(MiniJson.quote(e.getKey())).append(":[")
                                .append(e.getValue()[0]).append(',').append(e.getValue()[1]).append(']');
                    }
                    sb.append("},\"segments\":[");
                    for (int j = 0; j < b.segments.size(); j++) {
                        if (j > 0) {
                            sb.append(',');
                        }
                        String[] s2 = b.segments.get(j);
                        sb.append('[').append(MiniJson.quote(s2[0])).append(',')
                                .append(MiniJson.quote(s2[1])).append(']');
                    }
                    sb.append("],\"circles\":[");
                    for (int j = 0; j < b.circles.size(); j++) {
                        if (j > 0) {
                            sb.append(',');
                        }
                        Object[] c2 = b.circles.get(j);
                        sb.append("{\"center\":").append(MiniJson.quote(String.valueOf(c2[0])))
                                .append(",\"r\":").append(c2[1]).append('}');
                    }
                    sb.append("]}");
                }
                sb.append('}');
            } else {
                sb.append("{\"type\":").append(MiniJson.quote(b.type));
                sb.append(",\"no\":").append(MiniJson.quote(b.no));
                sb.append(",\"title\":").append(MiniJson.quote(b.title));
                sb.append(",\"body\":").append(MiniJson.quote(b.body.toString()));
                sb.append('}');
            }
        }
        sb.append(']');
        return sb.toString();
    }

    /** TTS 朗读分段：优先念讲稿（talk），没有就念步骤 */
    public static List<String> speakParts(List<Block> blocks) {
        List<String> out = new ArrayList<String>();
        StringBuilder talk = new StringBuilder();

        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (T_TALK.equals(b.type)) {
                String head = b.title == null ? "" : b.title;
                talk.append(clean(head + "\n" + b.body.toString())).append('\n');
            }
        }
        if (talk.toString().trim().length() > 0) {
            String[] parts = talk.toString().split("\\n+");
            for (int i = 0; i < parts.length; i++) {
                String s = parts[i].trim();
                if (s.length() > 0) {
                    out.add(s);
                }
            }
            return out;
        }

        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (T_PLOT.equals(b.type) || T_TALK.equals(b.type)) {
                continue;
            }
            StringBuilder s = new StringBuilder();
            if (b.title != null && b.title.length() > 0) {
                s.append(b.title).append("。");
            }
            s.append(clean(b.body.toString()));
            String t = s.toString().trim();
            if (t.length() > 0) {
                out.add(t);
            }
        }
        return out;
    }

    private static String clean(String s) {
        return s.replace("$", "")
                .replace("**", "")
                .replace("\\", "")
                .replace("#", "")
                .replace("→", "，")
                .replace("✅", "");
    }
}