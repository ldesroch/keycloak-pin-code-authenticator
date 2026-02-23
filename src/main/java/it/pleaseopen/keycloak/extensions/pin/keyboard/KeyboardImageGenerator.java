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

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a server-side keyboard image with digits placed at random,
 * non-overlapping positions. Returns a Base64-encoded PNG and a map of
 * digit → bounding-box coordinates for server-side hit-test validation.
 *
 * <p>The client never sees digit positions — only the rendered pixels.
 * Click coordinates are sent back and resolved server-side using the
 * stored coordinate map (kept in Keycloak auth session notes).
 *
 * <p>OCR obfuscation features (when enabled):
 * <ul>
 *   <li>Random font family and size per digit</li>
 *   <li>Random foreground colour per digit</li>
 *   <li>Random noise lines across the image</li>
 *   <li>Random noise dots across the image</li>
 * </ul>
 */
public class KeyboardImageGenerator {

    // Image dimensions
    public static final int IMAGE_WIDTH = 400;
    public static final int IMAGE_HEIGHT = 320;

    // Each digit cell size (bounding box for hit-test)
    public static final int CELL_WIDTH = 56;
    public static final int CELL_HEIGHT = 48;

    // Minimum gap between cells
    private static final int CELL_GAP = 4;

    // Padding from image edges
    private static final int PADDING = 10;

    // Available font families for obfuscation
    private static final String[] FONTS = {
        "SansSerif", "Serif", "Monospaced", "Dialog", "DialogInput"
    };

    private static final SecureRandom random = new SecureRandom();

    /**
     * Result of {@link #generate}: the Base64 PNG and the coordinate map.
     */
    public static class KeyboardImage {
        private final String base64Png;
        private final Map<String, int[]> coordinateMap; // digit → [x, y, w, h]

        KeyboardImage(String base64Png, Map<String, int[]> coordinateMap) {
            this.base64Png = base64Png;
            this.coordinateMap = coordinateMap;
        }

        /** Base64-encoded PNG (no data-URI prefix). */
        public String getBase64Png() {
            return base64Png;
        }

        /**
         * Map of digit string ("0"–"9") → {@code [x, y, width, height]}
         * representing the bounding box in which the digit was drawn.
         */
        public Map<String, int[]> getCoordinateMap() {
            return coordinateMap;
        }
    }

    /**
     * Generates a keyboard image with obfuscation disabled.
     */
    public static KeyboardImage generate() {
        return generate(false);
    }

    /**
     * Generates a keyboard image.
     *
     * @param obfuscate whether to apply OCR-obfuscation effects
     * @return a {@link KeyboardImage} containing Base64 PNG and coordinate map
     */
    public static KeyboardImage generate(boolean obfuscate) {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // Enable antialiasing
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background
        g.setColor(new Color(245, 245, 245));
        g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

        // Shuffle digits
        List<String> digits = new ArrayList<>();
        for (int i = 0; i <= 9; i++) {
            digits.add(String.valueOf(i));
        }
        Collections.shuffle(digits, random);

        // Place digits at random non-overlapping positions
        List<Rectangle> placed = new ArrayList<>();
        Map<String, int[]> coordinateMap = new HashMap<>();

        for (String digit : digits) {
            Rectangle pos = findNonOverlappingPosition(placed);
            placed.add(pos);
            coordinateMap.put(digit, new int[]{pos.x, pos.y, pos.width, pos.height});
        }

        // Draw obfuscation background effects before digits
        if (obfuscate) {
            drawNoiseLines(g);
            drawNoiseDots(g);
        }

        // Draw each digit
        for (int i = 0; i < digits.size(); i++) {
            String digit = digits.get(i);
            Rectangle box = placed.get(i);
            drawDigit(g, digit, box, obfuscate);
        }

        // Draw obfuscation foreground effects after digits
        if (obfuscate) {
            drawForegroundNoiseLines(g);
        }

        g.dispose();

        // Encode to Base64 PNG
        String base64 = encodeToBase64Png(image);

        return new KeyboardImage(base64, coordinateMap);
    }

