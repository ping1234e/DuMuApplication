package com.cscec.dumu.util;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TimestampFormatter {
    /**
     * 东八区（北京时间）
     */
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String formatDateTimeRange(LocalDateTime start, LocalDateTime end) {
        return start.format(FORMATTER) + " - " + end.format(FORMATTER);
    }

    /**
     * 将 LocalDateTime 转换为时间戳（秒）
     */
    public static long parseLocalDateTimeToTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return 0;
        }
        return dateTime.atZone(ZONE_SHANGHAI).toEpochSecond();
    }

    /**
     * 解析时间字符串为时间戳
     */
    public static long parseTimeString(String timeStr) {
        SimpleDateFormat sdf;
        if (timeStr.contains(":")) {
            sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        } else {
            sdf = new SimpleDateFormat("yyyy-MM-dd");
        }
        try {
            return sdf.parse(timeStr).getTime() / 1000;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 秒级时间戳转字符串（东八区）
     */
    public static String formatSecond(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(timestamp),
                ZONE_SHANGHAI
        );
        return dateTime.format(FORMATTER);
    }

    /**
     * 毫秒级时间戳转字符串（东八区）
     */
    public static String formatMilli(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZONE_SHANGHAI
        );
        return dateTime.format(FORMATTER);
    }

    /**
     * 自动判断单位（10位为秒，13位为毫秒）
     */
    public static String formatAuto(long timestamp) {
        // 13位为毫秒级
        if (timestamp > 9999999999L) {
            return formatMilli(timestamp);
        } else {
            return formatSecond(timestamp);
        }
    }
}
