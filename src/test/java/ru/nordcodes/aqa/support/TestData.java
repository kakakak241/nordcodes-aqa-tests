package ru.nordcodes.aqa.support;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public final class TestData {
    private static final AtomicLong SEQUENCE = new AtomicLong(0x1000);

    private TestData() { }

    public static String token() {
        return String.format(Locale.ROOT, "%032X", SEQUENCE.incrementAndGet());
    }
}
