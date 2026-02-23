/*
 * Copyright 2026 Pin Code Authenticator Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package it.pleaseopen.keycloak.extensions.pin.keyboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.awt.Rectangle;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KeyboardImageGenerator}.
 */
class KeyboardImageGeneratorTest {

    // ──────────────────────────────────────────────────────────────────────
    // generate()
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void generate_returnsNonNullResult() {
        KeyboardImageGenerator.KeyboardImage result = KeyboardImageGenerator.generate();
        assertThat(result).isNotNull();
        assertThat(result.getBase64Png()).isNotNull().isNotEmpty();
        assertThat(result.getCoordinateMap()).isNotNull();
    }

    @Test
    void generate_containsAllTenDigits() {
        KeyboardImageGenerator.KeyboardImage result = KeyboardImageGenerator.generate();
        Map<String, int[]> map = result.getCoordinateMap();
        assertThat(map).hasSize(10);
        for (int i = 0; i <= 9; i++) {
            assertThat(map).containsKey(String.valueOf(i));
        }
    }

    @Test
    void generate_coordinatesWithinImageBounds() {
        KeyboardImageGenerator.KeyboardImage result = KeyboardImageGenerator.generate();
        for (int[] box : result.getCoordinateMap().values()) {
            assertThat(box).hasSize(4);
            int x = box[0], y = box[1], w = box[2], h = box[3];
            assertThat(x).isGreaterThanOrEqualTo(0);
            assertThat(y).isGreaterThanOrEqualTo(0);
            assertThat(x + w).isLessThanOrEqualTo(KeyboardImageGenerator.IMAGE_WIDTH);
            assertThat(y + h).isLessThanOrEqualTo(KeyboardImageGenerator.IMAGE_HEIGHT);
        }
    }

    @RepeatedTest(5)
    void generate_noOverlappingCells() {
        KeyboardImageGenerator.KeyboardImage result = KeyboardImageGenerator.generate();
        int[][] boxes = result.getCoordinateMap().values().toArray(new int[0][]);

        for (int i = 0; i < boxes.length; i++) {
            Rectangle ri = new Rectangle(boxes[i][0], boxes[i][1], boxes[i][2], boxes[i][3]);
            for (int j = i + 1; j < boxes.length; j++) {
                Rectangle rj = new Rectangle(boxes[j][0], boxes[j][1], boxes[j][2], boxes[j][3]);
                assertThat(ri.intersects(rj))
                        .as("Cell %d and cell %d should not overlap", i, j)
                        .isFalse();
            }
        }
    }

    @Test
    void generate_producesValidPng() {
        KeyboardImageGenerator.KeyboardImage result = KeyboardImageGenerator.generate();
        byte[] decoded = Base64.getDecoder().decode(result.getBase64Png());
        // PNG magic bytes: 137 80 78 71 13 10 26 10
        assertThat(decoded.length).isGreaterThan(8);
        assertThat(decoded[0] & 0xFF).isEqualTo(0x89);
        assertThat(decoded[1]).isEqualTo((byte) 'P');
        assertThat(decoded[2]).isEqualTo((byte) 'N');
        assertThat(decoded[3]).isEqualTo((byte) 'G');
    }

