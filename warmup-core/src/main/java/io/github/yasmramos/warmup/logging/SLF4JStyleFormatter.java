package io.github.yasmramos.warmup.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.time.ZoneId;

public class SLF4JStyleFormatter extends Formatter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @Override
    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder(160);
        Instant ofEpochMilli = Instant.ofEpochMilli(record.getMillis());
        sb.append(TIME.format(ofEpochMilli)).append(" ");
        sb.append("[").append(Thread.currentThread().getName()).append("] ");
        sb.append(pad(level(record))).append(" ");
        sb.append(record.getLoggerName()).append(" - ");
        sb.append(formatMessage(record)).append(System.lineSeparator());

        if (record.getThrown() != null) {
            StringWriter sw = new StringWriter();
            record.getThrown().printStackTrace(new PrintWriter(sw));
            sb.append(sw);
        }
        return sb.toString();
    }

    private static String pad(String s) {
        return s.length() >= 5 ? s : s + "".repeat(5 - s.length());
    }

    private static String level(LogRecord record) {
        return switch (record.getLevel().getName()) {
            case "SEVERE" ->
                "ERROR";
            case "WARNING" ->
                "WARN";
            case "INFO" ->
                "INFO";
            case "CONFIG" ->
                "CONFIG";
            case "FINE" ->
                "DEBUG";
            case "FINER", "FINEST" ->
                "TRACE";
            default ->
                record.getLevel().getName();
        };
    }

}
