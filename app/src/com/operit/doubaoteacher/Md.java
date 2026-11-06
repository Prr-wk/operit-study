package com.operit.doubaoteacher;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 轻量 Markdown → Spannable：只处理对话里真正会出现的几种标记 */
public class Md {

    private static final Pattern CODE_BLOCK =
            Pattern.compile("```[a-zA-Z0-9+-]*\\n([\\s\\S]*?)```");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*\\n]+)\\*\\*");
    private static final Pattern HEAD = Pattern.compile("(?m)^[ \\t]*#{1,4}[ \\t]+(.+)$");
    private static final Pattern BULLET = Pattern.compile("(?m)^[ \\t]*[-*][ \\t]+");
    private static final Pattern QUOTE = Pattern.compile("(?m)^[ \\t]*>[ \\t]?");

    public static CharSequence render(String src, boolean onBlueBubble) {
        if (src == null) {
            return "";
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(src.replace("\r\n", "\n"));

        markMonospace(sb, CODE_BLOCK, true);
        markBold(sb, HEAD);
        replaceAll(sb, BULLET, "• ");
        replaceAll(sb, QUOTE, "");
        markMonospace(sb, INLINE_CODE, !onBlueBubble);
        markBold(sb, BOLD);

        if (onBlueBubble) {
            BackgroundColorSpan[] bgs = sb.getSpans(0, sb.length(), BackgroundColorSpan.class);
            for (int i = 0; i < bgs.length; i++) {
                sb.removeSpan(bgs[i]);
            }
        }
        return sb;
    }

    /** 保留 group(1) 内容并套等宽样式 */
    private static void markMonospace(SpannableStringBuilder sb, Pattern p, boolean bg) {
        int guard = 0;
        while (guard++ < 200) {
            Matcher m = p.matcher(sb.toString());
            if (!m.find()) {
                return;
            }
            String inner = m.group(1);
            int start = m.start();
            sb.replace(start, m.end(), inner);
            int end = start + inner.length();
            sb.setSpan(new TypefaceSpan("monospace"), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(Color.parseColor("#1A1C1F")), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (bg) {
                sb.setSpan(new BackgroundColorSpan(Color.parseColor("#F0F1F4")), start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    /** 保留 group(1) 内容并加粗 */
    private static void markBold(SpannableStringBuilder sb, Pattern p) {
        int guard = 0;
        while (guard++ < 200) {
            Matcher m = p.matcher(sb.toString());
            if (!m.find()) {
                return;
            }
            String inner = m.group(1);
            int start = m.start();
            sb.replace(start, m.end(), inner);
            sb.setSpan(new StyleSpan(Typeface.BOLD), start, start + inner.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void replaceAll(SpannableStringBuilder sb, Pattern p, String with) {
        int guard = 0;
        while (guard++ < 400) {
            Matcher m = p.matcher(sb.toString());
            if (!m.find()) {
                return;
            }
            sb.replace(m.start(), m.end(), with);
        }
    }
}