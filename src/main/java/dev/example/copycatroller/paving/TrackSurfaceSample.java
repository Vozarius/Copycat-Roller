package dev.example.copycatroller.paving;

public record TrackSurfaceSample(int x, int z, double surfaceY) {
    public TrackSurfaceSample {
        if (!Double.isFinite(surfaceY)) {
            throw new IllegalArgumentException("surfaceY must be finite");
        }
    }
}
