package com.nihonisshu.tabi.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Voxel design of a train car or bus, exported by the builder.
 * Local frame: x across (width, +x = left of travel), y up from rail top / road, z along the car (+z = front).
 * Origin = car centre at rail level. Block coordinates are design units (multiplied by scale when drawn);
 * size and seats are in blocks.
 */
public final class CarDesign {
    public final String id;
    public final double width, height, length;
    /** design units -> blocks (vehicles are modelled with finer voxels than the world) */
    public final double scale;
    public final List<String> palette = new ArrayList<>();
    /** each block: x, y, z (local block corner, may be fractional), palette index, flags (1 = door panel hidden when open) */
    public final List<double[]> blocks = new ArrayList<>();
    public final List<Vec3> seats = new ArrayList<>();

    private CarDesign(JsonObject o) {
        id = TabiData.str(o, "id", "car");
        JsonArray size = o.getAsJsonArray("size");
        width = size.get(0).getAsDouble();
        height = size.get(1).getAsDouble();
        length = size.get(2).getAsDouble();
        scale = TabiData.dbl(o, "scale", 1.0);
        for (JsonElement e : o.getAsJsonArray("palette")) palette.add(e.getAsString());
        for (JsonElement e : o.getAsJsonArray("blocks")) {
            JsonArray a = e.getAsJsonArray();
            blocks.add(new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsDouble(), a.size() > 4 ? a.get(4).getAsDouble() : 0});
        }
        for (JsonElement e : o.getAsJsonArray("seats")) {
            JsonArray a = e.getAsJsonArray();
            seats.add(new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()));
        }
    }

    private static Map<String, CarDesign> DESIGNS;

    public static synchronized CarDesign get(String id) {
        if (DESIGNS == null) {
            DESIGNS = new HashMap<>();
            JsonArray arr = TabiData.meta().getAsJsonArray("cars");
            if (arr != null) {
                for (JsonElement e : arr) {
                    CarDesign d = new CarDesign(e.getAsJsonObject());
                    DESIGNS.put(d.id, d);
                }
            }
        }
        return DESIGNS.get(id);
    }
}
