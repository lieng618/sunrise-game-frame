package org.sunrise.game.game.logic;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.Date;

/**
 * 通用时间工具类
 *
 */
public final class ToolsUtils {
    private ToolsUtils() {
        throw new AssertionError("ToolsUtils禁止实例化");
    }

    // ==================== 全局常量 ====================
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();
    private static final WeekFields WEEK_FIELDS = WeekFields.of(DayOfWeek.MONDAY, 4);
    private static final DateTimeFormatter STANDARD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss");

    public static final long DAY_MILLIS = 24 * 60 * 60 * 1000L;
    public static final long HOUR_MILLIS = 60 * 60 * 1000L;
    public static final long MINUTE_MILLIS = 60 * 1000L;
    public static final long SECOND_MILLIS = 1000L;
    public static final long PULSE_100MS_MILLIS = 100L;
    public static final float PULSE_100MS_SEC = PULSE_100MS_MILLIS / 1000f;

    // 使用 Object 作为锁对象，确保多线程临界点安全
    private static final Object LOCK = new Object();

    private static volatile long todayZeroTimeMillis;
    private static volatile long weekZeroTimeMillis;

    /**
     * 解析时间字符串为毫秒级时间戳
     */
    public static long getTimeMillis(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return 0L;
        }
        String normalized = timeStr.trim().replace('/', '-').replace('.', ' ');
        try {
            LocalDateTime dateTime = LocalDateTime.parse(normalized, STANDARD_FORMATTER);
            return dateTime.atZone(DEFAULT_ZONE).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return 0L;
        }
    }

    /**
     * 获取今日零点的毫秒级时间戳（DCL双重检查锁定，确保全服零点刷新安全）
     */
    public static long getTodayZeroTimeMillis() {
        long now = System.currentTimeMillis();
        // 绝大多数情况下直接走 volatile 读，性能极高
        if (todayZeroTimeMillis <= now && now < todayZeroTimeMillis + DAY_MILLIS) {
            return todayZeroTimeMillis;
        }

        // 跨天临界点，加锁防止并发时序错乱
        synchronized (LOCK) {
            // 二次检查
            if (todayZeroTimeMillis <= now && now < todayZeroTimeMillis + DAY_MILLIS) {
                return todayZeroTimeMillis;
            }
            LocalDate today = LocalDate.now(DEFAULT_ZONE);
            todayZeroTimeMillis = today.atStartOfDay(DEFAULT_ZONE).toInstant().toEpochMilli();
            return todayZeroTimeMillis;
        }
    }

    public static long getDayTimeMillisByOffset(int day) {
        return getTodayZeroTimeMillis() + day * DAY_MILLIS;
    }

    public static long getDayTimeMillisByOffset(int day, int hour) {
        return getDayTimeMillisByOffset(day) + hour * HOUR_MILLIS;
    }

    public static long getDayTimeMillisByOffset(int day, int hour, int minute) {
        return getDayTimeMillisByOffset(day, hour) + minute * MINUTE_MILLIS;
    }

    public static long getDayTimeMillisByOffset(int day, int hour, int minute, int second) {
        return getDayTimeMillisByOffset(day, hour, minute) + second * SECOND_MILLIS;
    }

    /**
     * 获取本周一零点的毫秒级时间戳（DCL双重检查锁定）
     */
    public static long getWeekZeroTimeMillis() {
        long now = System.currentTimeMillis();
        if (weekZeroTimeMillis <= now && now < weekZeroTimeMillis + 7 * DAY_MILLIS) {
            return weekZeroTimeMillis;
        }

        synchronized (LOCK) {
            if (weekZeroTimeMillis <= now && now < weekZeroTimeMillis + 7 * DAY_MILLIS) {
                return weekZeroTimeMillis;
            }
            LocalDate today = LocalDate.now(DEFAULT_ZONE);
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            weekZeroTimeMillis = monday.atStartOfDay(DEFAULT_ZONE).toInstant().toEpochMilli();
            return weekZeroTimeMillis;
        }
    }

    public static long getWeekZeroTimeMillisByOffset(int day) {
        return getWeekZeroTimeMillis() + day * DAY_MILLIS;
    }

    public static long getWeekZeroTimeMillisByOffset(int day, int hour) {
        return getWeekZeroTimeMillisByOffset(day) + hour * HOUR_MILLIS;
    }

    public static long getWeekZeroTimeMillisByOffset(int day, int hour, int minute) {
        return getWeekZeroTimeMillisByOffset(day, hour) + minute * MINUTE_MILLIS;
    }

    public static long getWeekZeroTimeMillisByOffset(int day, int hour, int minute, int second) {
        return getWeekZeroTimeMillisByOffset(day, hour, minute) + second * SECOND_MILLIS;
    }

    public static int getChinaWeekDay() {
        return LocalDate.now(DEFAULT_ZONE).getDayOfWeek().getValue();
    }

    /**
     * 检查两个毫秒级时间戳是否在同一天
     */
    public static boolean isSameDay(long t1, long t2) {
        LocalDate date1 = Instant.ofEpochMilli(t1).atZone(DEFAULT_ZONE).toLocalDate();
        LocalDate date2 = Instant.ofEpochMilli(t2).atZone(DEFAULT_ZONE).toLocalDate();
        return date1.equals(date2);
    }

    public static boolean isSameDay(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        return isSameDay(date1.getTime(), date2.getTime());
    }

    /**
     * 检查两个毫秒级时间戳是否在同一周
     */
    public static boolean isSameWeek(long t1, long t2) {
        // 快速判断：如果相差超过 8 天，绝对不在同一周（留出夏令时冗余）
        if (Math.abs(t1 - t2) > 8 * DAY_MILLIS) {
            return false;
        }

        ZonedDateTime zdt1 = Instant.ofEpochMilli(t1).atZone(DEFAULT_ZONE);
        ZonedDateTime zdt2 = Instant.ofEpochMilli(t2).atZone(DEFAULT_ZONE);

        int week1 = zdt1.get(WEEK_FIELDS.weekOfWeekBasedYear());
        int week2 = zdt2.get(WEEK_FIELDS.weekOfWeekBasedYear());
        int year1 = zdt1.get(WEEK_FIELDS.weekBasedYear());
        int year2 = zdt2.get(WEEK_FIELDS.weekBasedYear());

        return year1 == year2 && week1 == week2;
    }
}