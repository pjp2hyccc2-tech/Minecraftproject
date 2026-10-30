package com.mcbesiege.machine;

import com.mcbesiege.physics.RigidBody;

import java.util.ArrayList;
import java.util.List;

/** A rigid body of a machine and the part pieces it carries. */
public final class MBody {
    public final RigidBody rb;
    public final List<MPart.Sub> subs = new ArrayList<>();
    public int index;
    public boolean debris;

    public MBody(RigidBody rb) {
        this.rb = rb;
        rb.userData = this;
    }
}
