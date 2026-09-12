package com.lowdragmc.photon.client.gameobject.particle.renderer;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Pure shared CPU/GPU stretched geometry calculation; inputs must share rendered world space. */
public final class StretchedBillboardMath {
    private StretchedBillboardMath() {}
    public record Frame(Quaternionf rotation, float stretchedSizeX, float offsetX, float offsetY, float offsetZ) {}
    public static Frame compute(Vector3f vel, Vector3f worldPos, double camX, double camY, double camZ,
                                Vector3f size, Vector3f spaceScale, float lengthScale, float velocityScale) {
        float speed = vel.length();

        Vector3f right = new Vector3f();
        if (speed > 1e-5f) {
            right.set(vel).div(speed);
        } else {
            right.set(1, 0, 0);
        }

        Vector3f dirToCam = new Vector3f((float) (camX - worldPos.x), (float) (camY - worldPos.y), (float) (camZ - worldPos.z));
        if (dirToCam.lengthSquared() > 1e-5f) {
            dirToCam.normalize();
        } else {
            dirToCam.set(0, 0, 1);
        }

        Vector3f up = new Vector3f();
        dirToCam.cross(right, up);

        if (up.lengthSquared() < 1e-5f) {
            if (Math.abs(right.y) > 0.99f) {
                up.set(0, 0, 1).cross(right).normalize();
            } else {
                up.set(0, 1, 0).cross(right).normalize();
            }
        } else {
            up.normalize();
        }

        Vector3f forward = new Vector3f();
        right.cross(up, forward).normalize();

        Matrix3f mat = new Matrix3f(
                right.x,   right.y,   right.z,
                up.x,      up.y,      up.z,
                forward.x, forward.y, forward.z
        );
        var quaternion = new Quaternionf().setFromNormalized(mat);

        float stretch = lengthScale + speed * velocityScale;
        float stretchedSizeX = size.x * stretch;

        float offsetAmount = (stretchedSizeX - size.x) * spaceScale.x;
        return new Frame(quaternion, stretchedSizeX,
                right.x * offsetAmount, right.y * offsetAmount, right.z * offsetAmount);
    }
}

