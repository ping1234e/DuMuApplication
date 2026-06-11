package com.cscec.dumu.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class TimestampFormatter {

    // 东八区（北京时间）
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

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
            return sdf.parse(timeStr).getTime();
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

    /**
     * 将日期字符串（yyyy-MM-dd）转换为当天的开始时间戳（毫秒）
     * 例如：2026-06-10 -> 2026-06-10 00:00:00
     */
    public static long parseDateToStartTimestamp(String dateStr) {
        try {
            Date date = DATE_FORMAT.parse(dateStr);
            return date.getTime();
        } catch (ParseException e) {
            return 0;
        }
    }

    /**
     * 将日期字符串（yyyy-MM-dd）转换为当天的结束时间戳（毫秒）
     * 例如：2026-06-10 -> 2026-06-10 23:59:59
     */
    public static long parseDateToEndTimestamp(String dateStr) {
        try {
            Date date = DATE_FORMAT.parse(dateStr);
            // 加 86399000 毫秒 = 23:59:59
            return date.getTime() + 86399000;
        } catch (ParseException e) {
            return Long.MAX_VALUE;
        }
    }
}
