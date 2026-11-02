package com.sounan.entity;

/** Animation state shared by all SOUNAN monsters (read by the client models). */
public interface Animated {
    int pose();

    float poseTime(float partialTick);
}
