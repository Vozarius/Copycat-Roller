package dev.example.copycatroller.paving;

import java.util.List;
import java.util.Optional;

/**
 * Resolves a stable world-space outward normal from the profiles of an edge
 * Roller and its nearest inward neighbour. This deliberately ignores the
 * carriage's instantaneous rotation: train yaw changes while traversing a
 * curve, but the relative positions of the two paving profiles do not.
 */
public final class TrackProfileSideResolver {
    private static final double DIRECTION_EPSILON = 1.0e-7;

    private TrackProfileSideResolver() {
    }

    public static Optional<HorizontalNormal> outwardNormal(
        TrackSurfaceSample edge,
        List<TrackSurfaceSample> inwardSamples
    ) {
        double tangentLength = Math.hypot(edge.tangentX(), edge.tangentZ());
        double tangentX = edge.tangentX() / tangentLength;
        double tangentZ = edge.tangentZ() / tangentLength;
        double canonicalNormalX = -tangentZ;
        double canonicalNormalZ = tangentX;

        TrackSurfaceSample best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (TrackSurfaceSample candidate : inwardSamples) {
            double outwardX = edge.x() - candidate.x();
            double outwardZ = edge.z() - candidate.z();
            double lateral = outwardX * canonicalNormalX
                + outwardZ * canonicalNormalZ;
            if (Math.abs(lateral) < DIRECTION_EPSILON) {
                continue;
            }

            double along = outwardX * tangentX + outwardZ * tangentZ;
            double squaredDistance = outwardX * outwardX
                + outwardZ * outwardZ;
            double candidateLength = Math.hypot(
                candidate.tangentX(),
                candidate.tangentZ()
            );
            double tangentAlignment = Math.abs((
                edge.tangentX() * candidate.tangentX()
                    + edge.tangentZ() * candidate.tangentZ()
            ) / (tangentLength * candidateLength));
            double score = squaredDistance
                + along * along * 0.5
                + (1 - tangentAlignment) * 0.25;
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        if (best == null) {
            return Optional.empty();
        }

        double outwardX = edge.x() - best.x();
        double outwardZ = edge.z() - best.z();
        if (canonicalNormalX * outwardX + canonicalNormalZ * outwardZ < 0) {
            canonicalNormalX = -canonicalNormalX;
            canonicalNormalZ = -canonicalNormalZ;
        }
        return Optional.of(new HorizontalNormal(
            canonicalNormalX,
            canonicalNormalZ
        ));
    }

    public record HorizontalNormal(double x, double z) {
        public HorizontalNormal {
            if (!Double.isFinite(x) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("normal must be finite");
            }
            double length = Math.hypot(x, z);
            if (length < DIRECTION_EPSILON) {
                throw new IllegalArgumentException("normal must not be zero");
            }
            x /= length;
            z /= length;
        }
    }
}