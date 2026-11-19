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

/**
 * Bindings for NAPA's AprilTag detector (https://github.com/zwatx/NAPA). Load the napa_jni library
 * before calling them. A handle is not thread-safe; use one per camera.
 */
public class NapaJNI {
    /** Values per tag in {@link #detect}'s result: ID, hamming, decision margin, 8 corner values. */
    public static final int kValuesPerDetection = 11;

    /** {@link #setLens}'s model for no lens: tag sides are fitted in pixels. */
    public static final int kLensNone = 0;

    /** {@link #setLens}'s model for OpenCV's lens model (Brown-Conrady). */
    public static final int kLensBrownConrady = 1;

    /** {@link #setLens}'s model for OpenCV's fisheye lens model (cv::fisheye). */
    public static final int kLensFisheye = 2;

    private NapaJNI() {}

    /**
     * Creates a detector for one tag family.
     *
     * @param family the family's name, such as "tag36h11" or "tag16h5"
     * @return a handle, or 0 if the family is unknown or memory runs out
     */
    public static native long create(String family);

    /**
     * The number of payload bits NAPA corrects by default for a family: as unlikely to accept a
     * chance match as tag36h11 with 3.
     *
     * @param family the family's name, such as "tag36h11"
     * @return the bit count, or -1 if the family is unknown
     */
    public static native int recommendedHamming(String family);

    /**
     * Frees a detector and its worker threads.
     *
     * @param handle a handle from {@link #create}, or 0
     */
    public static native void destroy(long handle);

    /**
     * Sets how many threads one {@link #detect} call may use: the calling thread plus {@code threads
     * - 1} workers the detector owns. Results are the same at every count; only latency changes.
     *
     * @param handle a handle from {@link #create}
     * @param threads 1 to 16
     * @return 0, -1 if the count is invalid, or -2 if the threads couldn't be started (the previous
     *     setting stays)
     */
    public static native int setThreads(long handle, int threads);

    /**
     * Sets the camera's lens from its calibration, in OpenCV's conventions. Tag sides are then fitted
     * as straight lines through the lens, and corners mapped back to pixels; the image isn't
     * remapped.
     *
     * @param handle a handle from {@link #create}
     * @param model {@link #kLensNone} to clear it, {@link #kLensBrownConrady} or {@link
     *     #kLensFisheye}
     * @param intrinsics fx, fy, cx, cy, skew; may be null with {@link #kLensNone}
     * @param coefficients the distortion coefficients: 4, 5, 8, 12 or 14 of OpenCV's (tilt terms 0)
     *     for {@link #kLensBrownConrady}, or cv::fisheye's 4 for {@link #kLensFisheye}; may be null
     *     with {@link #kLensNone}
     * @return 0, or -1 if the lens is invalid
     */
    public static native int setLens(
            long handle, int model, double[] intrinsics, double[] coefficients);

    /**
     * Detects tags in an 8-bit grayscale image.
     *
     * @param handle a handle from {@link #create}
     * @param dataAddr the address of the image's first pixel
     * @param width the image's width, in pixels
     * @param height the image's height, in pixels
     * @param stride bytes from one row to the next
     * @param minTagPx the smallest black-square side, in pixels, NAPA is guaranteed to find (8 to
     *     30000)
     * @param maxHamming payload bits to correct (0 to 3), capped by the family
     * @param minContrast grey levels between a tag's black and its white quiet zone (1 to 255)
     * @param minBorderCells dark border cells required, out of 28
     * @param sharpeningX100 cell sharpening strength times 100 (0 to 100); upstream AprilTag uses 25
     * @return {@link #kValuesPerDetection} values per tag, its corners as x, y pairs in OpenCV's
     *     order with (0, 0) at the center of the top-left pixel; or null if the image or parameters
     *     are invalid, or NAPA ran out of memory
     */
    public static native double[] detect(
            long handle,
            long dataAddr,
            int width,
            int height,
            int stride,
            int minTagPx,
            int maxHamming,
            int minContrast,
            int minBorderCells,
            int sharpeningX100);
}
