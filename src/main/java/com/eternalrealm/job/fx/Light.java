package com.eternalrealm.job.fx;

/**
 * Where the effects engine puts its light: quads of four vertices in world coordinates, each with an ARGB colour whose
 * alpha is the light's strength (the client adds them to the scene; the preview renderer adds them to an image).
 */
@FunctionalInterface
public interface Light {
    void vertex(double x, double y, double z, int argb);
}