    @Test
    void generate_cellDimensionsMatchConstants() {
        KeyboardImageGenerator.KeyboardImage result = KeyboardImageGenerator.generate();
        for (int[] box : result.getCoordinateMap().values()) {
            assertThat(box[2]).isEqualTo(KeyboardImageGenerator.CELL_WIDTH);
            assertThat(box[3]).isEqualTo(KeyboardImageGenerator.CELL_HEIGHT);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // generate(obfuscate=true)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void generate_withObfuscation_returnsValidResult() {
        KeyboardImageGenerator.KeyboardImage result = KeyboardImageGenerator.generate(true);
        assertThat(result.getBase64Png()).isNotEmpty();
        assertThat(result.getCoordinateMap()).hasSize(10);
    }

    @RepeatedTest(3)
    void generate_withObfuscation_noOverlap() {
        KeyboardImageGenerator.KeyboardImage result = KeyboardImageGenerator.generate(true);
        int[][] boxes = result.getCoordinateMap().values().toArray(new int[0][]);

        for (int i = 0; i < boxes.length; i++) {
            Rectangle ri = new Rectangle(boxes[i][0], boxes[i][1], boxes[i][2], boxes[i][3]);
            for (int j = i + 1; j < boxes.length; j++) {
                Rectangle rj = new Rectangle(boxes[j][0], boxes[j][1], boxes[j][2], boxes[j][3]);
                assertThat(ri.intersects(rj)).isFalse();
            }
        }
    }

    @Test
    void generate_withObfuscation_differentImageThanWithout() {
        KeyboardImageGenerator.KeyboardImage plain = KeyboardImageGenerator.generate(false);
        KeyboardImageGenerator.KeyboardImage obfuscated = KeyboardImageGenerator.generate(true);
        // Images should be different (different digit placement + noise)
        // Very unlikely to be identical; we compare Base64 strings
        assertThat(obfuscated.getBase64Png()).isNotEqualTo(plain.getBase64Png());
    }

    // ──────────────────────────────────────────────────────────────────────
    // serializeCoordinateMap / deserializeCoordinateMap
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void serializeAndDeserialize_roundTrip() {
        Map<String, int[]> original = new HashMap<>();
        original.put("0", new int[]{10, 20, 56, 48});
        original.put("5", new int[]{100, 150, 56, 48});
        original.put("9", new int[]{200, 50, 56, 48});

        String serialized = KeyboardImageGenerator.serializeCoordinateMap(original);
        Map<String, int[]> deserialized = KeyboardImageGenerator.deserializeCoordinateMap(serialized);

        assertThat(deserialized).hasSize(3);
        assertThat(deserialized.get("0")).containsExactly(10, 20, 56, 48);
        assertThat(deserialized.get("5")).containsExactly(100, 150, 56, 48);
        assertThat(deserialized.get("9")).containsExactly(200, 50, 56, 48);
    }

    @Test
    void serializeCoordinateMap_format() {
        Map<String, int[]> map = new HashMap<>();
        map.put("3", new int[]{10, 20, 56, 48});
        String serialized = KeyboardImageGenerator.serializeCoordinateMap(map);
        assertThat(serialized).isEqualTo("3:10,20,56,48");
    }

    @Test
    void deserializeCoordinateMap_emptyString() {
        Map<String, int[]> result = KeyboardImageGenerator.deserializeCoordinateMap("");
        assertThat(result).isEmpty();
    }

    @Test
    void deserializeCoordinateMap_null() {
        Map<String, int[]> result = KeyboardImageGenerator.deserializeCoordinateMap(null);
        assertThat(result).isEmpty();
    }

    @Test
    void deserializeCoordinateMap_malformedEntries_ignored() {
        // Missing coords, malformed separator
        String malformed = "3:10,20;bad;5:100,150,56,48";
        Map<String, int[]> result = KeyboardImageGenerator.deserializeCoordinateMap(malformed);
        // Only the valid entry "5:100,150,56,48" should survive
        assertThat(result).hasSize(1);
        assertThat(result.get("5")).containsExactly(100, 150, 56, 48);
    }

    @Test
    void serializeAndDeserialize_fullGeneration() {
        KeyboardImageGenerator.KeyboardImage kb = KeyboardImageGenerator.generate();
        String serialized = KeyboardImageGenerator.serializeCoordinateMap(kb.getCoordinateMap());
        Map<String, int[]> deserialized = KeyboardImageGenerator.deserializeCoordinateMap(serialized);
        
        assertThat(deserialized).hasSize(10);
        for (String digit : kb.getCoordinateMap().keySet()) {
            assertThat(deserialized.get(digit))
                    .containsExactly(kb.getCoordinateMap().get(digit));
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // resolveDigit
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void resolveDigit_hitInsideBox() {
        Map<String, int[]> map = new HashMap<>();
        map.put("7", new int[]{50, 60, 56, 48});

        // Click at (70, 80) — inside the box [50,60]→[106,108]
        String digit = KeyboardImageGenerator.resolveDigit(70, 80, map);
        assertThat(digit).isEqualTo("7");
    }

    @Test
    void resolveDigit_hitOnTopLeftCorner() {
        Map<String, int[]> map = new HashMap<>();
        map.put("3", new int[]{100, 100, 56, 48});

        String digit = KeyboardImageGenerator.resolveDigit(100, 100, map);
        assertThat(digit).isEqualTo("3");
    }

    @Test
    void resolveDigit_missOutsideBox() {
        Map<String, int[]> map = new HashMap<>();
        map.put("4", new int[]{50, 60, 56, 48});

        // Click at (5, 5) — outside all boxes
        String digit = KeyboardImageGenerator.resolveDigit(5, 5, map);
        assertThat(digit).isNull();
    }

    @Test
    void resolveDigit_hitAtBottomRightEdge() {
        Map<String, int[]> map = new HashMap<>();
        map.put("2", new int[]{50, 60, 56, 48});

        // Right edge: x = 50+56-1 = 105, bottom edge: y = 60+48-1 = 107
        String digit = KeyboardImageGenerator.resolveDigit(105, 107, map);
        assertThat(digit).isEqualTo("2");
    }

    @Test
    void resolveDigit_missJustOutside() {
        Map<String, int[]> map = new HashMap<>();
        map.put("8", new int[]{50, 60, 56, 48});

        // Just outside: x = 50+56 = 106 (exclusive)
        String digit = KeyboardImageGenerator.resolveDigit(106, 80, map);
        assertThat(digit).isNull();
    }

    @Test
    void resolveDigit_emptyMap() {
        String digit = KeyboardImageGenerator.resolveDigit(50, 50, new HashMap<>());
        assertThat(digit).isNull();
    }

    @Test
    void resolveDigit_withFullGeneratedMap() {
        KeyboardImageGenerator.KeyboardImage kb = KeyboardImageGenerator.generate();
        Map<String, int[]> map = kb.getCoordinateMap();

        // For each digit, clicking at its centre should resolve correctly
        for (Map.Entry<String, int[]> entry : map.entrySet()) {
            int[] box = entry.getValue();
            int cx = box[0] + box[2] / 2;
            int cy = box[1] + box[3] / 2;
            String resolved = KeyboardImageGenerator.resolveDigit(cx, cy, map);
            assertThat(resolved)
                    .as("Centre of digit %s should resolve correctly", entry.getKey())
                    .isEqualTo(entry.getKey());
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // overlapsAny
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void overlapsAny_noExisting_returnsFalse() {
        Rectangle candidate = new Rectangle(10, 10, 56, 48);
        boolean result = KeyboardImageGenerator.overlapsAny(candidate, java.util.List.of(), 6);
        assertThat(result).isFalse();
    }

    @Test
    void overlapsAny_overlapping_returnsTrue() {
        Rectangle existing = new Rectangle(10, 10, 56, 48);
        Rectangle candidate = new Rectangle(20, 20, 56, 48);
        boolean result = KeyboardImageGenerator.overlapsAny(candidate, java.util.List.of(existing), 6);
        assertThat(result).isTrue();
    }

    @Test
    void overlapsAny_withGap_detectsNearbyCollision() {
        Rectangle existing = new Rectangle(10, 10, 56, 48);
        // Place candidate just 2 pixels away (gap=6 means this should be detected)
        Rectangle candidate = new Rectangle(68, 10, 56, 48); // 10+56+2 = 68
        boolean result = KeyboardImageGenerator.overlapsAny(candidate, java.util.List.of(existing), 6);
        assertThat(result).isTrue();
    }

    @Test
    void overlapsAny_wellSeparated_returnsFalse() {
        Rectangle existing = new Rectangle(10, 10, 56, 48);
        Rectangle candidate = new Rectangle(200, 200, 56, 48);
        boolean result = KeyboardImageGenerator.overlapsAny(candidate, java.util.List.of(existing), 6);
        assertThat(result).isFalse();
    }

    // ──────────────────────────────────────────────────────────────────────
    // findNonOverlappingPosition
    // ──────────────────────────────────────────────────────────────────────

    @RepeatedTest(3)
    void findNonOverlappingPosition_canPlaceTenCells() {
        java.util.List<Rectangle> placed = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Rectangle pos = KeyboardImageGenerator.findNonOverlappingPosition(placed);
            assertThat(pos).isNotNull();
            assertThat(pos.width).isEqualTo(KeyboardImageGenerator.CELL_WIDTH);
            assertThat(pos.height).isEqualTo(KeyboardImageGenerator.CELL_HEIGHT);
            placed.add(pos);
        }
        // All 10 positions should be non-overlapping
        for (int i = 0; i < placed.size(); i++) {
            for (int j = i + 1; j < placed.size(); j++) {
                assertThat(placed.get(i).intersects(placed.get(j)))
                        .as("Position %d and %d should not overlap", i, j)
                        .isFalse();
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Image dimensions
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void imageConstants_areReasonable() {
        assertThat(KeyboardImageGenerator.IMAGE_WIDTH).isGreaterThanOrEqualTo(200);
        assertThat(KeyboardImageGenerator.IMAGE_HEIGHT).isGreaterThanOrEqualTo(150);
        assertThat(KeyboardImageGenerator.CELL_WIDTH).isGreaterThanOrEqualTo(30);
        assertThat(KeyboardImageGenerator.CELL_HEIGHT).isGreaterThanOrEqualTo(30);
    }
}
