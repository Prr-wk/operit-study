package com.operit.doubaoteacher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 极简 JSON 解析/生成，避免依赖 org.json（部分 android.jar 不带） */
public final class MiniJson {

    private final String s;
    private int i;

    private MiniJson(String s) {
        this.s = s;
        this.i = 0;
    }

    public static Object parse(String text) throws Exception {
        MiniJson p = new MiniJson(text);
        p.ws();
        Object v = p.value();
        p.ws();
        return v;
    }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                i++;
            } else {
                break;
            }
        }
    }

    private Object value() throws Exception {
        if (i >= s.length()) {
            throw new Exception("json eof");
        }
        char c = s.charAt(i);
        if (c == '{') {
            return obj();
        }
        if (c == '[') {
            return arr();
        }
        if (c == '"') {
            return string();
        }
        if (s.startsWith("true", i)) {
            i += 4;
            return Boolean.TRUE;
        }
        if (s.startsWith("false", i)) {
            i += 5;
            return Boolean.FALSE;
        }
        if (s.startsWith("null", i)) {
            i += 4;
            return null;
        }
        return number();
    }

    private Map<String, Object> obj() throws Exception {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        i++; // {
        ws();
        if (i < s.length() && s.charAt(i) == '}') {
            i++;
            return m;
        }
        while (true) {
            ws();
            String k = string();
            ws();
            if (i >= s.length() || s.charAt(i) != ':') {
                throw new Exception("expect :");
            }
            i++;
            ws();
            m.put(k, value());
            ws();
            if (i < s.length() && s.charAt(i) == ',') {
                i++;
                continue;
            }
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return m;
            }
            throw new Exception("expect , or }");
        }
    }

    private List<Object> arr() throws Exception {
        List<Object> l = new ArrayList<Object>();
        i++; // [
        ws();
        if (i < s.length() && s.charAt(i) == ']') {
            i++;
            return l;
        }
        while (true) {
            ws();
            l.add(value());
            ws();
            if (i < s.length() && s.charAt(i) == ',') {
                i++;
                continue;
            }
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return l;
            }
            throw new Exception("expect , or ]");
        }
    }

    private String string() throws Exception {
        if (s.charAt(i) != '"') {
            throw new Exception("expect \"");
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = s.charAt(i++);
            switch (e) {
                case 'n':
                    sb.append('\n');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 'b':
                    sb.append('\b');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                case 'u':
                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default:
                    sb.append(e);
            }
        }
        throw new Exception("unterminated string");
    }

    private Object number() throws Exception {
        int start = i;
        while (i < s.length()) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e'
                    || c == 'E') {
                i++;
            } else {
                break;
            }
        }
        String t = s.substring(start, i);
        if (t.length() == 0) {
            throw new Exception("bad number");
        }
        return Double.valueOf(t);
    }

    // ============ 生成 ============

    public static String quote(String v) {
        if (v == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(v.length() + 16);
        sb.append('"');
        for (int k = 0; k < v.length(); k++) {
            char c = v.charAt(k);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}