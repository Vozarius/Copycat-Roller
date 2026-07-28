package dev.example.copycatroller.mixin;

import java.util.List;
import java.util.Set;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class CopycatRollerMixinPlugin implements IMixinConfigPlugin {
    private boolean dependenciesPresent;

    @Override
    public void onLoad(String mixinPackage) {
        LoadingModList modList = LoadingModList.get();
        dependenciesPresent = modList != null
            && modList.getModFileById("create") != null
            && modList.getModFileById("copycats") != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return dependenciesPresent;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
