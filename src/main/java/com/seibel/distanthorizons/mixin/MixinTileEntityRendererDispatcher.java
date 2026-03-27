package com.seibel.distanthorizons.mixin;

import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * Fixes a {@link java.util.ConcurrentModificationException} in
 * {@link TileEntityRendererDispatcher} that occurs when DH's LOD builder
 * threads call block rendering APIs (e.g. {@code Block.getIcon()}) that
 * internally invoke {@code getSpecialRendererByClass()}, which writes to
 * {@code mapSpecialRenderers} via {@link Map#put}, while the render thread
 * is simultaneously iterating {@code mapSpecialRenderers.values()} inside
 * {@code func_147543_a} (the world-update method).
 *
 * <p>Fix strategy:
 * <ol>
 *   <li>In {@code func_147543_a}: redirect the {@link Map#values()} call to
 *       return a synchronized snapshot ({@link ArrayList} copy) so that the
 *       subsequent iteration is immune to concurrent writes.</li>
 *   <li>In {@code getSpecialRendererByClass}: redirect the {@link Map#put}
 *       call to synchronize on the map object, matching the lock used when
 *       creating the snapshot in step 1.</li>
 * </ol>
 */
@Mixin(TileEntityRendererDispatcher.class)
public class MixinTileEntityRendererDispatcher
{
    /**
     * Redirect the {@code mapSpecialRenderers.values()} call in
     * {@code func_147543_a} to return a synchronized snapshot of the values
     * collection. Iterating an {@link ArrayList} copy is safe even if
     * another thread concurrently writes to the backing {@link Map}.
     */
    @Redirect(
        method = "func_147543_a",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;values()Ljava/util/Collection;"
        )
    )
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Collection redirectValuesToSnapshot(Map instance)
    {
        synchronized (instance)
        {
            return new ArrayList<>(instance.values());
        }
    }

    /**
     * Redirect the {@code mapSpecialRenderers.put()} call in
     * {@code getSpecialRendererByClass} to synchronize on the map object,
     * matching the lock used in {@link #redirectValuesToSnapshot}.
     */
    @Redirect(
        method = "getSpecialRendererByClass",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        )
    )
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object redirectPutSynchronized(Map instance, Object key, Object value)
    {
        synchronized (instance)
        {
            return instance.put(key, value);
        }
    }
}
