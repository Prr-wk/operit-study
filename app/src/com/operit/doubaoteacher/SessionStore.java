package com.operit.doubaoteacher;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 会话历史：一个会话一个 json 文件，存在 app 私有目录，不上传 */
public class SessionStore {

    public static class Meta {
        public String id = "";
        public String title = "";
        public long ts = 0;
        public int count = 0;
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "sessions");
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    public static String newId() {
        return "s" + System.currentTimeMillis();
    }

    public static void save(Context c, String id, String title, long ts, List<Msg> msgs) {
        if (id == null || id.length() == 0) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder(4096);
            sb.append('{');
            sb.append("\"id\":").append(MiniJson.quote(id)).append(',');
            sb.append("\"title\":").append(MiniJson.quote(title == null ? "" : title)).append(',');
            sb.append("\"ts\":").append(ts).append(',');
            sb.append("\"msgs\":[");
            for (int i = 0; i < msgs.size(); i++) {
                Msg m = msgs.get(i);
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"r\":").append(m.isUser() ? 1 : 2)
                        .append(",\"t\":").append(MiniJson.quote(m.text))
                        .append(",\"img\":").append(m.dataUrls != null && m.dataUrls.size() > 0 ? 1 : 0)
                        .append('}');
            }
            sb.append("]}");

            File f = new File(dir(c), id + ".json");
            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            out.write(sb.toString().getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignore) {
        }
    }

    public static List<Meta> list(Context c) {
        List<Meta> out = new ArrayList<Meta>();
        File[] fs = dir(c).listFiles();
        if (fs == null) {
            return out;
        }
        for (int i = 0; i < fs.length; i++) {
            String name = fs[i].getName();
            if (!name.endsWith(".json")) {
                continue;
            }
            try {
                String txt = readAll(fs[i]);
                Object o = MiniJson.parse(txt);
                if (!(o instanceof Map)) {
                    continue;
                }
                Map<?, ?> m = (Map<?, ?>) o;
                Meta mt = new Meta();
                mt.id = String.valueOf(m.get("id"));
                mt.title = String.valueOf(m.get("title"));
                Object ts = m.get("ts");
                mt.ts = ts instanceof Double ? ((Double) ts).longValue() : 0L;
                Object ms = m.get("msgs");
                mt.count = ms instanceof List ? ((List<?>) ms).size() : 0;
                out.add(mt);
            } catch (Throwable ignore) {
            }
        }
        Collections.sort(out, new Comparator<Meta>() {
            @Override
            public int compare(Meta a, Meta b) {
                return a.ts == b.ts ? 0 : (a.ts < b.ts ? 1 : -1);
            }
        });
        return out;
    }

    public static List<Msg> load(Context c, String id) {
        List<Msg> out = new ArrayList<Msg>();
        try {
            File f = new File(dir(c), id + ".json");
            if (!f.exists()) {
                return out;
            }
            Object o = MiniJson.parse(readAll(f));
            if (!(o instanceof Map)) {
                return out;
            }
            Object ms = ((Map<?, ?>) o).get("msgs");
            if (!(ms instanceof List)) {
                return out;
            }
            List<?> arr = (List<?>) ms;
            for (int i = 0; i < arr.size(); i++) {
                Object it = arr.get(i);
                if (!(it instanceof Map)) {
                    continue;
                }
                Map<?, ?> mm = (Map<?, ?>) it;
                Object r = mm.get("r");
                int role = (r instanceof Double && ((Double) r).intValue() == 1)
                        ? Msg.ROLE_USER : Msg.ROLE_AI;
                Object t = mm.get("t");
                out.add(new Msg(role, t == null ? "" : String.valueOf(t)));
            }
        } catch (Throwable ignore) {
        }
        return out;
    }

    public static String titleOf(Context c, String id) {
        try {
            File f = new File(dir(c), id + ".json");
            Object o = MiniJson.parse(readAll(f));
            if (o instanceof Map) {
                Object t = ((Map<?, ?>) o).get("title");
                return t == null ? "" : String.valueOf(t);
            }
        } catch (Throwable ignore) {
        }
        return "";
    }

    public static void delete(Context c, String id) {
        try {
            new File(dir(c), id + ".json").delete();
        } catch (Throwable ignore) {
        }
    }

    private static String readAll(File f) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        in.close();
        return new String(bos.toByteArray(), "UTF-8");
    }
}