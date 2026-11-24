package xyz.mcutils.backend.common.renderer.raster;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Fast software rasterizer for textured quads. Scanline-based: for each row,
 * intersect with parallelogram edges, interpolate (u,v) along the span. Matches
 * Graphics2D.drawImage( AffineTransform ) output with less work than per-pixel inverse.
 */
public final class QuadRasterizer {
    /**
     * Rasterizes a textured parallelogram with nearest-texel sampling at pixel centers.
     * (dx0,dy0) maps to texture (texX0,texY0), (dx1,dy1) to (texX0+tw,texY0) and (dx2,dy2) to (texX0,texY0+th).
     * A pixel is written only when its depth is strictly closer than the z-buffer; opaque pixels update the
     * z-buffer, semi-transparent ones are blended without owning depth so geometry behind them still composites.
     *
     * @param zBuffer    depth buffer the size of outPixels, larger (less negative) is closer
     * @param d0         depth at vertex (dx0,dy0)
     * @param d1         depth at vertex (dx1,dy1)
     * @param d2         depth at vertex (dx2,dy2)
     * @param brightness face shading factor in [0, 1]
     */
    public static void rasterizeQuad(int[] outPixels, float[] zBuffer, int outW, int outH, double dx0, double dy0, double dx1, double dy1, double dx2, double dy2, double d0, double d1, double d2, int texX0, int texY0, int tw, int th, int[] texPixels, int texW, float brightness) {
        double e1x = dx1 - dx0, e1y = dy1 - dy0;
        double e2x = dx2 - dx0, e2y = dy2 - dy0;
        double det = e1x * e2y - e1y * e2x;
        if (Math.abs(det) < 1e-9) {
            return;
        }

        double dx3 = dx1 + dx2 - dx0;
        double dy3 = dy1 + dy2 - dy0;
        int yMin = Math.max(0, (int) Math.ceil(Math.min(Math.min(dy0, dy1), Math.min(dy2, dy3)) - 0.5));
        int yMax = Math.min(outH - 1, (int) Math.floor(Math.max(Math.max(dy0, dy1), Math.max(dy2, dy3)) - 0.5));
        if (yMin > yMax) {
            return;
        }

        // The quad is a parallelogram, so texture coordinates and depth are affine in screen space.
        double invDet = 1.0 / det;
        double dd1 = d1 - d0, dd2 = d2 - d0;
        double dudx = e2y * invDet * tw, dudy = -e2x * invDet * tw;
        double dvdx = -e1y * invDet * th, dvdy = e1x * invDet * th;
        double dzdx = (dd1 * e2y - dd2 * e1y) * invDet, dzdy = (dd2 * e1x - dd1 * e2x) * invDet;

        int maxU = tw - 1;
        int maxV = th - 1;
        boolean shade = brightness < 1.0f;

        for (int y = yMin; y <= yMax; y++) {
            double py = y + 0.5;
            double xMin = Double.POSITIVE_INFINITY;
            double xMax = Double.NEGATIVE_INFINITY;
            double ax = dx0, ay = dy0;
            for (int e = 0; e < 4; e++) {
                double bx, by;
                switch (e) {
                    case 0 -> { bx = dx1; by = dy1; }
                    case 1 -> { bx = dx3; by = dy3; }
                    case 2 -> { bx = dx2; by = dy2; }
                    default -> { bx = dx0; by = dy0; }
                }
                if (Math.abs(by - ay) >= 1e-9 && py >= Math.min(ay, by) - 1e-6 && py <= Math.max(ay, by) + 1e-6) {
                    double x = ax + (py - ay) / (by - ay) * (bx - ax);
                    if (x < xMin) {
                        xMin = x;
                    }
                    if (x > xMax) {
                        xMax = x;
                    }
                }
                ax = bx;
                ay = by;
            }
            if (xMin > xMax) {
                continue;
            }

            int xStart = Math.max(0, (int) Math.ceil(xMin - 0.5));
            int xEnd = Math.min(outW - 1, (int) Math.floor(xMax - 0.5));
            if (xStart > xEnd) {
                int xMid = (int) Math.round((xMin + xMax) / 2);
                if (xMid < 0 || xMid >= outW) {
                    continue;
                }
                xStart = xMid;
                xEnd = xMid;
            }

            double offX = xStart + 0.5 - dx0;
            double offY = py - dy0;
            double u = dudx * offX + dudy * offY;
            double v = dvdx * offX + dvdy * offY;
            double z = d0 + dzdx * offX + dzdy * offY;

            int row = y * outW;
            for (int x = xStart; x <= xEnd; x++, u += dudx, v += dvdx, z += dzdx) {
                int dstIdx = row + x;
                float pixelDepth = (float) z;
                if (pixelDepth <= zBuffer[dstIdx]) {
                    continue;
                }

                int iu = Math.min(maxU, Math.max(0, (int) u));
                int iv = Math.min(maxV, Math.max(0, (int) v));
                int pixel = texPixels[(texY0 + iv) * texW + texX0 + iu];
                int a = pixel >>> 24;
                if (a == 0) {
                    continue;
                }

                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                if (shade) {
                    r = (int) (r * brightness);
                    g = (int) (g * brightness);
                    b = (int) (b * brightness);
                }

                if (a >= 254) {
                    outPixels[dstIdx] = (a << 24) | (r << 16) | (g << 8) | b;
                    zBuffer[dstIdx] = pixelDepth;
                } else {
                    int dst = outPixels[dstIdx];
                    int inv = 255 - a;
                    r = (r * a + ((dst >> 16) & 0xFF) * inv) / 255;
                    g = (g * a + ((dst >> 8) & 0xFF) * inv) / 255;
                    b = (b * a + (dst & 0xFF) * inv) / 255;
                    int outA = Math.min(255, a + inv * (dst >>> 24) / 255);
                    outPixels[dstIdx] = (outA << 24) | (r << 16) | (g << 8) | b;
                }
            }
        }
    }

    /**
     * Get texture pixels as int[]. Uses DataBufferInt when available for zero-copy.
     */
    public static int[] getTexturePixels(BufferedImage texture) {
        if (texture.getRaster().getDataBuffer() instanceof DataBufferInt db) {
            return db.getData();
        }
        // Fallback: copy via getRGB
        int w = texture.getWidth();
        int h = texture.getHeight();
        return texture.getRGB(0, 0, w, h, null, 0, w);
    }

}
