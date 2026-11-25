package com.kindle.photodrop;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/** 农历与二十四节气计算，按北京时间显示，不依赖第三方库。 */
final class LunarCalendar {

    private static final TimeZone CHINA = TimeZone.getTimeZone("Asia/Shanghai");
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private static final String[] GAN = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    private static final String[] LUNAR_MONTHS =
            {"", "正月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "冬月", "腊月"};
    private static final String[] LUNAR_DAYS = {
            "", "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
            "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
            "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    };

    private static final String[] TERMS = {
            "小寒", "大寒", "立春", "雨水", "惊蛰", "春分", "清明", "谷雨",
            "立夏", "小满", "芒种", "夏至", "小暑", "大暑", "立秋", "处暑",
            "白露", "秋分", "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
    };

    private static final int[] TERM_INFO = {
            0, 21208, 42467, 63836, 85337, 107014, 128867, 150921,
            173149, 195551, 218072, 240693, 263343, 285989, 308563, 331033,
            353350, 375494, 397447, 419210, 440795, 462224, 483532, 504758
    };

    private static final int[] LUNAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
            0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0,
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
            0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
            0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0,
            0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
            0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
            0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
            0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,
            0x0d520
    };

    private static final long TERM_BASE = termBase();

    private LunarCalendar() {
    }

    /** 返回如「丙午年 十月十七」。 */
    static String lunarText(Calendar date) {
        int year = date.get(Calendar.YEAR);
        if (year < 1900 || year > 2100) {
            return "";
        }
        int[] lunar = lunarDate(date);
        int month = lunar[1];
        int day = lunar[2];
        boolean leap = lunar[3] == 1;
        return ganzhiYear(lunar[0]) + " " + (leap ? "闰" : "") + LUNAR_MONTHS[month] + LUNAR_DAYS[day];
    }

    /** 返回该公历日所在的节气。 */
    static String solarTerm(Calendar date) {
        int year = date.get(Calendar.YEAR);
        long dayStart = startOfDay(date);
        String previous = termsOfYear(year - 1, dayStart);
        String current = termsOfYear(year, dayStart);
        if (current != null) {
            return current;
        }
        return previous == null ? "冬至" : previous;
    }

    static String weekdayText(Calendar date) {
        String[] names = {"星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};
        return names[date.get(Calendar.DAY_OF_WEEK) - 1];
    }

    private static String ganzhiYear(int year) {
        int index = year - 4;
        return GAN[Math.floorMod(index, 10)] + ZHI[Math.floorMod(index, 12)] + "年";
    }

    private static int[] lunarDate(Calendar date) {
        int year = date.get(Calendar.YEAR);
        int month = date.get(Calendar.MONTH) + 1;
        int day = date.get(Calendar.DAY_OF_MONTH);
        int offset = daysFromBase(year, month, day);
        int temp = 0;
        for (int i = 1900; i < year; i++) {
            temp += yearDays(i);
        }
        // 春节之前仍属于上一农历年。
        if (offset < temp && year > 1900) {
            year--;
            temp -= yearDays(year);
        }
        int leap = leapMonth(year);
        int dayOfLunarYear = offset - temp + 1;
        int lunarMonth = 1;
        int leapFlag = 0;
        int i = 1;
        while (i <= 12) {
            int days = monthDays(year, i);
            if (dayOfLunarYear <= days) {
                lunarMonth = i;
                break;
            }
            dayOfLunarYear -= days;
            if (leap > 0 && i == leap) {
                days = leapDays(year);
                if (dayOfLunarYear <= days) {
                    lunarMonth = i;
                    leapFlag = 1;
                    break;
                }
                dayOfLunarYear -= days;
            }
            i++;
        }
        return new int[]{year, lunarMonth, dayOfLunarYear, leapFlag};
    }

    private static int daysFromBase(int year, int month, int day) {
        Calendar base = new GregorianCalendar(UTC);
        base.clear();
        base.set(1900, 0, 31);
        Calendar target = new GregorianCalendar(UTC);
        target.clear();
        target.set(year, month - 1, day);
        return (int) ((target.getTimeInMillis() - base.getTimeInMillis()) / 86400000L);
    }

    private static int leapMonth(int year) {
        return LUNAR_INFO[year - 1900] & 0xf;
    }

    private static int leapDays(int year) {
        if (leapMonth(year) == 0) {
            return 0;
        }
        return (LUNAR_INFO[year - 1900] & 0x10000) == 0 ? 29 : 30;
    }

    private static int monthDays(int year, int month) {
        return (LUNAR_INFO[year - 1900] & (0x10000 >> month)) == 0 ? 29 : 30;
    }

    private static int yearDays(int year) {
        int info = LUNAR_INFO[year - 1900];
        int days = 348;
        for (int bit = 0x8000; bit > 0x8; bit >>= 1) {
            days += (info & bit) == 0 ? 0 : 1;
        }
        return days + leapDays(year);
    }

    private static String termsOfYear(int year, long dayStart) {
        if (year < 1900 || year > 2100) {
            return null;
        }
        String result = null;
        for (int i = 0; i < TERMS.length; i++) {
            long millis = (long) (31556925974.7 * (year - 1900) + TERM_INFO[i] * 60000L) + TERM_BASE;
            Calendar term = new GregorianCalendar(CHINA);
            term.setTimeInMillis(millis);
            if (startOfDay(term) <= dayStart) {
                result = TERMS[i];
            } else {
                break;
            }
        }
        return result;
    }

    private static long startOfDay(Calendar date) {
        Calendar day = new GregorianCalendar(CHINA);
        day.clear();
        day.set(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH));
        return day.getTimeInMillis();
    }

    private static long termBase() {
        Calendar base = new GregorianCalendar(UTC);
        base.clear();
        base.set(1900, 0, 6, 2, 5, 0);
        return base.getTimeInMillis();
    }
}
