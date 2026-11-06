package com.operit.doubaoteacher;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * 一整页讲义（单画布）。
 * <p>
 * 一道题从头到尾只有这一块板：用户说的话变成板上一条灰条，
 * 老师的板书按回合往下追加。没有头像、没有气泡。
 * 高度由网页回报（AndroidBridge.onHeight），滚动交给外层 ScrollView。
 */
public class AiBoardView extends FrameLayout {

    private WebView wv;
    private boolean pageReady = false;
    private final List<String> pendingJs = new ArrayList<String>();

    /** 网页里发生的交互（自评、开放题提交）回调给 Activity */
    public interface Listener {
        void onQuizFeedback(String label);

        void onAskSubmit(String text);

        /** 单词卡/句子卡点读音：payload = 文本|slow(1/0) */
        void onSpeak(String payload);

        /** 错题卡点“存错题” */
        void onSaveWrong(String point);
    }

    private Listener listener;

    public void setListener(Listener l) {
        this.listener = l;
    }

    public AiBoardView(Context c) {
        super(c);
        init(c);
    }

    private void init(Context c) {
        setBackgroundColor(Color.TRANSPARENT);
        setPadding(0, 0, 0, 0);

        wv = new WebView(c);
        wv.setBackgroundColor(Color.TRANSPARENT);
        wv.setVerticalScrollBarEnabled(false);
        wv.setHorizontalScrollBarEnabled(false);
        wv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        if (android.os.Build.VERSION.SDK_INT >= 16) {
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);
        }
        s.setTextZoom(100);

        try {
            wv.addJavascriptInterface(new Bridge(), "AndroidBridge");
        } catch (Throwable ignore) {
        }

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                for (int i = 0; i < pendingJs.size(); i++) {
                    call(pendingJs.get(i));
                }
                pendingJs.clear();
            }
        });

        addView(wv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        wv.loadUrl("file:///android_asset/board.html");
    }

    private class Bridge {
        @JavascriptInterface
        public void onHeight(int h) {
            // 画布高度已固定（占屏幕大部分），不再跟随内容伸缩
        }

        @JavascriptInterface
        public void onQuizFeedback(final String label) {
            if (listener == null) {
                return;
            }
            post(new Runnable() {
                @Override
                public void run() {
                    listener.onQuizFeedback(label);
                }
            });
        }

        @JavascriptInterface
        public void onSpeak(final String payload) {
            if (listener == null) {
                return;
            }
            post(new Runnable() {
                @Override
                public void run() {
                    listener.onSpeak(payload);
                }
            });
        }

        @JavascriptInterface
        public void onSaveWrong(final String point) {
            if (listener == null) {
                return;
            }
            post(new Runnable() {
                @Override
                public void run() {
                    listener.onSaveWrong(point);
                }
            });
        }

        @JavascriptInterface
        public void onAskSubmit(final String text) {
            if (listener == null) {
                return;
            }
            post(new Runnable() {
                @Override
                public void run() {
                    listener.onAskSubmit(text);
                }
            });
        }
    }

    private void applyHeight(final int h) {
        post(new Runnable() {
            @Override
            public void run() {
                int target = Math.max(dp(48), h);
                ViewGroup.LayoutParams lp = getLayoutParams();
                if (lp != null && lp.height != target) {
                    lp.height = target;
                    setLayoutParams(lp);
                }
            }
        });
    }

    private void run(String js) {
        if (!pageReady) {
            if (pendingJs.size() < 40) {
                pendingJs.add(js);
            }
            return;
        }
        call(js);
    }

    private void call(String js) {
        try {
            Class<?> vc = Class.forName("android.webkit.ValueCallback");
            java.lang.reflect.Method m =
                    WebView.class.getMethod("evaluateJavascript", String.class, vc);
            m.invoke(wv, js, null);
            return;
        } catch (Throwable ignore) {
        }
        try {
            wv.loadUrl("javascript:" + js);
        } catch (Throwable ignore) {
        }
    }

    // ===================== 对外 =====================

    /** 用户插话：板上一条灰条（可带图） */
    public void addUserLine(String text, List<String> imgs) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("GB.addUserLine({text:").append(MiniJson.quote(text == null ? "" : text));
        sb.append(",imgs:[");
        if (imgs != null) {
            for (int i = 0; i < imgs.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(MiniJson.quote(imgs.get(i)));
            }
        }
        sb.append("]})");
        run(sb.toString());
    }

    /** 开一轮新的 AI 回复 */
    public void beginTurn() {
        run("GB.beginTurn()");
    }

    public void typing() {
        run("GB.setTyping()");
    }

    /** 更新当前回合的板书块 */
    public void setTurnBlocksJson(String json) {
        run("GB.setTurnBlocks(" + json + ")");
    }

    public void reset() {
        run("GB.reset()");
    }

    public void highlight(int idx) {
        run("GB.highlight(" + idx + ")");
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}