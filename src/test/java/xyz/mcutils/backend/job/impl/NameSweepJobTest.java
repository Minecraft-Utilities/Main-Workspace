package xyz.mcutils.backend.job.impl;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NameSweepJobTest {
    @Test
    void enumeratesEveryNameOfALengthExactlyOnce() {
        long total = NameSweepJob.totalNames(3);
        Set<String> names = new HashSet<>();
        for (long i = 0; i < total; i++) {
            names.add(NameSweepJob.nameAt(i, 3));
        }
        assertEquals(50_653, total);
        assertEquals(total, names.size());
    }

    @Test
    void firstAndLastNamesAreTheAlphabetEnds() {
        assertEquals("aaaa", NameSweepJob.nameAt(0, 4));
        assertEquals("____", NameSweepJob.nameAt(NameSweepJob.totalNames(4) - 1, 4));
    }
}
