package com.hathoute.kubernetes.operator.openhands.reporter;

import java.time.Instant;
import java.util.Map;

public record Event(int id, Instant timestamp, String source, String message,
                    Map<String, Object> raw) {
}