    /**
     * Finds a random position for a cell that does not overlap any already-placed cell.
     * Uses rejection sampling with a fallback grid if too many collisions.
     */
    static Rectangle findNonOverlappingPosition(List<Rectangle> placed) {
        int maxAttempts = 200;
        int minX = PADDING;
        int minY = PADDING;
        int maxX = IMAGE_WIDTH - CELL_WIDTH - PADDING;
        int maxY = IMAGE_HEIGHT - CELL_HEIGHT - PADDING;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = minX + random.nextInt(maxX - minX + 1);
            int y = minY + random.nextInt(maxY - minY + 1);
            Rectangle candidate = new Rectangle(x, y, CELL_WIDTH, CELL_HEIGHT);

            if (!overlapsAny(candidate, placed, CELL_GAP)) {
                return candidate;
            }
        }

        // Fallback: use a grid position that hasn't been taken
        return fallbackGridPosition(placed);
    }

    /**
     * Checks whether a candidate rectangle (expanded by gap) overlaps any placed rectangle.
     */
    static boolean overlapsAny(Rectangle candidate, List<Rectangle> placed, int gap) {
        Rectangle expanded = new Rectangle(
                candidate.x - gap, candidate.y - gap,
                candidate.width + 2 * gap, candidate.height + 2 * gap);
        for (Rectangle r : placed) {
            if (expanded.intersects(r)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fallback grid placement: 5 columns × 2 rows.
     */
    private static Rectangle fallbackGridPosition(List<Rectangle> placed) {
        int cols = 5;
        int cellW = CELL_WIDTH;
        int cellH = CELL_HEIGHT;
        int gapX = (IMAGE_WIDTH - 2 * PADDING - cols * cellW) / Math.max(cols - 1, 1);
        int gapY = (IMAGE_HEIGHT - 2 * PADDING - 2 * cellH) / 2;
        int startX = PADDING;
        int startY = PADDING + gapY / 2;

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < cols; col++) {
                int x = startX + col * (cellW + gapX);
                int y = startY + row * (cellH + gapY);
                Rectangle candidate = new Rectangle(x, y, cellW, cellH);
                if (!overlapsAny(candidate, placed, 0)) {
                    return candidate;
                }
            }
        }
        // Last resort: place sequentially (should never happen with 10 digits)
        int idx = placed.size();
        int col = idx % cols;
        int row = idx / cols;
        int x = PADDING + col * (cellW + 10);
        int y = PADDING + row * (cellH + 10);
        return new Rectangle(x, y, cellW, cellH);
    }

    /**
     * Draws a single digit inside its bounding box.
     */
    private static void drawDigit(Graphics2D g, String digit, Rectangle box, boolean obfuscate) {
        // Draw cell background with slight random colour variation
        Color bgColor;
        if (obfuscate) {
            int bg = 220 + random.nextInt(30); // light grey variation
            bgColor = new Color(bg, bg - random.nextInt(15), bg - random.nextInt(15));
        } else {
            bgColor = new Color(240, 240, 240);
        }
        g.setColor(bgColor);
        g.fillRoundRect(box.x, box.y, box.width, box.height, 8, 8);

        // Draw cell border
        g.setColor(new Color(180, 180, 180));
        g.drawRoundRect(box.x, box.y, box.width, box.height, 8, 8);

        // Choose font
        String fontFamily;
        int fontSize;
        int fontStyle;
        if (obfuscate) {
            fontFamily = FONTS[random.nextInt(FONTS.length)];
            fontSize = 20 + random.nextInt(10); // 20–29
            fontStyle = random.nextBoolean() ? Font.BOLD : Font.PLAIN;
        } else {
            fontFamily = "SansSerif";
            fontSize = 24;
            fontStyle = Font.BOLD;
        }
        g.setFont(new Font(fontFamily, fontStyle, fontSize));

        // Choose text colour
        Color textColor;
        if (obfuscate) {
            textColor = new Color(random.nextInt(80), random.nextInt(80), random.nextInt(80));
        } else {
            textColor = new Color(50, 50, 50);
        }
        g.setColor(textColor);

        // Centre the digit in the box
        FontMetrics fm = g.getFontMetrics();
        int textX = box.x + (box.width - fm.stringWidth(digit)) / 2;
        int textY = box.y + (box.height - fm.getHeight()) / 2 + fm.getAscent();

        // Apply slight random offset for obfuscation
        if (obfuscate) {
            textX += random.nextInt(5) - 2;
            textY += random.nextInt(5) - 2;
        }

        g.drawString(digit, textX, textY);
    }

    /**
     * Draws random noise lines in the background.
     */
    private static void drawNoiseLines(Graphics2D g) {
        int lineCount = 8 + random.nextInt(8);
        for (int i = 0; i < lineCount; i++) {
            g.setColor(new Color(
                    180 + random.nextInt(60),
                    180 + random.nextInt(60),
                    180 + random.nextInt(60),
                    80 + random.nextInt(80)));
            g.setStroke(new BasicStroke(1 + random.nextFloat() * 1.5f));
            g.drawLine(
                    random.nextInt(IMAGE_WIDTH), random.nextInt(IMAGE_HEIGHT),
                    random.nextInt(IMAGE_WIDTH), random.nextInt(IMAGE_HEIGHT));
        }
        // Reset stroke
        g.setStroke(new BasicStroke(1));
    }

    /**
     * Draws random noise dots in the background.
     */
    private static void drawNoiseDots(Graphics2D g) {
        int dotCount = 80 + random.nextInt(80);
        for (int i = 0; i < dotCount; i++) {
            g.setColor(new Color(
                    140 + random.nextInt(100),
                    140 + random.nextInt(100),
                    140 + random.nextInt(100)));
            int size = 1 + random.nextInt(3);
            g.fillOval(random.nextInt(IMAGE_WIDTH), random.nextInt(IMAGE_HEIGHT), size, size);
        }
    }

    /**
     * Draws subtle foreground noise lines over the digits (makes OCR harder).
     */
    private static void drawForegroundNoiseLines(Graphics2D g) {
        int lineCount = 3 + random.nextInt(4);
        for (int i = 0; i < lineCount; i++) {
            g.setColor(new Color(
                    100 + random.nextInt(80),
                    100 + random.nextInt(80),
                    100 + random.nextInt(80),
                    50 + random.nextInt(60)));
            g.setStroke(new BasicStroke(0.8f + random.nextFloat()));
            g.drawLine(
                    random.nextInt(IMAGE_WIDTH), random.nextInt(IMAGE_HEIGHT),
                    random.nextInt(IMAGE_WIDTH), random.nextInt(IMAGE_HEIGHT));
        }
        g.setStroke(new BasicStroke(1));
    }

    /**
     * Encodes a BufferedImage to a Base64 PNG string.
     */
    static String encodeToBase64Png(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode keyboard image to PNG", e);
        }
    }

    /**
     * Serializes a coordinate map to a compact string for storage in auth notes.
     * Format: "digit:x,y,w,h;digit:x,y,w,h;..."
     */
    public static String serializeCoordinateMap(Map<String, int[]> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, int[]> entry : map.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            int[] coords = entry.getValue();
            sb.append(entry.getKey())
              .append(':')
              .append(coords[0]).append(',')
              .append(coords[1]).append(',')
              .append(coords[2]).append(',')
              .append(coords[3]);
        }
        return sb.toString();
    }

    /**
     * Deserializes a coordinate map from the compact string format.
     *
     * @param serialized string in format "digit:x,y,w,h;digit:x,y,w,h;..."
     * @return map of digit → [x, y, w, h]
     */
    public static Map<String, int[]> deserializeCoordinateMap(String serialized) {
        Map<String, int[]> map = new HashMap<>();
        if (serialized == null || serialized.isEmpty()) {
            return map;
        }
        String[] entries = serialized.split(";");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length != 2) continue;
            String digit = parts[0];
            String[] coords = parts[1].split(",");
            if (coords.length != 4) continue;
            map.put(digit, new int[]{
                    Integer.parseInt(coords[0]),
                    Integer.parseInt(coords[1]),
                    Integer.parseInt(coords[2]),
                    Integer.parseInt(coords[3])
            });
        }
        return map;
    }

    /**
     * Resolves a click coordinate to a digit using the stored coordinate map.
     *
     * @param x             click X coordinate
     * @param y             click Y coordinate
     * @param coordinateMap digit → [x, y, w, h] map
     * @return the digit string, or null if no hit
     */
    public static String resolveDigit(int x, int y, Map<String, int[]> coordinateMap) {
        for (Map.Entry<String, int[]> entry : coordinateMap.entrySet()) {
            int[] box = entry.getValue();
            if (x >= box[0] && x < box[0] + box[2] &&
                y >= box[1] && y < box[1] + box[3]) {
                return entry.getKey();
            }
        }
        return null;
    }
}
