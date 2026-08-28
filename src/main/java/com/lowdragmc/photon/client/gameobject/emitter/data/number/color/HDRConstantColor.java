package com.lowdragmc.photon.client.gameobject.emitter.data.number.color;

import com.lowdragmc.lowdraglib2.configurator.ui.HDRColorConfigurator;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunctionConfig;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.configurator.NumberFunctionConfigurator;
import com.lowdragmc.photon.client.util.HDRColorCompat;
import lombok.Getter;
import org.joml.Vector4f;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * The HDR counterpart of {@link Color}, using LDLib2's RGB + intensity vector representation.
 *
 * <p>Deliberately not derived from {@code Constant} — its {@code @Persisted Number} can't hold an HDR
 * colour, and inheriting it would also make this droppable onto every scalar field (the drag predicate
 * keys off the configurator's default value type, which is a {@code Constant}).
 */
@LDLRegisterClient(name = "hdr_color", registry = "photon:number_function")
public class HDRConstantColor implements HDRColorFunction {

    @Getter
    @Persisted
    private Vector4f color;

    public HDRConstantColor() {
        this(HDRColorCompat.white());
    }

    public HDRConstantColor(Vector4f color) {
        this.color = color;
    }

    public void setColor(Vector4f color) {
        this.color = color == null ? HDRColorCompat.white() : color;
    }

    @Override
    public void loadConfig(NumberFunctionConfig config) {
        this.color = HDRColorCompat.fromARGB((int) config.defaultValue());
    }

    @Override
    public void sampleHDR(float t, Supplier<Float> lerp, Vector4f out) {
        out.set(color.x * color.w, color.y * color.w, color.z * color.w, 1f);
    }

    @Override
    public NumberFunction copy() {
        return new HDRConstantColor(new Vector4f(color));
    }

    @Override
    public void createConfigurator(NumberFunctionConfigurator configurator) {
        configurator.inlineContainer.addChildren(new HDRColorConfigurator("", () -> color, hdr -> {
            setColor(hdr);
            configurator.updateValue(this);
        }, new Vector4f(color), true));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        return obj instanceof HDRConstantColor other && Objects.equals(color, other.color);
    }

    @Override
    public int hashCode() {
        return Objects.hash(color);
    }
}
