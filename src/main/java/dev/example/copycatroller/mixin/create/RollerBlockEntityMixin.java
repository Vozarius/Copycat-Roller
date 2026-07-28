package dev.example.copycatroller.mixin.create;

import com.copycatsplus.copycats.CCBlocks;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RollerBlockEntity.class)
public abstract class RollerBlockEntityMixin {
    @Inject(
        method = "isValidMaterial(Lnet/minecraft/world/item/ItemStack;)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 1
    )
    private void copycatRoller$allowExactLayer(
        ItemStack newFilter,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (newFilter.is(CCBlocks.COPYCAT_LAYER.asItem())) {
            callback.setReturnValue(true);
        }
    }
}
