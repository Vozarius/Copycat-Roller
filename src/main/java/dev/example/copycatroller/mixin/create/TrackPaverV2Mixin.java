package dev.example.copycatroller.mixin.create;

import com.simibubi.create.content.contraptions.actors.roller.PaveTask;
import com.simibubi.create.content.contraptions.actors.roller.TrackPaverV2;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import dev.example.copycatroller.paving.PreciseTrackHeightSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrackPaverV2.class)
public abstract class TrackPaverV2Mixin {
    @Inject(
        method = "pave(Lcom/simibubi/create/content/contraptions/actors/roller/PaveTask;"
            + "Lcom/simibubi/create/content/trains/graph/TrackGraph;"
            + "Lcom/simibubi/create/content/trains/graph/TrackEdge;DD)V",
        at = @At("HEAD"),
        require = 1
    )
    private static void copycatRoller$capturePreciseHeight(
        PaveTask task,
        TrackGraph graph,
        TrackEdge edge,
        double from,
        double to,
        CallbackInfo callback
    ) {
        PreciseTrackHeightSampler.capture(task, graph, edge, from, to);
    }
}
