package com.ruoyi.ems.room.protocol;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AttemptSequencer {

    private final Map<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    public long advance(String emsSessionId) {
        return sequences.computeIfAbsent(emsSessionId, k -> new AtomicLong(0))
            .incrementAndGet();
    }

    public long current(String emsSessionId) {
        AtomicLong l = sequences.get(emsSessionId);
        return l != null ? l.get() : 0;
    }

    public void clear(String emsSessionId) {
        sequences.remove(emsSessionId);
    }
}
