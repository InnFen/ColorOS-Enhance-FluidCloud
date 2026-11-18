package dev.pixellock.coloros;

import android.content.Context;
import android.icu.util.ChineseCalendar;
import android.text.format.DateFormat;

import java.util.Locale;

/** Fallback date line when the ColorOS date view can't be found: "11月18日 周三 十月初十" style. */
final class DateText {
    private static final String[] MONTHS = {"正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"};
    private static final String[] TENS = {"初", "十", "廿", "三"};
    private static final String[] UNITS = {"一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};

    private DateText() { }

    static String format(Context c) {
        Locale locale = c.getResources().getConfiguration().getLocales().get(0);
        long now = System.currentTimeMillis();
        String pattern = DateFormat.getBestDateTimePattern(locale, "MMMdEEE");
        String date = DateFormat.format(pattern, now).toString();
        if ("zh".equals(locale.getLanguage())) date += "  " + lunar(now, locale);
        return date;
    }

    static String lunar(long millis, Locale locale) {
        try {
            ChineseCalendar cc = new ChineseCalendar();
            cc.setTimeInMillis(millis);
            int month = cc.get(ChineseCalendar.MONTH);
            int day = cc.get(ChineseCalendar.DAY_OF_MONTH);
            boolean leap = cc.get(ChineseCalendar.IS_LEAP_MONTH) == 1;
            boolean traditional = "TW".equals(locale.getCountry()) || "HK".equals(locale.getCountry())
                    || "Hant".equals(locale.getScript());
            String monthName = MONTHS[month];
            if (traditional && month == 11) monthName = "臘";
            return (leap ? (traditional ? "閏" : "闰") : "") + monthName + "月" + dayName(day);
        } catch (Throwable t) {
            return "";
        }
    }

    static String dayName(int day) {
        if (day == 10) return "初十";
        if (day == 20) return "二十";
        if (day == 30) return "三十";
        return TENS[day / 10] + UNITS[(day % 10) - 1];
    }
}
