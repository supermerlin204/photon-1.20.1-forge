package com.lowdragmc.photon.util;

import com.lowdragmc.lowdraglib2.syncdata.IProviderAwareNBTSerializable;
import net.minecraft.nbt.Tag;

/** Bridges provider-aware 1.21 serialization to Forge 1.20.1's provider-less contract. */
public interface RegistryAwareNBTSerializable<T extends Tag> extends IProviderAwareNBTSerializable<T> {
}
