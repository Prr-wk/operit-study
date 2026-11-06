package com.operit.doubaoteacher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** 学习记录列表 */
public class HistoryActivity extends Activity {

    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_history);
        list = (LinearLayout) findViewById(R.id.hList);

        findViewById(R.id.hBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        render();
    }

    private void render() {
        list.removeAllViews();
        List<SessionStore.Meta> metas = SessionStore.list(this);

        if (metas.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("还没有学习记录\n\n去问一道题，回来就能看到");
            t.setTextSize(14);
            t.setTextColor(Color.parseColor("#9AA0A6"));
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(80), 0, 0);
            list.addView(t);
            return;
        }

        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm");
        for (int i = 0; i < metas.size(); i++) {
            final SessionStore.Meta m = metas.get(i);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setBackgroundResource(R.drawable.bg_card);
            card.setPadding(dp(14), dp(12), dp(10), dp(12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            card.setLayoutParams(lp);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView title = new TextView(this);
            String t = m.title == null ? "" : m.title.trim();
            if (t.length() == 0) {
                t = "（无标题）";
            }
            if (t.length() > 40) {
                t = t.substring(0, 40) + "…";
            }
            title.setText(t);
            title.setTextSize(15);
            title.setTextColor(Color.parseColor("#1A1C1F"));
            title.setMaxLines(2);
            col.addView(title);

            TextView sub = new TextView(this);
            sub.setText(fmt.format(new Date(m.ts)) + "  ·  " + m.count + " 条");
            sub.setTextSize(12);
            sub.setTextColor(Color.parseColor("#9AA0A6"));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sp.topMargin = dp(5);
            sub.setLayoutParams(sp);
            col.addView(sub);

            card.addView(col);

            TextView del = new TextView(this);
            del.setText("删除");
            del.setTextSize(13);
            del.setTextColor(Color.parseColor("#E5484D"));
            del.setPadding(dp(12), dp(8), dp(6), dp(8));
            card.addView(del);

            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(HistoryActivity.this)
                            .setMessage("删除这条学习记录？")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    SessionStore.delete(HistoryActivity.this, m.id);
                                    render();
                                }
                            }).show();
                }
            });

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent it = new Intent();
                    it.putExtra("id", m.id);
                    setResult(RESULT_OK, it);
                    finish();
                }
            });

            list.addView(card);
        }
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}