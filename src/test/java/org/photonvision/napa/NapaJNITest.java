/*
 * Copyright (C) Photon Vision.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.photonvision.napa;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NapaJNITest {
    // NAPA's tag36h11 codeword for ID 0: data cells row-major, a set bit for a white cell
    private static final long kTag0 = 0x21a146babL;

    // The tag is drawn kCell pixels per cell with its black square's top-left pixel at
    // (kLeft, kTop), in an image whose rows are padded past its width
    private static final int kCell = 12;
    private static final int kLeft = 40;
    private static final int kTop = 32;
    private static final int kWidth = 200;
    private static final int kHeight = 160;
    private static final int kStride = 208;
    private static final int kMinTagPx = 16;

    private Arena m_arena;
    private MemorySegment m_image;
    private long m_handle;

    @BeforeAll
    public static void loadLibrary() {
        String dir =
                Objects.requireNonNull(
                        System.getProperty("napa.libraryDir"),
                        "Set napa.libraryDir to the folder holding napa_jni (Gradle's test task does)");
        System.load(Path.of(dir, System.mapLibraryName("napa_jni")).toAbsolutePath().toString());
    }

    @BeforeEach
    public void setup() {
        m_arena = Arena.ofConfined();
        m_image = drawTag(0);
        m_handle = NapaJNI.create("tag36h11");
        assertNotEquals(0, m_handle);
    }

    @AfterEach
    public void teardown() {
        NapaJNI.destroy(m_handle);
        m_arena.close();
    }

    // Draws the tag turned clockwise by quarterTurns quarter turns
    private MemorySegment drawTag(int quarterTurns) {
        MemorySegment image = m_arena.allocate((long) kStride * kHeight);
        image.fill((byte) 0); // padding past each row's width stays black
        for (int y = 0; y < kHeight; y++) {
            for (int x = 0; x < kWidth; x++) {
                int cellX = Math.floorDiv(x - kLeft, kCell);
                int cellY = Math.floorDiv(y - kTop, kCell);
                boolean white;
                if (cellX < 0 || cellX > 7 || cellY < 0 || cellY > 7) {
                    white = true; // around the tag
                } else if (cellX == 0 || cellX == 7 || cellY == 0 || cellY == 7) {
                    white = false; // the black border
                } else {
                    // The tag's own cell at this image cell
                    int tagX = cellX;
                    int tagY = cellY;
                    for (int turn = 0; turn < quarterTurns; turn++) {
                        int x0 = tagX;
                        tagX = tagY;
                        tagY = 7 - x0;
                    }
                    int bit = 6 * (tagY - 1) + (tagX - 1);
                    white = ((kTag0 >> (35 - bit)) & 1) != 0;
                }
                image.set(ValueLayout.JAVA_BYTE, (long) y * kStride + x, (byte) (white ? 255 : 0));
            }
        }
        return image;
    }

    private double[] detect(long handle) {
        return detect(handle, m_image.address(), kMinTagPx);
    }

    // NAPA's defaults for the other parameters, as PhotonVision passes them
    private static double[] detect(long handle, long dataAddr, int minTagPx) {
        return NapaJNI.detect(handle, dataAddr, kWidth, kHeight, kStride, minTagPx, 3, 20, 22, 25);
    }

    // Asserts one tag 0 whose corners are the black square's, in OpenCV's order (the tag's own
    // top-left, top-right, bottom-right, bottom-left) for a tag turned quarterTurns clockwise, with
    // (0, 0) at the top-left pixel's center as NapaJNI documents
    private static void assertTag0(double[] result, int quarterTurns) {
        assertEquals(NapaJNI.kValuesPerDetection, result.length);
        assertEquals(0, result[0]); // ID
        assertEquals(0, result[1]); // hamming
        double low = -0.5;
        double high = 8 * kCell - 0.5;
        // The image's top-left, top-right, bottom-right and bottom-left corners of the square
        double[][] square = {{low, low}, {high, low}, {high, high}, {low, high}};
        for (int k = 0; k < 4; k++) {
            double[] expected = square[(k + quarterTurns) % 4];
            double x = result[3 + 2 * k] - kLeft;
            double y = result[4 + 2 * k] - kTop;
            assertTrue(
                    Math.hypot(x - expected[0], y - expected[1]) < 0.5,
                    "corner " + k + " is at (" + x + ", " + y + ") with " + quarterTurns + " turns");
        }
    }

    @Test
    public void testFindsATagInEveryOrientation() {
        for (int quarterTurns = 0; quarterTurns < 4; quarterTurns++) {
            m_image = drawTag(quarterTurns);
            assertTag0(detect(m_handle), quarterTurns);
        }
    }

    @Test
    public void testThreadsFindTheSameTag() {
        double[] serial = detect(m_handle);
        assertEquals(0, NapaJNI.setThreads(m_handle, 4));
        assertArrayEquals(serial, detect(m_handle));
    }

    @Test
    public void testLens() {
        double[] plain = detect(m_handle);
        double[] intrinsics = {300, 300, kWidth / 2.0, kHeight / 2.0, 0};
        // An undistorted lens fits sides as pixels do
        assertEquals(
                0, NapaJNI.setLens(m_handle, NapaJNI.kLensBrownConrady, intrinsics, new double[5]));
        double[] throughLens = detect(m_handle);
        for (int i = 0; i < plain.length; i++) {
            assertEquals(plain[i], throughLens[i], 1e-6);
        }
        assertEquals(0, NapaJNI.setLens(m_handle, NapaJNI.kLensNone, null, null));
        assertArrayEquals(plain, detect(m_handle));

        assertEquals(
                -1, NapaJNI.setLens(m_handle, NapaJNI.kLensBrownConrady, intrinsics, new double[3]));
        assertEquals(
                -1, NapaJNI.setLens(m_handle, NapaJNI.kLensBrownConrady, intrinsics, new double[15]));
        assertEquals(-1, NapaJNI.setLens(m_handle, NapaJNI.kLensBrownConrady, new double[4], null));
    }

    @Test
    public void testInvalidInput() {
        assertNull(detect(m_handle, m_image.address(), 0));
        assertNull(detect(0, m_image.address(), kMinTagPx));
        assertNull(detect(m_handle, 0, kMinTagPx));
        assertEquals(0, NapaJNI.create("tag99h99"));
        assertEquals(0, NapaJNI.create(null));
        assertEquals(-1, NapaJNI.setThreads(0, 2));
        assertEquals(-1, NapaJNI.setThreads(m_handle, 0));
        assertEquals(-1, NapaJNI.setThreads(m_handle, 17));
    }

    @Test
    public void testRecommendedHamming() {
        assertEquals(3, NapaJNI.recommendedHamming("tag36h11"));
        assertEquals(-1, NapaJNI.recommendedHamming("tag99h99"));
        assertEquals(-1, NapaJNI.recommendedHamming(null));
    }
}
