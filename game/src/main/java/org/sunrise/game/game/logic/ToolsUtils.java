package org.sunrise.game.game.logic;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;

public class ToolsUtils {

    // 常量默认时区
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();
    // 归一化格式器
    private static final DateTimeFormatter STANDARD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss");
    // 时间解析缓存
    private static final Map<String, Long> TIME_CACHE = new HashMap<>();

    private static long todayZeroTimeMillis;
    private static long weekZeroTimeMillis;

    public static final long DAY_MILLS = 24 * 60 * 60 * 1000L;
    public static final long HOUR_MILLS = 60 * 60 * 1000L;
    public static final long MINUTE_MILLS = 60 * 1000L;
    public static final long SEC_MILLS = 1000L;

    /**
     * 解析时间字符串为时间戳 (支持 yyyy/M/d.HH:mm:ss, yyyy-MM-dd HH:mm:ss 等)
     */
    public static long getTimeMillis(String s) {
        if (s == null || s.trim().isEmpty()) {
            return 0;
        }

        return TIME_CACHE.computeIfAbsent(s, key -> {
            // 归一化处理
            String normalized = key.trim()
                    .replace('/', '-')
                    .replace('.', ' ');

            try {
                LocalDateTime dt = LocalDateTime.parse(normalized, STANDARD_FORMATTER);
                return dt.atZone(DEFAULT_ZONE).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                return 0L;
            }
        });
    }

    /**
     * 获取今日零点时间戳
     */
    public static long getTodayZeroTimeMillis() {
        if (todayZeroTimeMillis == 0 || System.currentTimeMillis() - todayZeroTimeMillis >= DAY_MILLS) {
            LocalDate today = LocalDate.now();
            ZonedDateTime midnightZoned = today.atStartOfDay(DEFAULT_ZONE);
            todayZeroTimeMillis = midnightZoned.toInstant().toEpochMilli();
        }
        return todayZeroTimeMillis;
    }

    /**
     * 获取时间戳 通过偏移量 以今日0点为基础单位
     */
    public static long getDayTimeMillisByOffset(int day) {
        return getTodayZeroTimeMillis() + day * DAY_MILLS;
    }

    public static long getDayTimeMillisByOffset(int day, int hour) {
        return getTodayZeroTimeMillis() + day * DAY_MILLS + hour * HOUR_MILLS;
    }

    public static long getDayTimeMillisByOffset(int day, int hour, int minute) {
        return getTodayZeroTimeMillis() + day * DAY_MILLS + hour * HOUR_MILLS + minute * MINUTE_MILLS;
    }

    public static long getDayTimeMillisByOffset(int day, int hour, int minute, int sec) {
        return getTodayZeroTimeMillis() + day * DAY_MILLS + hour * HOUR_MILLS + minute * MINUTE_MILLS + sec * SEC_MILLS;
    }

    /**
     * 获取本周零点时间戳
     */
    public static long getWeekZeroTimeMillis() {
        if (weekZeroTimeMillis == 0 || System.currentTimeMillis() - weekZeroTimeMillis >= 7 * DAY_MILLS) {
            LocalDate today = LocalDate.now();
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            ZonedDateTime mondayMidnight = monday.atStartOfDay(DEFAULT_ZONE);
            weekZeroTimeMillis = mondayMidnight.toInstant().toEpochMilli();
        }
        return weekZeroTimeMillis;
    }

    /**
     * 获取时间戳 通过偏移量 以本周一0点为基础单位
     */
    public static long getWeekZeroTimeMillisByOffset(int day) {
        return getWeekZeroTimeMillis() + day * DAY_MILLS;
    }

    public static long getWeekZeroTimeMillisByOffset(int day, int hour) {
        return getWeekZeroTimeMillis() + day * DAY_MILLS + hour * HOUR_MILLS;
    }

    public static long getWeekZeroTimeMillisByOffset(int day, int hour, int minute) {
        return getWeekZeroTimeMillis() + day * DAY_MILLS + hour * HOUR_MILLS + minute * MINUTE_MILLS;
    }

    public static long getWeekZeroTimeMillisByOffset(int day, int hour, int minute, int sec) {
        return getWeekZeroTimeMillis() + day * DAY_MILLS + hour * HOUR_MILLS + minute * MINUTE_MILLS + sec * SEC_MILLS;
    }
}