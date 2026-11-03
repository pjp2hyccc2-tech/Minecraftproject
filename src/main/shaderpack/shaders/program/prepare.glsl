// =====================================================================================
//  ReisatsuShaders - prepare / prepare1 (reisatsu:hidamari): the light table
//  For every block column of the 128 x 128 window around the camera: the 4 lamps that can
//  reach it, best first. Vanilla light spreads by Manhattan distance, so the search is
//  separable: TABLE_Z finds the best 4 along z (from the emitter map's 4 lamp layers),
//  TABLE_X the best 6 of those along x, walks the wall tiles from the column to each of them
//  (lamps behind a wall are flagged and ranked last) and keeps 4.
//  Score = level - |dx| - |dz| - a vertical penalty (distance from the camera's height band),
//  + OUTDOOR_REACH for the lamps outside (they light the open lot farther than vanilla does).
//    out: colortex7 texel (x, z) = 4 packed lamps (lib/emitters.glsl tablePack)
//  prepare1 also writes the frame's list of outdoor lamps for the air (row 0 right of the
//  table, airListTexel; read by lib/emitters.glsl airLampsGather).
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

#if defined WORLD_HIDAMARI && defined TABLE_X
// ---- the outdoor lamps of the air: one list for the whole frame ---------------------------
// The glow of the lamps in the mist must not depend on which lamps the columns along a ray
// happen to list (that drew walls and wedges of light across the sky): the outdoor lamps
// within ~40 blocks are gathered here once - from the z-lines (TABLE_Z) of six rows of
// columns, overlapping, deduplicated - a sign's blocks merged into a few bright entries, and
// ranked by how much they matter from the camera. The weakest kept ones fade out before they
// would drop off the list. Every texel of the list computes the same list and writes its part.
#define AIR_WORK 24
#define AIR_SEEN 64
vec4 airListTexel(int slot) {
    ivec2 cc = ivec2(floor(cameraPosition.xz)) - emapOrigin();
    int refY = tableRefY();
    int seen[AIR_SEEN];
    int nSeen = 0;
    vec3 eSum[AIR_WORK];
    float eN[AIR_WORK];
    int eType[AIR_WORK];
    int eLevel[AIR_WORK];
    for (int e = 0; e < AIR_WORK; e++) { eSum[e] = vec3(0.0); eN[e] = 1.0; eType[e] = 0; eLevel[e] = 0; }
    int nE = 0;
    for (int r = 0; r < 6; r++) {
        int zg = cc.y - 30 + r * 12;
        if (zg < 0 || zg >= EMAP_TILE) continue;
        for (int dx = -40; dx <= 40; dx++) {
            int x = cc.x + dx;
            if (x < 0 || x >= EMAP_TILE) continue;
            vec4 zt = texelFetch(colortex7, ivec2(x, zg), 0);
            for (int k = 0; k < 4; k++) {
                float v = zt[k];
                if (abs(v) < 0.5) continue;
                int c = tableCode(v);
                int type = c & 31;
                if (!emitterOutdoorType(type) || !emitterCasts(type)) continue;
                int level = (c >> 5) & 15;
                int yrel = (c >> 9) & 31;
                int z = zg + ((c >> 19) & 31) - 15;
                int key = x | (z << 7) | (yrel << 14);
                bool dup = false;
                for (int q = 0; q < AIR_SEEN; q++) {
                    if (q >= nSeen) break;
                    if (seen[q] == key) { dup = true; break; }
                }
                if (dup) continue;
                if (nSeen < AIR_SEEN) { seen[nSeen] = key; nSeen++; }
                vec3 pb = vec3(float(x), float(yrel), float(z));
                // a sign is many blocks: its neighbours of one type become one bright entry
                int m = -1;
                for (int e = 0; e < AIR_WORK; e++) {
                    if (e >= nE) break;
                    if (eType[e] != type) continue;
                    vec3 d = abs(eSum[e] / eN[e] - pb);
                    if (max(d.x, max(d.y, d.z)) < 2.6) { m = e; break; }
                }
                if (m >= 0) {
                    eSum[m] += pb;
                    eN[m] += 1.0;
                    eLevel[m] = max(eLevel[m], level);
                } else if (nE < AIR_WORK) {
                    eSum[nE] = pb;
                    eN[nE] = 1.0;
                    eType[nE] = type;
                    eLevel[nE] = level;
                    nE++;
                }
            }
        }
    }
    // how much each matters from here: its strength over (distance^2 + 100)
    float imp[AIR_WORK];
    for (int e = 0; e < AIR_WORK; e++) {
        imp[e] = 0.0;
        if (e >= nE) continue;
        vec3 p = eSum[e] / eN[e];
        vec3 wpos = vec3(float(emapOrigin().x) + p.x, float(refY) + p.y, float(emapOrigin().y) + p.z) + 0.5;
        vec3 d = wpos - cameraPosition;
        // gone before the edge of the gathered window
        float edge = 1.0 - smoothstep(30.0, 39.0, max(abs(p.x - float(cc.x)), abs(p.z - float(cc.y))));
        imp[e] = EM_GAIN[clamp(eType[e], 0, EM_COUNT - 1)] * eN[e] / (dot(d, d) + 100.0) * edge;
    }
    // the best AIR_OUT, strongest first; the best one left out sets the fade
    int pick = -1;
    float pickImp = 0.0;
    float rejected = 0.0;
    int want = slot < AIR_OUT ? slot : slot - AIR_OUT;
    for (int n = 0; n <= AIR_OUT; n++) {
        int bi = -1;
        float bv = 0.0;
        for (int e = 0; e < AIR_WORK; e++) {
            if (e >= nE) break;
            if (imp[e] > bv) { bv = imp[e]; bi = e; }
        }
        if (bi < 0) break;
        if (n == AIR_OUT) { rejected = bv; break; }
        if (n == want) { pick = bi; pickImp = bv; }
        imp[bi] = -1.0;
    }
    if (pick < 0) return vec4(0.0);
    if (slot < AIR_OUT) return vec4(eSum[pick] / eN[pick], float(eType[pick]));
    float w = rejected > 0.0 ? smoothstep(rejected, rejected * 1.8, pickImp) : 1.0;
    w *= smoothstep(0.0, 1e-4, pickImp);
    return vec4(eN[pick], float(eLevel[pick]), w, 0.0);
}
#endif

void main() {
    ivec2 cell = ivec2(gl_FragCoord.xy);
    vec4 outv = vec4(0.0);
#ifdef WORLD_HIDAMARI
  #ifdef TABLE_X
    if (cell.y == 0 && cell.x >= AIR_LIST_X && cell.x < AIR_LIST_X + 2 * AIR_OUT) {
        outv = airListTexel(cell.x - AIR_LIST_X);
    }
  #endif
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
                // the lamps outside reach farther over the open lot (lib/emitters.glsl OUTDOOR_REACH)
                float score = float(level) - abs(float(dz)) - tableVerticalPenalty(float(by))
                            + (emitterOutdoorType(type) ? OUTDOOR_REACH : 0.0);
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
                int c = tableCode(v);
                int type = c & 31;
                int level = (c >> 5) & 15;
                int yrel = (c >> 9) & 31;
                int dz = ((c >> 19) & 31) - 15;
                float score = float(level) - abs(float(dz)) - abs(float(dx)) - tableVerticalPenalty(float(yrel + refY))
                            + (emitterOutdoorType(type) ? OUTDOOR_REACH : 0.0);
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
            int c = tableCode(v);
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
