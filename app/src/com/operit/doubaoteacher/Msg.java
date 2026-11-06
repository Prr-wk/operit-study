package com.operit.doubaoteacher;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

/** 一条对话消息 */
public class Msg {
    public static final int ROLE_USER = 1;
    public static final int ROLE_AI = 2;

    public int role;
    public String text;
    /** 用于界面显示的位图 */
    public List<Bitmap> bitmaps = new ArrayList<Bitmap>();
    /** 用于发送给接口的 dataURL */
    public List<String> dataUrls = new ArrayList<String>();

    public Msg(int role, String text) {
        this.role = role;
        this.text = text == null ? "" : text;
    }

    public boolean isUser() {
        return role == ROLE_USER;
    }
}