package xyz.mcutils.backend.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NameSweepServiceTest {
    @Test
    void enumeratesEveryNameOfALengthExactlyOnce() {
        long total = NameSweepService.totalNames(3);
        Set<String> names = new HashSet<>();
        for (long i = 0; i < total; i++) {
            names.add(NameSweepService.nameAt(i, 3));
        }
        assertEquals(50_653, total);
        assertEquals(total, names.size());
    }

    @Test
    void firstAndLastNamesAreTheAlphabetEnds() {
        assertEquals("aaaa", NameSweepService.nameAt(0, 4));
        assertEquals("____", NameSweepService.nameAt(NameSweepService.totalNames(4) - 1, 4));
    }
}
