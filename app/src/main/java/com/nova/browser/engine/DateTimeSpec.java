package com.nova.browser;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DateTimeSpec {
    public enum Kind { DATE, TIME, DATETIME_LOCAL, MONTH, WEEK }

    private static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);
    private static final Pattern WEEK = Pattern.compile("^(\\d{4,9})-W(\\d{2})$");

    private DateTimeSpec() {}

    public static LocalDateTime parse(Kind kind, String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        try {
            switch (kind) {
                case DATE:
                    return LocalDate.parse(s).atStartOfDay();
                case TIME:
                    return LocalTime.parse(s).atDate(EPOCH).truncatedTo(ChronoUnit.MINUTES);
                case DATETIME_LOCAL:
                    return LocalDateTime.parse(s.replace(' ', 'T')).truncatedTo(ChronoUnit.MINUTES);
                case MONTH:
                    return YearMonth.parse(s).atDay(1).atStartOfDay();
                case WEEK:
                    return parseWeek(s);
                default:
                    return null;
            }
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static LocalDateTime parseWeek(String s) {
        Matcher m = WEEK.matcher(s);
        if (!m.matches()) return null;
        int year = Integer.parseInt(m.group(1));
        int week = Integer.parseInt(m.group(2));
        LocalDate d = LocalDate.of(year, 1, 4).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week).with(ChronoField.DAY_OF_WEEK, 1);
        if (d.get(IsoFields.WEEK_BASED_YEAR) != year || d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) != week) return null;
        return d.atStartOfDay();
    }

    public static LocalDateTime normalize(Kind kind, LocalDateTime v) {
        LocalDate d = v.toLocalDate();
        switch (kind) {
            case DATE:
                return d.atStartOfDay();
            case TIME:
                return v.toLocalTime().atDate(EPOCH).truncatedTo(ChronoUnit.MINUTES);
            case DATETIME_LOCAL:
                return v.truncatedTo(ChronoUnit.MINUTES);
            case MONTH:
                return d.withDayOfMonth(1).atStartOfDay();
            case WEEK:
                return d.with(ChronoField.DAY_OF_WEEK, 1).atStartOfDay();
            default:
                return v;
        }
    }

    public static String format(Kind kind, LocalDateTime value) {
        LocalDateTime v = normalize(kind, value);
        LocalDate d = v.toLocalDate();
        switch (kind) {
            case DATE:
                return String.format("%04d-%02d-%02d", d.getYear(), d.getMonthValue(), d.getDayOfMonth());
            case TIME:
                return String.format("%02d:%02d", v.getHour(), v.getMinute());
            case DATETIME_LOCAL:
                return String.format("%04d-%02d-%02dT%02d:%02d", d.getYear(), d.getMonthValue(), d.getDayOfMonth(), v.getHour(), v.getMinute());
            case MONTH:
                return String.format("%04d-%02d", d.getYear(), d.getMonthValue());
            case WEEK:
                return String.format("%04d-W%02d", d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            default:
                return "";
        }
    }

    public static LocalDateTime clamp(Kind kind, LocalDateTime value, String minRaw, String maxRaw) {
        LocalDateTime v = normalize(kind, value);
        LocalDateTime min = parse(kind, minRaw);
        LocalDateTime max = parse(kind, maxRaw);
        if (min != null && max != null && min.isAfter(max)) {
            if (kind != Kind.TIME) return v;
            if (!v.isBefore(min) || !v.isAfter(max)) return v;
            long toMin = ChronoUnit.MINUTES.between(v, min);
            long fromMax = ChronoUnit.MINUTES.between(max, v);
            return toMin <= fromMax ? min : max;
        }
        if (min != null && v.isBefore(min)) return min;
        if (max != null && v.isAfter(max)) return max;
        return v;
    }

    public static LocalDateTime initial(Kind kind, String def, String min, String max, LocalDateTime now) {
        LocalDateTime base = parse(kind, def);
        return clamp(kind, base != null ? base : now, min, max);
    }

    public static LocalDateTime minStart(Kind kind, String minRaw) {
        return parse(kind, minRaw);
    }

    public static LocalDateTime maxEnd(Kind kind, String maxRaw) {
        LocalDateTime m = parse(kind, maxRaw);
        if (m == null) return null;
        switch (kind) {
            case DATE:
                return m.plusDays(1).minusNanos(1);
            case MONTH:
                return m.plusMonths(1).minusNanos(1);
            case WEEK:
                return m.plusWeeks(1).minusNanos(1);
            default:
                return m;
        }
    }

    public static long epochMillis(LocalDateTime v, ZoneId zone) {
        return v.atZone(zone).toInstant().toEpochMilli();
    }
}
