// =====================================================================================
//  ReisatsuShaders - prepare / prepare1 (reisatsu:hidamari): the light table
//  For every block column of the 128 x 128 window around the camera: the 4 lamps that can
//  reach it, best first. Vanilla light spreads by Manhattan distance, so the search is
//  separable: TABLE_Z finds the best 4 along z (from the emitter map's 4 lamp layers),
//  TABLE_X the best 6 of those along x, walks the wall tiles from the column to each of them
//  (lamps behind a wall are flagged and ranked last) and keeps 4.
//  Score = level - |dx| - |dz| - a vertical penalty (distance from the camera's height band).
//    out: colortex7 texel (x, z) = 4 packed lamps (lib/emitters.glsl tablePack)
// =====================================================================================
#include "/lib/header.glsl"

#ifdef VERTEX_SHADER
void main() {
    gl_Position = ftransform();
}
#endif

#ifdef FRAGMENT_SHADER

#define KEEP 6
float bestS[KEEP];
float bestE[KEEP];

void consider(float score, float enc) {
    if (score <= bestS[KEEP - 1]) return;
    int i = KEEP - 1;
    // insertion: shift the worse ones down
    for (int k = KEEP - 1; k > 0; k--) {
        if (score > bestS[k - 1]) {
            bestS[k] = bestS[k - 1];
            bestE[k] = bestE[k - 1];
            i = k - 1;
        } else {
            break;
        }
    }
    bestS[i] = score;
    bestE[i] = enc;
}

// lamps far above or below the camera's band reach fewer of the surfaces that are seen
float verticalPenalty(int y) {
    float ref = cameraPosition.y - 0.6;
    return max(abs(float(y) + 0.5 - ref) - 2.5, 0.0) * 0.6;
}

void main() {
    ivec2 cell = ivec2(gl_FragCoord.xy);
    vec4 outv = vec4(0.0);
#ifdef WORLD_HIDAMARI
    if (cell.x < EMAP_TILE && cell.y < EMAP_TILE) {
        for (int i = 0; i < KEEP; i++) { bestS[i] = 0.0; bestE[i] = 0.0; }
        int refY = tableRefY();
  #ifdef TABLE_Z
        // ---- along z: read the 4 lamp layers of the column line ------------------------
        for (int dz = -15; dz <= 15; dz++) {
            int z = cell.y + dz;
            if (z < 0 || z >= EMAP_TILE) continue;
            for (int layer = 0; layer < 4; layer++) {
                vec4 t = texelFetch(shadowcolor0, emapTileOffset(layer) + ivec2(cell.x, z), 0);
                if (!emapLampMarker(t)) continue;
                int type = int(floor(t.r * 255.0 + 0.5));
                if (!emitterCasts(type)) continue;
                int by = int(floor(t.g * 255.0 + 0.5)) + 32;
                int yrel = by - refY;
                if (yrel < 0 || yrel > 31) continue;
                int level = emapLampLevel(t);
                if (level <= 0) level = int(EM_LEVEL[type]);
                float score = float(level) - abs(float(dz)) - verticalPenalty(by);
                if (score <= 0.0) continue;
                // tiny bias: brighter lamps first on equal score
                consider(score + float(level) * 0.001, tablePack(type, level, yrel, 0, dz, false));
            }
        }
        outv = vec4(bestE[0], bestE[1], bestE[2], bestE[3]);
  #endif
  #ifdef TABLE_X
        // ---- along x: the best of every z-line result within reach --------------------
        for (int dx = -15; dx <= 15; dx++) {
            int x = cell.x + dx;
            if (x < 0 || x >= EMAP_TILE) continue;
            vec4 zt = texelFetch(colortex7, ivec2(x, cell.y), 0);
            for (int k = 0; k < 4; k++) {
                float v = zt[k];
                if (abs(v) < 0.5) continue;
                int c = int(abs(v) + 0.5);
                int type = c & 31;
                int level = (c >> 5) & 15;
                int yrel = (c >> 9) & 31;
                int dz = ((c >> 19) & 31) - 15;
                float score = float(level) - abs(float(dz)) - abs(float(dx)) - verticalPenalty(yrel + refY);
                if (score <= 0.0) continue;
                consider(score + float(level) * 0.001, tablePack(type, level, yrel, dx, dz, false));
            }
        }
        // ---- walls: is the lamp's light stopped on its way to this column? --------------
        float outS[4];
        float outE[4];
        for (int i = 0; i < 4; i++) { outS[i] = -1e9; outE[i] = 0.0; }
        for (int i = 0; i < KEEP; i++) {
            float v = bestE[i];
            if (abs(v) < 0.5) continue;
            int c = int(abs(v) + 0.5);
            int dx = ((c >> 14) & 31) - 15;
            int dz = ((c >> 19) & 31) - 15;
            bool walled = wallBetween(cell, cell + ivec2(dx, dz));
            float s = bestS[i] - (walled ? 6.0 : 0.0);
            float enc = walled ? -v : v;
            if (s > outS[3]) {
                int j = 3;
                for (int k = 3; k > 0; k--) {
                    if (s > outS[k - 1]) { outS[k] = outS[k - 1]; outE[k] = outE[k - 1]; j = k - 1; } else break;
                }
                outS[j] = s;
                outE[j] = enc;
            }
        }
        outv = vec4(outE[0], outE[1], outE[2], outE[3]);
  #endif
    }
#endif
    /* RENDERTARGETS: 7 */
    gl_FragData[0] = outv;
}
#endif
