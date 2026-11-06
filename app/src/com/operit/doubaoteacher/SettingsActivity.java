package com.operit.doubaoteacher;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** 独立设置页：模型配置 / 教学 / 语音 / 关于 */
public class SettingsActivity extends Activity {

    private Cfg cfg;
    private EditText etBase, etKey, etModel, etPrompt;
    private TextView tvPing, tvRate, tvVersion, tvBackend, btnPing;
    private CheckBox cbTts, cbDigest;
    private SeekBar sbRate;
    private RadioButton rbGuide, rbTeach, rbAnswer;

    /** 保存前的模式，用来判断是否需要换提示词 */
    private String modeAtOpen;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = new Cfg();
        cfg.load(this);
        modeAtOpen = cfg.mode;

        setContentView(R.layout.activity_settings);

        etBase = (EditText) findViewById(R.id.etBase);
        etKey = (EditText) findViewById(R.id.etKey);
        etModel = (EditText) findViewById(R.id.etModel);
        etPrompt = (EditText) findViewById(R.id.etPrompt);
        tvPing = (TextView) findViewById(R.id.tvPing);
        tvRate = (TextView) findViewById(R.id.tvRate);
        tvVersion = (TextView) findViewById(R.id.tvVersion);
        tvBackend = (TextView) findViewById(R.id.tvBackend);
        btnPing = (TextView) findViewById(R.id.btnPing);
        cbTts = (CheckBox) findViewById(R.id.cbTts);
        cbDigest = (CheckBox) findViewById(R.id.cbDigest);
        sbRate = (SeekBar) findViewById(R.id.sbRate);
        rbGuide = (RadioButton) findViewById(R.id.rbGuide);
        rbTeach = (RadioButton) findViewById(R.id.rbTeach);
        rbAnswer = (RadioButton) findViewById(R.id.rbAnswer);

        fill();

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btnSave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        findViewById(R.id.btnResetPrompt).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cfg.resetCustom();
                etPrompt.setText(cfg.defaultTemplate());
                Toast.makeText(SettingsActivity.this, "已恢复默认提示词", Toast.LENGTH_SHORT).show();
            }
        });
        btnPing.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ping();
            }
        });

        sbRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                tvRate.setText("语速　" + String.format("%.1f", rateOf(p)) + "x");
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });

        // 切换模式 → 提示词框换成该模式的文本
        View.OnClickListener modeClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String m = v.getId() == R.id.rbTeach ? Cfg.MODE_TEACH
                        : (v.getId() == R.id.rbAnswer ? Cfg.MODE_ANSWER : Cfg.MODE_GUIDE);
                if (m.equals(cfg.mode)) {
                    return;
                }
                cfg.mode = m;
                etPrompt.setText(cfg.editablePrompt());
            }
        };
        rbGuide.setOnClickListener(modeClick);
        rbTeach.setOnClickListener(modeClick);
        rbAnswer.setOnClickListener(modeClick);
    }

    private float rateOf(int progress) {
        return 0.6f + progress / 100f;
    }

    private void fill() {
        etBase.setText(cfg.base);
        etKey.setText(cfg.key);
        etModel.setText(cfg.model);
        etPrompt.setText(cfg.editablePrompt());

        if (Cfg.MODE_TEACH.equals(cfg.mode)) {
            rbTeach.setChecked(true);
        } else if (Cfg.MODE_ANSWER.equals(cfg.mode)) {
            rbAnswer.setChecked(true);
        } else {
            rbGuide.setChecked(true);
        }

        cbTts.setChecked(cfg.tts);
        cbDigest.setChecked(cfg.ttsDigest);
        int p = Math.round((cfg.ttsRate - 0.6f) * 100f);
        if (p < 0) {
            p = 0;
        }
        if (p > 80) {
            p = 80;
        }
        sbRate.setProgress(p);
        tvRate.setText("语速　" + String.format("%.1f", cfg.ttsRate) + "x");

        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            tvVersion.setText("v" + pi.versionName + "　(build " + pi.versionCode + ")");
        } catch (Throwable ignore) {
        }
        String base = cfg.base == null ? "" : cfg.base.replace("https://", "").replace("http://", "");
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        tvBackend.setText(cfg.model + " · " + base);
    }

    private void save() {
        cfg.base = etBase.getText().toString().trim();
        cfg.key = etKey.getText().toString().trim();
        cfg.model = etModel.getText().toString().trim();
        if (cfg.model.length() == 0) {
            cfg.model = "deepseek-flash";
        }
        cfg.tts = cbTts.isChecked();
        cfg.ttsDigest = cbDigest.isChecked();
        cfg.ttsRate = rateOf(sbRate.getProgress());

        // 提示词：与出厂默认一致 → 视为未自定义，删掉覆盖文件
        String typed = etPrompt.getText().toString();
        String def = cfg.defaultTemplate();
        if (typed.trim().length() == 0 || normalize(typed).equals(normalize(def))) {
            cfg.resetCustom();
        } else {
            cfg.saveCustom(typed);
        }

        cfg.save(this);
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private String normalize(String s) {
        return s.replace("\r\n", "\n").replaceAll("\\s+", " ").trim();
    }

    private void ping() {
        cfg.base = etBase.getText().toString().trim();
        cfg.key = etKey.getText().toString().trim();
        cfg.model = etModel.getText().toString().trim();
        btnPing.setEnabled(false);
        tvPing.setTextColor(0xFF6B7280);
        tvPing.setText("正在测试…");

        final String url = cfg.chatUrl();
        final String key = cfg.key;
        final String model = cfg.model;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String r = ApiClient.ping(url, key, model);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        btnPing.setEnabled(true);
                        if ("ok".equals(r)) {
                            tvPing.setTextColor(0xFF0C9B52);
                            tvPing.setText("连接正常");
                        } else {
                            tvPing.setTextColor(0xFFD33A3A);
                            tvPing.setText("失败：" + r);
                        }
                    }
                });
            }
        }).start();
    }
}