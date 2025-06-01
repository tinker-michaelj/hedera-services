// SPDX-License-Identifier: Apache-2.0
package org.hiero.otter.fixtures.logging;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.hiero.otter.fixtures.logging.internal.InMemoryAppender;

/**
 * A structured representation of a log event captured with the {@link InMemoryAppender}
 *
 * @param timestamp   The timestamp of the log message.
 * @param level       The severity level of the log message.
 * @param message     The formatted log message.
 * @param loggerName  The name of the logger that produced the event.
 * @param threadName  The name of the thread that generated the log.
 * @param marker      An optional marker associated with the log event.
 *
 * @see InMemoryAppender
 */
public record StructuredLog(
        long timestamp,
        @NonNull Level level,
        @NonNull String message,
        @NonNull String loggerName,
        @NonNull String threadName,
        @Nullable Marker marker,
        long nodeId) {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static String abbreviateClassName(final String fullClassName) {
        if (fullClassName == null || fullClassName.isEmpty()) {
            return fullClassName;
        }
        final String[] parts = fullClassName.split("\\.");
        if (parts.length == 0) {
            return fullClassName;
        }

        final StringBuilder abbreviated = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            abbreviated.append(parts[i].charAt(0)).append('.');
        }
        abbreviated.append(parts[parts.length - 1]);
        return abbreviated.toString();
    }

    @Override
    public String toString() {
        final ZonedDateTime dateTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault());

        return String.format(
                "%s [%s] [%s] (%s) [%s] (%s) - %s\n",
                dateTime.format(FORMATTER),
                level,
                nodeId >= 0 ? nodeId : "unknown",
                threadName,
                marker,
                abbreviateClassName(loggerName),
                message);
    }
}
