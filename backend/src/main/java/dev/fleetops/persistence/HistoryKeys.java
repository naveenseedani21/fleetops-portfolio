package dev.fleetops.persistence;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

public final class HistoryKeys {
  private static final DateTimeFormatter FORMAT =
      new DateTimeFormatterBuilder().appendInstant(9).toFormatter();

  private HistoryKeys() {}

  public static String timestamp(Instant instant) {
    return FORMAT.format(instant);
  }
}
