package pro.icover.vitrina;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class LogicTest {

    // ---------- Navigator: раскладка «Витрины» в координатах экрана 1280×720 ----------
    // 0..3 — ярлыки сверху справа, 4..9 — док (центр по Y 486).
    private static final float[] CX = {640, 820, 960, 1110, 200, 376, 552, 728, 904, 1080};
    private static final float[] CY = {60, 60, 60, 60, 486, 486, 486, 486, 486, 486};

    @Test
    public void rightMovesAlongDock() {
        assertEquals(5, Navigator.pick(CX, CY, 4, Navigator.RIGHT, null));
    }

    @Test
    public void rightAtDockEndFindsNothing() {
        assertEquals(-1, Navigator.pick(CX, CY, 9, Navigator.RIGHT, null));
    }

    @Test
    public void upFromDockGoesToNearestShortcut() {
        // Док 9 (x=1080) → ярлык «Настройки» (x=1110).
        assertEquals(3, Navigator.pick(CX, CY, 9, Navigator.UP, null));
    }

    @Test
    public void downFromShortcutGoesToDock() {
        // Ярлык 1 (x=820) → ближайшая по X плитка дока 8 (x=904), а не 7 (x=728).
        assertEquals(8, Navigator.pick(CX, CY, 1, Navigator.DOWN, null));
    }

    @Test
    public void downFromDockFindsNothingWhenGridHidden() {
        assertEquals(-1, Navigator.pick(CX, CY, 6, Navigator.DOWN, null));
    }

    @Test
    public void disallowedItemsAreSkipped() {
        boolean[] allowed = new boolean[CX.length];
        Arrays.fill(allowed, true);
        allowed[5] = false;
        assertEquals(6, Navigator.pick(CX, CY, 4, Navigator.RIGHT, allowed));
    }

    @Test
    public void leftRightStaysInRowInsteadOfJumping() {
        // Из ярлыка 0 вправо — ярлык 1, а не ближайшая по X плитка дока ниже.
        assertEquals(1, Navigator.pick(CX, CY, 0, Navigator.RIGHT, null));
    }

    @Test
    public void nearestByXPicksClosestColumn() {
        float[] grid = {100, 260, 420, 580};
        assertEquals(2, Navigator.nearestByX(grid, 450, null));
    }

    // ---------- DockModel ----------
    @Test
    public void defaultsTakeFirstSixInstalledApps() {
        List<String> installed = Arrays.asList("a", "b", "c", "d", "e", "f", "g");
        assertEquals(Arrays.asList("a", "b", "c", "d", "e", "f"), DockModel.defaults(installed));
    }

    @Test
    public void defaultsWithFewAppsTakeAll() {
        assertEquals(Collections.singletonList("x"), DockModel.defaults(Collections.singletonList("x")));
        assertTrue(DockModel.defaults(Collections.emptyList()).isEmpty());
    }

    @Test
    public void sanitizeDropsRemovedAndDuplicates() {
        List<String> saved = Arrays.asList("b", "gone", "b", "a");
        assertEquals(Arrays.asList("b", "a"), DockModel.sanitize(saved, Arrays.asList("a", "b", "c")));
    }

    @Test
    public void encodeDecodeRoundTrip() {
        List<String> dock = Arrays.asList("com.example.first", "com.example.second");
        assertEquals(dock, DockModel.decode(DockModel.encode(dock)));
        assertTrue(DockModel.decode("").isEmpty());
        assertTrue(DockModel.decode(null).isEmpty());
    }

    @Test
    public void addAppendsWhenRoom() {
        assertEquals(Arrays.asList("a", "b", "c"), DockModel.add(Arrays.asList("a", "b"), "c"));
        assertEquals(null, DockModel.displacedBy(Arrays.asList("a", "b"), "c"));
    }

    @Test
    public void addReplacesLastWhenFull() {
        List<String> full = Arrays.asList("a", "b", "c", "d", "e", "f");
        assertEquals("f", DockModel.displacedBy(full, "x"));
        assertEquals(Arrays.asList("a", "b", "c", "d", "e", "x"), DockModel.add(full, "x"));
    }

    @Test
    public void addExistingIsNoOp() {
        List<String> full = Arrays.asList("a", "b", "c", "d", "e", "f");
        assertEquals(full, DockModel.add(full, "c"));
        assertEquals(null, DockModel.displacedBy(full, "c"));
    }

    @Test
    public void removeDropsOnlyThatApp() {
        assertEquals(Arrays.asList("a", "c"), DockModel.remove(Arrays.asList("a", "b", "c"), "b"));
        assertEquals(Arrays.asList("a", "c"), DockModel.remove(Arrays.asList("a", "c"), "zzz"));
    }

    @Test
    public void moveSwapsWithNeighbourAndStopsAtEdges() {
        List<String> d = Arrays.asList("a", "b", "c");
        assertEquals(Arrays.asList("b", "a", "c"), DockModel.move(d, "b", -1));
        assertEquals(Arrays.asList("a", "c", "b"), DockModel.move(d, "b", 1));
        assertEquals(d, DockModel.move(d, "a", -1));
        assertEquals(d, DockModel.move(d, "c", 1));
        assertEquals(d, DockModel.move(d, "zzz", 1));
    }

    // ---------- TimeOfDay ----------
    @Test
    public void slotsMatchDemoBoundaries() {
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.slot(0));
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.slot(5));
        assertEquals(TimeOfDay.MORNING, TimeOfDay.slot(6));
        assertEquals(TimeOfDay.MORNING, TimeOfDay.slot(10));
        assertEquals(TimeOfDay.DAY, TimeOfDay.slot(11));
        assertEquals(TimeOfDay.DAY, TimeOfDay.slot(16));
        assertEquals(TimeOfDay.EVENING, TimeOfDay.slot(17));
        assertEquals(TimeOfDay.EVENING, TimeOfDay.slot(21));
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.slot(22));
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.slot(23));
    }

    // ---------- Blur ----------
    @Test
    public void blurKeepsUniformImageUniform() {
        int[] px = new int[16 * 9];
        Arrays.fill(px, 0xFF336699);
        Blur.boxBlur(px, 16, 9, 3);
        for (int c : px) {
            assertEquals(0xFF336699, c);
        }
    }

    @Test
    public void blurSpreadsImpulseSymmetrically() {
        int w = 21;
        int h = 21;
        int[] px = new int[w * h];
        Arrays.fill(px, 0xFF000000);
        px[10 * w + 10] = 0xFFFFFFFF;
        Blur.boxBlur(px, w, h, 2);
        int center = px[10 * w + 10] & 0xFF;
        int left = px[10 * w + 8] & 0xFF;
        int right = px[10 * w + 12] & 0xFF;
        int up = px[8 * w + 10] & 0xFF;
        assertTrue(center > 0);
        assertTrue(center >= left);
        // Проходы идут по очереди, округление даёт расхождение не больше единицы.
        assertEquals(left, right, 1);
        assertEquals(left, up, 1);
    }

    @Test
    public void overlayBlendsTintOverPixels() {
        int[] px = {0xFF000000, 0xFFFFFFFF};
        // Белый с альфой 50%: чёрный → серый 128 (округление), белый остаётся белым.
        Blur.overlay(px, 0x80FFFFFF);
        assertEquals(0xFF808080, px[0]);
        assertEquals(0xFFFFFFFF, px[1]);
        int[] same = {0xFF336699};
        Blur.overlay(same, 0x00FF0000);
        assertEquals(0xFF336699, same[0]);
    }

    @Test
    public void saturationOfGreyIsNoOpAndBrightnessScales() {
        int[] px = {0xFF808080};
        Blur.saturateBrighten(px, 1.3f, 1f);
        assertEquals(0xFF808080, px[0]);
        Blur.saturateBrighten(px, 1f, 0.5f);
        assertEquals(0xFF404040, px[0]);
    }
}
