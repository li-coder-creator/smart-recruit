package com.recruit.smartrecruit.utils;

import jakarta.validation.Valid;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class Md5Util {

    // MD5加密
    public static String encrypt( String password) {
        try {
            // 获取 MD5 算法对象
            MessageDigest md = MessageDigest.getInstance("MD5");

            // 执行加密
            byte[] bytes = md.digest(password.getBytes(StandardCharsets.UTF_8));

            // 转成十六进制字符串
            StringBuilder sb = new StringBuilder();

            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    sb.append("0");
                }
                sb.append(hex);
            }

            return sb.toString();

        } catch (Exception e) {
            throw new RuntimeException("MD5加密失败", e);
        }
    }
}