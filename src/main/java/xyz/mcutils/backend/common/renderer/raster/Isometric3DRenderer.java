package xyz.mcutils.backend.common.renderer.raster;

import xyz.mcutils.backend.common.math.Vector3;
import xyz.mcutils.backend.common.math.Vector3Utils;
import xyz.mcutils.backend.common.renderer.IsometricLighting;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.List;

/**
 * 3D isometric renderer: given textures and faces, rotates by yaw/pitch,
 * orthographically projects, depth-sorts, and draws quads.
 * Supports multiple textures (e.g. skin 64×64). Used by full-body and head renderers.
 */
public class Isometric3DRenderer {
    public static final Isometric3DRenderer INSTANCE = new Isometric3DRenderer();

    private static final int FACE_STRIDE = 10;

    /**
     * Renders the given textured face batches with the given view onto an image.
     *
     * @param batches list of (texture, faces) — e.g. skin 64×64
     * @param view    view parameters (eye, target, yaw, pitch, aspect ratio)
     * @param size    output height in pixels; width = size * aspectRatio
     * @return the rendered image
     */
    public BufferedImage render(List<TexturedFaces> batches, ViewParams view, int size) {
        int width = (int) Math.round(size * view.aspectRatio());

        Vector3 eye = view.eye();
        Vector3 target = view.target();
        Vector3 fwd = Vector3Utils.normalize(target.subtract(eye));
        Vector3 right = Vector3Utils.normalize(Vector3Utils.cross(new Vector3(0, 1, 0), fwd));
        Vector3 up = Vector3Utils.normalize(Vector3Utils.cross(fwd, right));

        // Rotation (yaw about Y, then pitch about X) composed with the view basis, so each vertex is a single
        // matrix multiply. Rows give view X (right), view Y (up) and depth (-forward).
        double yaw = Math.toRadians(view.yawDeg());
        double pitch = Math.toRadians(view.pitchDeg());
        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double[] rot = {
                cy, 0, -sy,
                -sp * sy, cp, -sp * cy,
                cp * sy, sp, cp * cy
        };
        double[] mx = viewRow(right.x(), right.y(), right.z(), rot);
        double[] my = viewRow(up.x(), up.y(), up.z(), rot);
        double[] mz = viewRow(-fwd.x(), -fwd.y(), -fwd.z(), rot);
        double tx = target.x() - eye.x(), ty = target.y() - eye.y(), tz = target.z() - eye.z();
        double cx = right.x() * tx + right.y() * ty + right.z() * tz;
        double cyv = up.x() * tx + up.y() * ty + up.z() * tz;
        double czv = -(fwd.x() * tx + fwd.y() * ty + fwd.z() * tz);
        // A face is front-facing when its rotated normal points against the view direction.
        double[] facing = viewRow(fwd.x(), fwd.y(), fwd.z(), rot);
        double[] sun = {IsometricLighting.SUN_DIRECTION.x(), IsometricLighting.SUN_DIRECTION.y(), IsometricLighting.SUN_DIRECTION.z()};
        double[] sunLocal = viewRow(sun[0], sun[1], sun[2], rot);

        int totalFaces = 0;
        for (TexturedFaces batch : batches) {
            totalFaces += batch.faces().size();
        }
        // Per face: x0 y0 x1 y1 x2 y2 pd0 pd1 pd2 brightness
        double[] data = new double[totalFaces * FACE_STRIDE];
        double[] depth = new double[totalFaces];
        int[] batchOf = new int[totalFaces];
        Face[] faceOf = new Face[totalFaces];
        int[] order = new int[totalFaces];
        int visible = 0;

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        int faceIndex = 0;
        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            for (Face face : batches.get(batchIndex).faces()) {
                int base = faceIndex * FACE_STRIDE;
                double x = 0, y = 0, z = 0;
                Vector3[] vs = {face.v0(), face.v1(), face.v2(), face.v3()};
                double depthSum = 0;
                for (int i = 0; i < 4; i++) {
                    Vector3 v = vs[i];
                    double px = v.x() - target.x(), py = v.y() - target.y(), pz = v.z() - target.z();
                    x = mx[0] * px + mx[1] * py + mx[2] * pz + cx;
                    y = my[0] * px + my[1] * py + my[2] * pz + cyv;
                    z = mz[0] * px + mz[1] * py + mz[2] * pz + czv;
                    depthSum += z;
                    if (i < 3) {
                        data[base + i * 2] = x;
                        data[base + i * 2 + 1] = y;
                        data[base + 6 + i] = z;
                    }
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }

                Vector3 n = face.normal();
                double nLen = Math.sqrt(n.x() * n.x() + n.y() * n.y() + n.z() * n.z());
                if (nLen < 1e-10) {
                    nLen = 1;
                }
                if ((facing[0] * n.x() + facing[1] * n.y() + facing[2] * n.z()) / nLen < 1e-9) {
                    double lit = (sunLocal[0] * n.x() + sunLocal[1] * n.y() + sunLocal[2] * n.z()) / nLen;
                    data[base + 9] = Math.max(0, Math.min(1, IsometricLighting.MIN_BRIGHTNESS + (1.0 - IsometricLighting.MIN_BRIGHTNESS) * (1.0 + lit) * 0.5));
                    depth[faceIndex] = depthSum / 4.0;
                    order[visible++] = faceIndex;
                }
                batchOf[faceIndex] = batchIndex;
                faceOf[faceIndex] = face;
                faceIndex++;
            }
        }

