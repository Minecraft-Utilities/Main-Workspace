package xyz.mcutils.backend.common;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameModelTest {
    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyz";

    private static NameModel trained(int length, int samples) {
        return trained(length, samples, false);
    }

    private static NameModel trained(int length, int samples, boolean fifthOrder) {
        Random random = new Random(7);
        NameModel.Trainer trainer = new NameModel.Trainer(length, fifthOrder);
        for (int i = 0; i < samples; i++) {
            StringBuilder name = new StringBuilder();
            for (int j = 0; j < length; j++) {
                String vowels = "aeiou";
                name.append(j % 2 == 0 ? LETTERS.charAt(random.nextInt(LETTERS.length())) : vowels.charAt(random.nextInt(vowels.length())));
            }
            trainer.add(name.toString());
        }
        return trainer.build();
    }

    private static List<String> drain(NameModel model, int pageSize) {
        List<String> all = new ArrayList<>();
        NameModel.Cursor cursor = model.start();
        while (true) {
            NameModel.Page page = model.next(cursor, pageSize);
            all.addAll(page.names());
            cursor = page.next();
            if (page.exhausted()) {
                return all;
            }
        }
    }

    @Test
    void listsEveryNameOnceInCostOrder() {
        NameModel model = trained(3, 5000);
        List<String> names = drain(model, 1000);

        assertEquals(50_653, names.size());
        assertEquals(50_653, new HashSet<>(names).size());
        for (int i = 1; i < names.size(); i++) {
            int previousCost = model.costOf(names.get(i - 1));
            int cost = model.costOf(names.get(i));
            assertTrue(previousCost < cost
                            || (previousCost == cost && names.get(i - 1).compareTo(names.get(i)) != 0 && alphabetOrder(names.get(i - 1), names.get(i))),
                    names.get(i - 1) + " before " + names.get(i));
        }
    }

    @Test
    void fifthOrderModelListsEveryNameOnceInCostOrder() {
        NameModel model = trained(4, 20_000, true);
        List<String> names = drain(model, 1000);

        assertEquals(1_874_161, names.size());
        assertEquals(1_874_161, new HashSet<>(names).size());
        for (int i = 1; i < names.size(); i++) {
            assertTrue(model.costOf(names.get(i - 1)) <= model.costOf(names.get(i)), names.get(i - 1) + " before " + names.get(i));
        }
    }

    @Test
    void fifthOrderModelSurvivesSerialisation() {
        NameModel model = trained(5, 20_000, true);
        NameModel restored = NameModel.fromBytes(model.toBytes());

        assertEquals(model.next(model.start(), 5000), restored.next(restored.start(), 5000));
    }

    @Test
    void pageSizeDoesNotChangeTheSequence() {
        NameModel model = trained(3, 5000);
        List<String> reference = drain(model, 50_653);

        assertEquals(reference, drain(model, 1));
        assertEquals(reference, drain(model, 97));
        assertEquals(reference, drain(model, 4096));
    }

    @Test
    void likelyNamesComeFirst() {
        NameModel.Trainer trainer = new NameModel.Trainer(4);
        for (int i = 0; i < 1000; i++) {
            trainer.add("noob");
        }
        NameModel model = trainer.build();

        assertEquals("noob", model.next(model.start(), 1).names().getFirst());
    }

    @Test
    void survivesSerialisation() {
        NameModel model = trained(4, 3000);
        NameModel restored = NameModel.fromBytes(model.toBytes());

        assertEquals(model.length(), restored.length());
        NameModel.Page expected = model.next(model.start(), 5000);
        assertEquals(expected, restored.next(restored.start(), 5000));
    }

    @Test
    void resumesFromACursorInTheMiddleOfABand() {
        NameModel model = trained(4, 3000);
        NameModel.Page first = model.next(model.start(), 12_345);
        NameModel.Page second = model.next(first.next(), 777);
        List<String> whole = model.next(model.start(), 12_345 + 777).names();

        List<String> stitched = new ArrayList<>(first.names());
        stitched.addAll(second.names());
        assertEquals(whole, stitched);
        assertFalse(second.exhausted());
    }

    @Test
    void exhaustedModelStaysExhausted() {
        NameModel model = trained(2, 500);
        NameModel.Page all = model.next(model.start(), 10_000);

        assertTrue(all.exhausted());
        assertEquals(37 * 37, all.names().size());
        NameModel.Page again = model.next(all.next(), 10);
        assertTrue(again.exhausted());
        assertTrue(again.names().isEmpty());
    }

    @Test
    void rejectsNamesOutsideTheModel() {
        NameModel model = trained(3, 100);
        Set<String> invalid = Set.of("ab", "abcd", "AB!");
        for (String name : invalid) {
            assertThrows(IllegalArgumentException.class, () -> model.next(new NameModel.Cursor(0, name), 1));
        }
    }

    private static boolean alphabetOrder(String a, String b) {
        for (int i = 0; i < a.length(); i++) {
            int difference = NameModel.ALPHABET.indexOf(a.charAt(i)) - NameModel.ALPHABET.indexOf(b.charAt(i));
            if (difference != 0) {
                return difference < 0;
            }
        }
        return false;
    }
}
