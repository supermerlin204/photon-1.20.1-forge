package com.lowdragmc.photon.client;

import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.WholeFXEffectExecutor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

/** Development item for validating whole-FX rotation with the exported blade effect. */
@OnlyIn(Dist.CLIENT)
public class BladeTestItem extends Item {
    public BladeTestItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            FX fx = FXHelper.getFX(ResourceLocation.fromNamespaceAndPath(Photon.MOD_ID, "blade"));
            if (fx != null) {
                // Minecraft's positive entity yaw is opposite to the FX world-Y convention.
                // Negating it keeps the slash facing the player for every horizontal view angle.
                var eulerDegrees = new Vector3f(0, -player.getYRot(), 0);
                var effect = new WholeFXEffectExecutor(fx, level, player, eulerDegrees,
                        WholeFXEffectExecutor.RotationMode.LOCAL);
                effect.start();
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
