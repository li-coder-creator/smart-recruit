package com.recruit.smartrecruit.constant;

public final class CompanyFileConstants {
    public static final int MAX_PHOTO_COUNT = 6;
    public static final long MAX_PHOTO_BYTES = 3L * 1024 * 1024;
    public static final long MAX_QUALIFICATION_BYTES = 10L * 1024 * 1024;
    /** 避免小体积压缩图片在解码时耗尽内存；不进行自动缩图。 */
    public static final long MAX_IMAGE_PIXELS = 25_000_000L;
    public static final int MAX_ORIGINAL_FILENAME_LENGTH = 255;
    public static final String PHOTO_DIRECTORY = "photos";
    public static final String QUALIFICATION_DIRECTORY = "qualifications";
    public static final String TRASH_DIRECTORY = ".trash";
    public static final String PNG_TYPE = "image/png";
    public static final String JPEG_TYPE = "image/jpeg";
    public static final String PDF_TYPE = "application/pdf";

    private CompanyFileConstants() {
    }
}
