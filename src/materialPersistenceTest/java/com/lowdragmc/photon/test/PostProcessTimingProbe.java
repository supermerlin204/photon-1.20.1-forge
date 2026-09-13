package com.lowdragmc.photon.test;

import com.lowdragmc.photon.client.fx.*;
import com.lowdragmc.photon.client.fx.timeline.*;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.postfx.runtime.*;
import net.minecraft.client.particle.*;
import java.util.*;

/** Requests must exist before root.frame, independent of particle queue order. */
final class PostProcessTimingProbe {
    static void verify() throws Exception {
        var data = new FXData();
        var track = new PostProcessTrack();
        var path = new com.lowdragmc.lowdraglib2.editor.resource.BuiltinPath("timing:test");
        track.clips().add(new PostProcessClip(0, 1, 1).effect(path.getPathWithType()));
        data.timeline().tracks().add(track);
        var runtime = new FXRuntime(data);
        var sink = new PostEffectStack();
        var executor = new IEffectExecutor() {
            public net.minecraft.world.level.Level getLevel() { return null; }
            public PostEffectStack postEffectSink() { return sink; }
        };
        var field = PostEffectStack.class.getDeclaredField("requests");
        field.setAccessible(true);
        var requests = (List<?>) field.get(sink);
        Map<ParticleRenderType, Queue<Particle>> host = new HashMap<>();
        host.put(FXObject.NO_RENDER_RENDER_TYPE, new ArrayDeque<>(List.of((Particle) runtime.root)));
        runtime.timelinePlayer.begin(executor);
        FXPostProcessPreparation.prepare(host, .5f);
        require(requests.size() == 1, "short clip missing before root draw");
        FXPostProcessPreparation.prepare(host, .5f);
        runtime.timelinePlayer.frame(.5f);
        require(requests.size() == 1, "duplicate request from second pass/root");
        sink.onFrameEnd();
        PostFXTargetPool.endFrame();
        runtime.timelinePlayer.tick();
        runtime.timelinePlayer.tick();
        FXPostProcessPreparation.prepare(host, .5f);
        require(requests.isEmpty(), "expired clip extended/replayed");
        runtime.timelinePlayer.begin(executor);
        runtime.root.setDelay(2);
        FXPostProcessPreparation.prepare(host, .5f);
        require(requests.isEmpty(), "start delay ignored");
        runtime.root.setDelay(0);
        FXPostProcessPreparation.prepare(host, .5f);
        require(requests.size() == 1, "delay suppressed valid submission");
        sink.onFrameEnd();
        runtime.destroy(true);
        PostFXTargetPool.endFrame();
        FXPostProcessPreparation.prepare(host, .5f);
        require(requests.isEmpty(), "destroyed runtime submitted");
        runtime.timelinePlayer.begin(executor);
        track.mute(true);
        FXPostProcessPreparation.prepare(host, .5f);
        require(requests.isEmpty(), "muted track submitted");
        track.mute(false);
        runtime.timelinePlayer.begin(executor);
        host.clear();
        FXPostProcessPreparation.prepare(host, .5f);
        require(requests.isEmpty(), "unregistered runtime submitted");
        require(!PostEffectStack.GLOBAL.hasPending() && !PostEffectStack.EDITOR_SCENE.hasPending(), "sink leaked");
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
