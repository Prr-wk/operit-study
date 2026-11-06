package com.operit.doubaoteacher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.ViewFlipper;

/** 首启引导：欢迎 → 连模型 → 选讲法。只走一次。 */
public class OnboardingActivity extends Activity {

    private Cfg cfg;
    private ViewFlipper flipper;
    private TextView tvStep, tvOPing, btnOPing;
    private EditText etOBase, etOKey, etOModel;
    private RadioButton rbOGuide, rbOTeach, rbOAnswer;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = new Cfg();
        cfg.load(this);
        if (cfg.configured()) {
            enter();
            return;
        }
        setContentView(R.layout.activity_onboarding);

        flipper = (ViewFlipper) findViewById(R.id.flipper);
        tvStep = (TextView) findViewById(R.id.tvStep);
        etOBase = (EditText) findViewById(R.id.etOBase);
        etOKey = (EditText) findViewById(R.id.etOKey);
        etOModel = (EditText) findViewById(R.id.etOModel);
        tvOPing = (TextView) findViewById(R.id.tvOPing);
        btnOPing = (TextView) findViewById(R.id.btnOPing);
        rbOGuide = (RadioButton) findViewById(R.id.rbOGuide);
        rbOTeach = (RadioButton) findViewById(R.id.rbOTeach);
        rbOAnswer = (RadioButton) findViewById(R.id.rbOAnswer);

        etOBase.setText(cfg.base);
        etOKey.setText(cfg.key);
        etOModel.setText(cfg.model);
        rbOGuide.setChecked(true);

        findViewById(R.id.btnStart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                go(1);
            }
        });

        findViewById(R.id.btnNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 先把已填的存下来，最后一步统一落盘
                cfg.base = etOBase.getText().toString().trim();
                cfg.key = etOKey.getText().toString().trim();
                cfg.model = etOModel.getText().toString().trim();
                go(2);
            }
        });

        btnOPing.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ping();
            }
        });

        findViewById(R.id.btnDone).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishAll();
            }
        });
    }

    private void go(int index) {
        flipper.setDisplayedChild(index);
        tvStep.setText((index + 1) + " / 3");
    }

    private void ping() {
        final String url, key, model;
        cfg.base = etOBase.getText().toString().trim();
        cfg.key = etOKey.getText().toString().trim();
        cfg.model = etOModel.getText().toString().trim();
        url = cfg.chatUrl();
        key = cfg.key;
        model = cfg.model;
        btnOPing.setEnabled(false);
        tvOPing.setTextColor(0xFF6B7280);
        tvOPing.setText("正在测试…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String r = ApiClient.ping(url, key, model);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        btnOPing.setEnabled(true);
                        if ("ok".equals(r)) {
                            tvOPing.setTextColor(0xFF0C9B52);
                            tvOPing.setText("连接正常，点「下一步」继续");
                        } else {
                            tvOPing.setTextColor(0xFFD33A3A);
                            tvOPing.setText("失败：" + r);
                        }
                    }
                });
            }
        }).start();
    }

    private void finishAll() {
        cfg.base = etOBase.getText().toString().trim();
        cfg.key = etOKey.getText().toString().trim();
        cfg.model = etOModel.getText().toString().trim();
        if (cfg.model.length() == 0) {
            cfg.model = "deepseek-flash";
        }
        if (rbOTeach.isChecked()) {
            cfg.mode = Cfg.MODE_TEACH;
        } else if (rbOAnswer.isChecked()) {
            cfg.mode = Cfg.MODE_ANSWER;
        } else {
            cfg.mode = Cfg.MODE_GUIDE;
        }
        cfg.onboarded = true;
        cfg.save(this);

        if (cfg.key == null || cfg.key.trim().length() == 0) {
            new AlertDialog.Builder(this)
                    .setTitle("还没有填 Key")
                    .setMessage("现在也能进入，但发消息会失败。要不要先去设置里补上？")
                    .setPositiveButton("去设置", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            startActivity(new Intent(OnboardingActivity.this, SettingsActivity.class));
                            finish();
                        }
                    })
                    .setNegativeButton("先进入", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            enter();
                        }
                    })
                    .show();
        } else {
            enter();
        }
    }

    private void enter() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}