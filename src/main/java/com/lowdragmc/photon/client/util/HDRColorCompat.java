package com.lowdragmc.photon.client.util;

import com.lowdragmc.lowdraglib2.utils.LDLibExtraCodecs;
import com.mojang.serialization.Codec;
import net.minecraft.util.Mth;
import org.joml.Vector4f;

/** Conversion helpers for LDLib2 1.20.1's legacy HDR vector: base RGB in xyz, intensity in w. */
public final class HDRColorCompat {
    public static final Codec<Vector4f> CODEC = LDLibExtraCodecs.VECTOR4F;

    private HDRColorCompat() {
    }

    public static Vector4f white() {
        return new Vector4f(1f, 1f, 1f, 1f);
    }

    public static Vector4f black() {
        return new Vector4f(0f, 0f, 0f, 1f);
    }

    public static Vector4f fromARGB(int argb) {
        return new Vector4f(
                ((argb >>> 16) & 0xFF) / 255f,
                ((argb >>> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f,
                1f);
    }

    public static Vector4f fromPremultiplied(Vector4f value) {
        return fromPremultiplied(value.x, value.y, value.z);
    }

    public static Vector4f fromPremultiplied(float r, float g, float b) {
        float intensity = Math.max(r, Math.max(g, b));
        if (intensity > 1f) {
            return new Vector4f(r / intensity, g / intensity, b / intensity, intensity);
        }
        return new Vector4f(r, g, b, 1f);
    }

    public static Vector4f withIntensity(Vector4f value, float intensity) {
        return new Vector4f(value.x, value.y, value.z, Math.max(0f, intensity));
    }

    public static Vector4f premultiplied(Vector4f value) {
        return new Vector4f(value.x * value.w, value.y * value.w, value.z * value.w, 1f);
    }

    public static int baseARGB(Vector4f value) {
        return pack(value.x, value.y, value.z);
    }

    public static int toARGB(Vector4f value) {
        return pack(value.x * value.w, value.y * value.w, value.z * value.w);
    }

    private static int pack(float r, float g, float b) {
        return 0xFF000000
                | (Mth.clamp(Math.round(r * 255f), 0, 255) << 16)
                | (Mth.clamp(Math.round(g * 255f), 0, 255) << 8)
                | Mth.clamp(Math.round(b * 255f), 0, 255);
    }
}