        // Back-to-front (painter's) order: depths are negative (-(fwd·(world-eye))), so ascending puts the
        // farthest faces first. Insertion sort is stable, keeping coplanar overlay faces after their base layer.
        for (int i = 1; i < visible; i++) {
            int cur = order[i];
            double d = depth[cur];
            int j = i - 1;
            while (j >= 0 && depth[order[j]] > d) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = cur;
        }

        double modelW = Math.max(1, maxX - minX);
        double modelH = Math.max(1, maxY - minY);
        double scale = Math.min(width / modelW, size / modelH);
        double offsetX = (width - modelW * scale) / 2 - minX * scale;
        double offsetY = maxY * scale;

        BufferedImage result = new BufferedImage(width, size, BufferedImage.TYPE_INT_ARGB);
        int[] outPixels = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();
        // Depths are negative, so closer to the camera means larger; -∞ lets any real depth win.
        float[] zBuffer = new float[width * size];
        Arrays.fill(zBuffer, Float.NEGATIVE_INFINITY);

        int[][] batchTexPixels = new int[batches.size()][];
        int[] batchTexW = new int[batches.size()];
        int[] batchTexH = new int[batches.size()];
        for (int i = 0; i < batches.size(); i++) {
            BufferedImage texture = batches.get(i).texture();
            batchTexPixels[i] = QuadRasterizer.getTexturePixels(texture);
            batchTexW[i] = texture.getWidth();
            batchTexH[i] = texture.getHeight();
        }

        for (int k = 0; k < visible; k++) {
            int idx = order[k];
            int base = idx * FACE_STRIDE;
            Face face = faceOf[idx];
            int batch = batchOf[idx];
            int texW = batchTexW[batch];
            int texH = batchTexH[batch];

            // Texture subrect: floor(u0), floor(v0), ceil(u1) - floor(u0), ceil(v1) - floor(v0)
            int sx1 = Math.max(0, Math.min((int) Math.floor(face.u0()), texW - 1));
            int sy1 = Math.max(0, Math.min((int) Math.floor(face.v0_()), texH - 1));
            int sx2 = Math.max(sx1 + 1, Math.min((int) Math.ceil(face.u1()), texW));
            int sy2 = Math.max(sy1 + 1, Math.min((int) Math.ceil(face.v1_()), texH));

            QuadRasterizer.rasterizeQuad(outPixels, zBuffer, width, size,
                    data[base] * scale + offsetX, offsetY - data[base + 1] * scale,
                    data[base + 2] * scale + offsetX, offsetY - data[base + 3] * scale,
                    data[base + 4] * scale + offsetX, offsetY - data[base + 5] * scale,
                    data[base + 6], data[base + 7], data[base + 8],
                    sx1, sy1, sx2 - sx1, sy2 - sy1, batchTexPixels[batch], texW, (float) data[base + 9]);
        }
        return result;
    }

    private static double[] viewRow(double ax, double ay, double az, double[] rot) {
        return new double[]{
                ax * rot[0] + ay * rot[3] + az * rot[6],
                ax * rot[1] + ay * rot[4] + az * rot[7],
                ax * rot[2] + ay * rot[5] + az * rot[8]
        };
    }

    /**
     * Renders faces with a single texture. Convenience for skin-only rendering.
     *
     * @param texture the texture (e.g. 64×64 skin)
     * @param faces   the list of textured faces
     * @param view    view parameters
     * @param size    output height in pixels
     * @return the rendered image
     */
    public BufferedImage render(BufferedImage texture, List<Face> faces, ViewParams view, int size) {
        return render(List.of(new TexturedFaces(texture, faces)), view, size);
    }

    /**
     * View parameters for the 3D isometric renderer.
     * The target is also used as the model rotation center.
     */
    public record ViewParams(Vector3 eye, Vector3 target, double yawDeg, double pitchDeg, double aspectRatio) {}

    /**
     * Pairs a texture with its faces. Used for multi-texture rendering.
     */
    public record TexturedFaces(BufferedImage texture, List<Face> faces) {}
}
