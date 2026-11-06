package com.operit.doubaoteacher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.Map;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_PICK = 101;
    private static final int REQ_SESSION = 105;

    private Cfg cfg = new Cfg();
    private final List<Msg> messages = new ArrayList<Msg>();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private ScrollView scroll;
    private LinearLayout chatBox;
    private LinearLayout interBox;
    private EditText input;
    private TextView btnSend;
    private LinearLayout qCard;
    private ImageView qImg;

    private boolean busy = false;
    private boolean landedAtBottom = true;

    // 当前会话（用于学习记录）
    private String sessionId = "";

    // 单画布：一题一块板
    private AiBoardView board;

    // TTS
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private List<String> speakQueue = new ArrayList<String>();
    private int speakIndex = 0;
    private AiBoardView speakingBoard;

    // 流式节流
    private long lastPush = 0;

    // ===================== 生命周期 =====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cfg.load(this);
        ensureStoragePermission();
        loadWeakness();

        scroll = (ScrollView) findViewById(R.id.scroll);
        chatBox = (LinearLayout) findViewById(R.id.chatBox);
        interBox = (LinearLayout) findViewById(R.id.interBox);
        input = (EditText) findViewById(R.id.input);
        btnSend = (TextView) findViewById(R.id.btnSend);
        qCard = (LinearLayout) findViewById(R.id.qCard);
        qImg = (ImageView) findViewById(R.id.qImg);

        showEmptyHint();

        findViewById(R.id.btnHistory).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (busy) {
                    return;
                }
                saveSession();
                startActivityForResult(new Intent(MainActivity.this, HistoryActivity.class),
                        REQ_SESSION);
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doSend(input.getText().toString(), null, null);
            }
        });

        findViewById(R.id.btnPick).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFromGallery();
            }
        });

        findViewById(R.id.btnSettings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettings();
            }
        });

        findViewById(R.id.btnNew).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (busy) {
                    return;
                }
                if (messages.size() > 0) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage("开始新的一题？当前对话会清空。")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("开始", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    resetChat();
                                }
                            }).show();
                } else {
                    resetChat();
                }
            }
        });

        findViewById(R.id.btnRemoveQ).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                qCard.setVisibility(View.GONE);
                qImg.setImageDrawable(null);
            }
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSendState();
            }
        });

        scroll.getViewTreeObserver().addOnScrollChangedListener(
                new ViewTreeObserver.OnScrollChangedListener() {
                    @Override
                    public void onScrollChanged() {
                        int max = Math.max(1, chatBox.getHeight() - scroll.getHeight());
                        landedAtBottom = (max - scroll.getScrollY()) < dp(90);
                    }
                });

        updateSendState();
        initTts();

        if (!cfg.ready()) {
            ui.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showSettings();
                }
            }, 350);
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Throwable ignore) {
            }
        }
        super.onDestroy();
    }

    private void resetChat() {
        messages.clear();
        chatBox.removeAllViews();
        interBox.removeAllViews();
        qCard.setVisibility(View.GONE);
        qImg.setImageDrawable(null);
        stopSpeak();
        sessionId = "";
        board = null;
        showEmptyHint();
        landedAtBottom = true;
    }

    // ===================== 空状态 =====================

    private void showEmptyHint() {
        chatBox.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView face = new ImageView(this);
        face.setImageResource(R.drawable.ic_doubao);
        int s = dp(76);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(s, s);
        fp.gravity = Gravity.CENTER_HORIZONTAL;
        fp.topMargin = dp(50);
        face.setLayoutParams(fp);
        box.addView(face);

        TextView t1 = new TextView(this);
        t1.setText("拍一道题，我陪你一步步想明白");
        t1.setTextSize(16);
        t1.setTypeface(t1.getTypeface(), 1);
        t1.setTextColor(Color.parseColor("#1A1C1F"));
        t1.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams t1p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        t1p.gravity = Gravity.CENTER_HORIZONTAL;
        t1p.topMargin = dp(16);
        t1.setLayoutParams(t1p);
        box.addView(t1);

        TextView t2 = new TextView(this);
        t2.setText("板书讲解 · 公式排版 · 分步推导");
        t2.setTextSize(13);
        t2.setTextColor(Color.parseColor("#9AA0A6"));
        t2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams t2p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        t2p.gravity = Gravity.CENTER_HORIZONTAL;
        t2p.topMargin = dp(6);
        t2.setLayoutParams(t2p);
        box.addView(t2);

        chatBox.addView(box);
    }

    private boolean isOnlyEmptyHint() {
        if (chatBox.getChildCount() != 1) {
            return false;
        }
        View v = chatBox.getChildAt(0);
        if (!(v instanceof LinearLayout)) {
            return false;
        }
        LinearLayout box = (LinearLayout) v;
        if (box.getChildCount() < 2) {
            return false;
        }
        View second = box.getChildAt(1);
        if (!(second instanceof TextView)) {
            return false;
        }
        return ((TextView) second).getText().toString().contains("拍一道题");
    }

    private void clearHintIfNeeded() {
        if (isOnlyEmptyHint()) {
            chatBox.removeAllViews();
        }
    }

    // ===================== 气泡 =====================

    /** 用户消息气泡 */
    private void addUserBubble(Msg m) {
        clearHintIfNeeded();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = dp(14);
        row.setLayoutParams(rlp);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(R.drawable.bg_bubble_user);
        bubble.setPadding(dp(13), dp(10), dp(13), dp(10));
        bubble.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (m.bitmaps != null) {
            for (int i = 0; i < m.bitmaps.size(); i++) {
                Bitmap bm = m.bitmaps.get(i);
                if (bm == null) {
                    continue;
                }
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int maxW = (int) (getScreenWidth() * 0.60);
                int w = Math.min(bm.getWidth(), maxW);
                int h = (int) (bm.getHeight() * ((float) w / bm.getWidth()));
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(w, h);
                if (i < m.bitmaps.size() - 1 || m.text.length() > 0) {
                    ip.bottomMargin = dp(8);
                }
                iv.setLayoutParams(ip);
                iv.setImageBitmap(bm);
                bubble.addView(iv);
            }
        }

        TextView tv = new TextView(this);
        tv.setTextSize(16);
        tv.setLineSpacing(dp(4), 1f);
        tv.setTextColor(Color.parseColor("#1A1C1F"));
        tv.setText(Md.render(m.text, false));
        tv.setMaxWidth((int) (getScreenWidth() * 0.78));
        bubble.addView(tv);

        row.addView(bubble);
        chatBox.addView(row);
        if (landedAtBottom) {
            scrollToBottom();
        }
    }

    /** 保证只存在一块板（一题一页） */
    private void ensureBoard() {
        if (board != null && board.getParent() == chatBox) {
            return;
        }
        clearHintIfNeeded();
        board = new AiBoardView(this);
        int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.68);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h);
        board.setLayoutParams(lp);
        board.setListener(new AiBoardView.Listener() {
            @Override
            public void onQuizFeedback(String label) {
                onEval(label);
            }

            @Override
            public void onAskSubmit(String text) {
                if (busy || text == null || text.trim().length() == 0) {
                    return;
                }
                doSend(text.trim(), null, null);
            }

            @Override
            public void onSpeak(String payload) {
                if (payload == null) {
                    return;
                }
                int bar = payload.lastIndexOf('|');
                String text = payload;
                boolean slow = false;
                if (bar > 0) {
                    text = payload.substring(0, bar);
                    slow = "1".equals(payload.substring(bar + 1));
                }
                if (text.trim().length() == 0) {
                    return;
                }
                speakOne(text.trim(), slow);
            }

            @Override
            public void onSaveWrong(String point) {
                saveWrong(point);
            }
        });
        chatBox.addView(board);
    }

    /** 单词卡/句子卡的单句朗读：不走板书队列，直接念一句 */
    private void speakOne(String text, boolean slow) {
        if (tts == null || !ttsReady) {
            return;
        }
        try {
            tts.stop();
            float base = (cfg != null && cfg.ttsRate > 0) ? cfg.ttsRate : 1.0f;
            tts.setSpeechRate(slow ? base * 0.75f : base);
            HashMap<String, String> params = new HashMap<String, String>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "pick");
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        } catch (Throwable ignore) {
        }
    }

    /** 把当前整段对话（题目 + 讲解原文）存进错题本 */
    private void saveWrong(String point) {
        try {
            String NL = System.lineSeparator();
            long now = System.currentTimeMillis();
            File dir = new File(getFilesDir(), "errors");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            StringBuilder sb = new StringBuilder();
            String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                    java.util.Locale.CHINA).format(new java.util.Date(now));
            sb.append("# 错题 ").append(ts).append(NL).append(NL);
            if (point != null && point.trim().length() > 0) {
                sb.append("知识点：").append(point.trim()).append(NL).append(NL);
            }
            for (int i = 0; i < messages.size(); i++) {
                Msg m = messages.get(i);
                if (m.text == null || m.text.trim().length() == 0) {
                    continue;
                }
                sb.append(m.isUser() ? "【题目/我的作答】" : "【讲解】").append(NL);
                sb.append(m.text.trim()).append(NL).append(NL);
            }
            File f = new File(dir, "err_" + now + ".md");
            java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
            fo.write(sb.toString().getBytes("UTF-8"));
            fo.close();

            // 追加流水，供 Operit 侧同步成错题权重
            String kp = point == null ? "" : point.trim();
            String subject = "";
            String pt = kp;
            int cut = kp.indexOf("::");
            if (cut > 0) {
                subject = kp.substring(0, cut);
                pt = kp.substring(cut + 2);
            }
            StringBuilder line = new StringBuilder();
            line.append("{\"ts\":").append(now);
            line.append(",\"subject\":").append(MiniJson.quote(subject));
            line.append(",\"point\":").append(MiniJson.quote(pt));
            line.append(",\"file\":").append(MiniJson.quote(f.getName())).append("}").append(NL);
            File idx = new File(dir, "index.jsonl");
            java.io.FileOutputStream fo2 = new java.io.FileOutputStream(idx, true);
            fo2.write(line.toString().getBytes("UTF-8"));
            fo2.close();

            Toast.makeText(this, kp.length() > 0 ? "已存错题：" + kp : "已存进错题本",
                    Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            Toast.makeText(this, "存错题失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 学习档案：优先直读共享目录的 profile.json，失败再退回 files/weakness.json */
    private void loadWeakness() {
        String text = null;

        // 1) 直读 Operit 的学习档案（需要读存储权限）
        try {
            File f = new File("/sdcard/Download/Operit/study_assistant/profile.json");
            if (f.exists() && f.canRead()) {
                text = readAll(f);
            }
        } catch (Throwable ignore) {
        }

        // 2) 退路：Operit 侧推过来的摘要
        if (text == null || text.trim().length() == 0) {
            try {
                File f = new File(getFilesDir(), "weakness.json");
                if (f.exists()) {
                    text = readAll(f);
                }
            } catch (Throwable ignore) {
            }
        }
        if (text == null || text.trim().length() == 0) {
            return;
        }
        try {
            cfg.weakness = summarizeProfile(text);
        } catch (Throwable ignore) {
        }
    }

    private String readAll(File f) throws Exception {
        java.io.FileInputStream fi = new java.io.FileInputStream(f);
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = fi.read(buf)) > 0) {
            bo.write(buf, 0, n);
        }
        fi.close();
        return new String(bo.toByteArray(), "UTF-8");
    }

    /** 从 profile.json 结构里取 (subject, point, weight, updated)，按 7 天半衰期算有效权重，取前 5 */
    @SuppressWarnings("unchecked")
    private String summarizeProfile(String json) throws Exception {
        Object root = MiniJson.parse(json);
        if (!(root instanceof Map)) {
            return "";
        }
        Map<String, Object> top = (Map<String, Object>) root;
        double half = 7.0;
        Object hv = top.get("halfLifeDays");
        if (hv instanceof Number) {
            half = ((Number) hv).doubleValue();
        }
        if (half <= 0) {
            half = 7.0;
        }
        Object pts = top.get("points");
        if (!(pts instanceof Map)) {
            return "";
        }
        long now = System.currentTimeMillis();
        List<double[]> w = new ArrayList<double[]>();
        List<String> names = new ArrayList<String>();

        for (Map.Entry<String, Object> e : ((Map<String, Object>) pts).entrySet()) {
            if (!(e.getValue() instanceof Map)) {
                continue;
            }
            Map<String, Object> v = (Map<String, Object>) e.getValue();
            double weight = num(v.get("weight"), 0);
            long up = (long) num(v.get("updated"), now);
            double days = Math.max(0d, (now - up) / 86400000d);
            double eff = weight * Math.pow(0.5d, days / half);
            if (eff <= 0.05d) {
                continue;
            }
            String subject = str(v.get("subject"), "");
            String point = str(v.get("point"), e.getKey());
            names.add(subject + " \u00b7 " + point);
            w.add(new double[]{eff, names.size() - 1});
        }
        if (w.isEmpty()) {
            return "";
        }
        java.util.Collections.sort(w, new java.util.Comparator<double[]>() {
            @Override
            public int compare(double[] a, double[] b) {
                return Double.compare(b[0], a[0]);
            }
        });
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w.size() && i < 5; i++) {
            sb.append("\u00b7 ").append(names.get((int) w.get(i)[1])).append("\n");
        }
        return sb.toString();
    }

    private static double num(Object o, double dft) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        if (o instanceof String) {
            try {
                return Double.parseDouble((String) o);
            } catch (Throwable ignore) {
            }
        }
        return dft;
    }

    private static String str(Object o, String dft) {
        return o instanceof String ? (String) o : dft;
    }

    /** Android 23+ 运行时申请读存储权限（编译用的是 API19 jar，只能用反射） */
    private void ensureStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT < 23) {
            return;
        }
        try {
            java.lang.reflect.Method check =
                    getClass().getMethod("checkSelfPermission", String.class);
            int r = (Integer) check.invoke(this, "android.permission.READ_EXTERNAL_STORAGE");
            if (r != 0) {
                java.lang.reflect.Method req = getClass()
                        .getMethod("requestPermissions", String[].class, int.class);
                req.invoke(this, new String[]{"android.permission.READ_EXTERNAL_STORAGE"}, 1001);
            }
        } catch (Throwable ignore) {
        }
    }

    /** 小测后的自评 → 决定下一步怎么讲 */
    private void onEval(String label) {
        if (busy || label == null) {
            return;
        }
        String say;
        if (label.indexOf("完全不会") >= 0) {
            say = "我完全不会，先别往下讲，退回去把这一步需要的前置知识补一下，再回来这一步。";
        } else if (label.indexOf("有点懵") >= 0) {
            say = "这一步我有点懵，换个讲法再讲一遍——别重复刚才那段话，换个角度或者举一个更具体的例子。";
        } else {
            say = "这一步我懂了，继续往下讲下一步。";
        }
        doSend(say, null, null);
    }

    /** 老师消息：整页讲义式板书（无头像、无气泡） */
    private AiBoardView addBoardRow() {
        clearHintIfNeeded();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = dp(18);
        row.setLayoutParams(rlp);

        AiBoardView bv = new AiBoardView(this);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(140));
        bv.setLayoutParams(bp);
        row.addView(bv);

        chatBox.addView(row);
        if (landedAtBottom) {
            scrollToBottom();
        }
        return bv;
    }

    private void scrollToBottom() {
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    // ===================== 发送 =====================

    private void updateSendState() {
        boolean ok = input.getText().toString().trim().length() > 0 && !busy;
        btnSend.setAlpha(ok ? 1f : 0.35f);
        btnSend.setEnabled(ok);
    }

    private void doSend(String text, List<Bitmap> bmps, List<String> urls) {
        if (busy) {
            return;
        }
        String t = text == null ? "" : text.trim();
        boolean hasImg = urls != null && urls.size() > 0;
        if (t.length() == 0 && !hasImg) {
            return;
        }
        if (!cfg.ready()) {
            Toast.makeText(this, "先填一下接口地址、Key 和模型", Toast.LENGTH_SHORT).show();
            showSettings();
            return;
        }

        hideKeyboard();
        stopSpeak();
        input.setText("");
        interBox.removeAllViews();

        Msg user = new Msg(Msg.ROLE_USER, t);
        if (bmps != null) {
            user.bitmaps.addAll(bmps);
        }
        if (urls != null) {
            user.dataUrls.addAll(urls);
        }
        messages.add(user);

        ensureBoard();
        board.addUserLine(user.text, user.dataUrls);

        busy = true;
        updateSendState();

        board.beginTurn();
        board.typing();
        lastPush = 0;

        ApiClient.stream(cfg, messages, new ApiClient.StreamCb() {
            @Override
            public void onDelta(final String full) {
                long now = System.currentTimeMillis();
                if (now - lastPush < 260) {
                    return;
                }
                lastPush = now;
                final String json = BoardParser.toJson(BoardParser.parse(full));
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        board.setTurnBlocksJson(json);
                        if (landedAtBottom) {
                            scrollToBottom();
                        }
                    }
                });
            }

            @Override
            public void onDone(final String full) {
                final List<BoardParser.Block> blocks = BoardParser.parse(full);
                final String json = BoardParser.toJson(blocks);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        board.setTurnBlocksJson(json);
                        messages.add(new Msg(Msg.ROLE_AI, full));
                        saveSession();
                        busy = false;
                        updateSendState();
                        refreshChips();
                        scrollToBottom();
                        speak(blocks, board);
                    }
                });
            }

            @Override
            public void onError(final String message) {
                final List<BoardParser.Block> blocks = new ArrayList<BoardParser.Block>();
                BoardParser.Block b = new BoardParser.Block();
                b.title = "出错了";
                b.body.append(message).append("\n请点右上角 ⚙ 检查接口地址、Key 和模型名。");
                blocks.add(b);
                final String json = BoardParser.toJson(blocks);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        board.setTurnBlocksJson(json);
                        busy = false;
                        updateSendState();
                        refreshChips();
                    }
                });
            }
        });
    }


    // ===================== 学习记录 =====================

    private String firstUserText() {
        for (int i = 0; i < messages.size(); i++) {
            Msg m = messages.get(i);
            if (m.isUser() && m.text != null && m.text.trim().length() > 0) {
                return m.text.trim();
            }
        }
        return "";
    }

    private void saveSession() {
        if (messages.isEmpty()) {
            return;
        }
        if (sessionId.length() == 0) {
            sessionId = SessionStore.newId();
        }
        String t = firstUserText();
        if (t.length() > 24) {
            t = t.substring(0, 24) + "…";
        }
        SessionStore.save(this, sessionId, t, System.currentTimeMillis(), messages);
    }

    private void restoreSession(String id) {
        if (id == null || id.length() == 0) {
            return;
        }
        List<Msg> ms = SessionStore.load(this, id);
        if (ms.isEmpty()) {
            return;
        }
        stopSpeak();
        busy = false;
        messages.clear();
        chatBox.removeAllViews();
        interBox.removeAllViews();
        sessionId = id;

        ensureBoard();
        board.reset();
        for (int i = 0; i < ms.size(); i++) {
            Msg m = ms.get(i);
            messages.add(m);
            if (m.isUser()) {
                board.addUserLine(m.text, m.dataUrls);
            } else {
                board.beginTurn();
                board.setTurnBlocksJson(BoardParser.toJson(BoardParser.parse(m.text)));
            }
        }
        refreshChips();
        scrollToBottom();
    }

    // ===================== 快捷短语 =====================

    private void refreshChips() {
        interBox.removeAllViews();
        final String[] list;
        if (Cfg.MODE_TEACH.equals(cfg.mode)) {
            list = new String[]{"继续", "这一步没懂", "再讲一遍"};
        } else if (Cfg.MODE_ANSWER.equals(cfg.mode)) {
            list = new String[]{"换个说法解释", "再来一道类似的"};
        } else {
            list = new String[]{"给我点提示", "我懂了，下一步", "直接告诉我答案"};
        }
        for (int i = 0; i < list.length; i++) {
            TextView chip = new TextView(this);
            chip.setText(list[i]);
            chip.setTextSize(14);
            chip.setTextColor(Color.parseColor("#2F6BFF"));
            chip.setBackgroundResource(R.drawable.bg_chip);
            chip.setPadding(dp(14), dp(7), dp(14), dp(7));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.rightMargin = dp(8);
            chip.setLayoutParams(p);
            final String label = list[i];
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    doSend(label, null, null);
                }
            });
            interBox.addView(chip);
        }
    }

    // ===================== 语音 =====================

    private void initTts() {
        try {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS) {
                        int r = tts.setLanguage(Locale.CHINA);
                        ttsReady = (r != TextToSpeech.LANG_MISSING_DATA
                                && r != TextToSpeech.LANG_NOT_SUPPORTED);
                        tts.setSpeechRate(1.0f);
                        if (!ttsReady) {
                            ttsReady = false;
                        }
                    }
                }
            });
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    final int idx = parseIdx(utteranceId);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            if (speakingBoard != null && idx >= 0) {
                                speakingBoard.highlight(idx);
                            }
                        }
                    });
                }

                @Override
                public void onDone(String utteranceId) {
                }

                @Override
                public void onError(String utteranceId) {
                }
            });
        } catch (Throwable e) {
            tts = null;
            ttsReady = false;
        }
    }

    private int parseIdx(String id) {
        try {
            if (id != null && id.startsWith("u")) {
                return Integer.parseInt(id.substring(1));
            }
        } catch (Throwable ignore) {
        }
        return -1;
    }

    private boolean hasTalk(List<BoardParser.Block> blocks) {
        for (int i = 0; i < blocks.size(); i++) {
            BoardParser.Block b = blocks.get(i);
            if (b != null && "talk".equals(b.type)) {
                return true;
            }
        }
        return false;
    }

    private void speak(List<BoardParser.Block> blocks, AiBoardView board) {
        if (!cfg.tts) {
            return;
        }
        if (tts == null || !ttsReady) {
            return;
        }
        if (cfg.ttsDigest && !hasTalk(blocks)) {
            return;
        }
        List<String> parts = BoardParser.speakParts(blocks);
        if (parts.isEmpty()) {
            return;
        }
        stopSpeak();
        try {
            tts.setSpeechRate(cfg.ttsRate <= 0 ? 1.0f : cfg.ttsRate);
        } catch (Throwable ignore) {
        }
        speakingBoard = board;
        speakQueue = parts;
        speakIndex = 0;
        for (int i = 0; i < parts.size(); i++) {
            String s = parts.get(i);
            if (s.trim().length() == 0) {
                continue;
            }
            HashMap<String, String> params = new HashMap<String, String>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "u" + i);
            tts.speak(s, TextToSpeech.QUEUE_ADD, params);
        }
    }

    private void stopSpeak() {
        if (tts != null) {
            try {
                tts.stop();
            } catch (Throwable ignore) {
            }
        }
        speakQueue = new ArrayList<String>();
        speakIndex = 0;
    }

    // ===================== 选图 =====================

    private void pickFromGallery() {
        Intent it = new Intent("android.intent.action.OPEN_DOCUMENT");
        it.setType("image/*");
        it.putExtra("android.intent.extra.ALLOW_MULTIPLE", true);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(it, "选择题目图片（可多选，最多 6 张）"), REQ_PICK);
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的相册应用", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ_SESSION) {
            if (result == RESULT_OK && data != null) {
                restoreSession(data.getStringExtra("id"));
            }
            return;
        }
        if (result != RESULT_OK || req != REQ_PICK || data == null) {
            return;
        }

        List<Uri> uris = new ArrayList<Uri>();
        if (data.getClipData() != null) {
            ClipData cd = data.getClipData();
            int n = Math.min(cd.getItemCount(), 6);
            for (int i = 0; i < n; i++) {
                Uri u = cd.getItemAt(i).getUri();
                if (u != null) {
                    uris.add(u);
                }
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) {
            return;
        }

        try {
            List<Bitmap> bmps = new ArrayList<Bitmap>();
            List<String> urls = new ArrayList<String>();
            for (int i = 0; i < uris.size(); i++) {
                Bitmap bm = readScaled(uris.get(i), 1600);
                if (bm == null) {
                    continue;
                }
                bmps.add(bm);
                urls.add(toDataUrl(bm, 85));
            }
            if (bmps.isEmpty()) {
                Toast.makeText(this, "图片读不出来", Toast.LENGTH_SHORT).show();
                return;
            }
            qImg.setImageBitmap(bmps.get(0));
            qCard.setVisibility(View.VISIBLE);

            String text = bmps.size() > 1
                    ? ("这几道题我不会，帮我看看（" + bmps.size() + " 张）。")
                    : "这道题我不会，帮我看看。";
            doSend(text, bmps, urls);
        } catch (Throwable e) {
            Toast.makeText(this, "处理图片出错：" + e, Toast.LENGTH_LONG).show();
        }
    }

    private Bitmap readScaled(Uri uri, int maxSide) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = getContentResolver().openInputStream(uri);
        BitmapFactory.decodeStream(in, null, o);
        if (in != null) {
            in.close();
        }
        int scale = 1;
        int big = Math.max(o.outWidth, o.outHeight);
        while (big / scale > maxSide * 2) {
            scale *= 2;
        }
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = scale;
        in = getContentResolver().openInputStream(uri);
        Bitmap raw = BitmapFactory.decodeStream(in, null, o2);
        if (in != null) {
            in.close();
        }
        if (raw == null) {
            return null;
        }
        int w = raw.getWidth();
        int h = raw.getHeight();
        float r = Math.min(1f, (float) maxSide / Math.max(w, h));
        if (r < 1f) {
            Bitmap scaled = Bitmap.createScaledBitmap(raw, Math.round(w * r), Math.round(h * r), true);
            if (scaled != raw) {
                raw.recycle();
            }
            return scaled;
        }
        return raw;
    }

    private String toDataUrl(Bitmap bm, int quality) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, quality, bos);
        String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        bos.close();
        return "data:image/jpeg;base64," + b64;
    }

    // ===================== 设置 =====================

    @Override
    protected void onResume() {
        super.onResume();
        if (cfg != null) {
            cfg.load(this);
            loadWeakness();
        }
    }

    private void showSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    // ===================== 杂项 =====================

    private void hideKeyboard() {
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (im != null && getCurrentFocus() != null) {
            im.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private int getScreenWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }
}