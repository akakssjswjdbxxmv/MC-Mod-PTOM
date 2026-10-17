package com.lege.client.material;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 单条材料的不可变快照。
 * stack 已做防御性拷贝，可安全持有与显示。
 */
public record MaterialEntrySnapshot(
        ResourceLocation itemId,
        ItemStack stack,
        int total,
        int available,
        int missing,
        int mismatched
) {
}
