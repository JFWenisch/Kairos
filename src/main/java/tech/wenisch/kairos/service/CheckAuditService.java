package tech.wenisch.kairos.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Service
public class CheckAuditService {

    private static final int MAX_ENTRIES = 200;

    private final Deque<CheckAuditEntry> entries = new ArrayDeque<>();

    public synchronized void record(String kind, String resourceName, String target, String triggeredBy, String result) {
        entries.addFirst(new CheckAuditEntry(
                LocalDateTime.now(),
                kind,
                resourceName,
                target,
                triggeredBy == null || triggeredBy.isBlank() ? "Anonymous" : triggeredBy,
                result != null ? result : "—"
        ));
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
    }

    public synchronized List<CheckAuditEntry> getEntries() {
        return List.copyOf(entries);
    }
}
