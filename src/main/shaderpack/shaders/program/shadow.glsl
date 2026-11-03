// =====================================================================================
//  ReisatsuShaders - the shadow pass
//  * other dimensions: the sun / moon shadow map (water and glass cast none)
//  * reisatsu:hidamari: the overcast night has no moon to cast shadows - the pass draws the
//    top-down EMITTER MAP instead (lib/emitters.glsl): 4 x 4 tiles of 128 x 128 texels, one
//    texel per block column around the camera.
//      tiles 0..3: the lit lamps of 4 height layers. Every face of a lamp block is snapped onto
//        the block's footprint (at_midBlock + the quad corner from gl_VertexID), so even a
//        2 px thin sign fills its texel; faces without glowing texels (the pole under the
//        sign, a lamp's housing) are dropped in the fragment shader.
//      tile 4: the height of the topmost surface of every column (what is under a roof).
//      tiles 5 / 6: full wall blocks at y 66 / 67 (their side faces snapped onto the footprint
//        like the lamps): lamps behind a wall do not light this side.
//      tile 7: the lowest window blind of each column (slats for the light from outside).
// =====================================================================================
#define PROGRAM_SHADOW
#include "/lib/header.glsl"

#ifdef VERTEX_SHADER
in vec4 mc_Entity;
in vec4 mc_midTexCoord;
#ifdef WORLD_HIDAMARI
in vec3 at_midBlock;
#endif

out vec2 texcoord;
out vec4 glcolor;
#ifdef WORLD_HIDAMARI
flat out int kind;          // 0 dropped, 1 lamp, 2 cover, 3 wall, 4 blind
flat out int etype;
flat out int blockY;
flat out vec2 midUv;
flat out vec2 halfUv;
flat out vec3 faceN;
out float worldY;
out float lightLevel;
#endif

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    glcolor = gl_Color;
    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
    int id = int(mc_Entity.x + 0.5);
#ifdef WORLD_HIDAMARI
    vec3 playerPos = (shadowModelViewInverse * viewPos).xyz;
    vec3 wp = playerPos + cameraPosition;
    vec3 n = gl_Normal;
    n = dot(n, n) > 1e-6 ? normalize(n) : vec3(0.0, 1.0, 0.0);
    faceN = n;
    midUv = mc_midTexCoord.xy;
    halfUv = abs(texcoord - mc_midTexCoord.xy);
    worldY = wp.y;
    vec2 lmv = normalizeLightmap((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);
    // the lamp's own light measured on its faces (+1 = its level). Faces drawn full-bright
    // (Forge forge_data block_light 15 / sky_light 15: the glowing panel of a model) say
    // nothing about the level: 0 = unknown (the design level is used)
    lightLevel = (lmv.x > 0.985 && lmv.y > 0.985) ? 0.0 : lmv.x * 15.0;
    ivec2 org = emapOrigin();
    int type = emitterTypeOfBlock(id);
    kind = 0;
    etype = type;
    blockY = 0;
    vec2 texel;
    float depth;
    if (type > EM_NONE) {
        bool hasMid = dot(at_midBlock, at_midBlock) > 1e-6;
        vec3 center = hasMid ? wp + at_midBlock / 64.0 : floor(wp - n * 0.02) + 0.5;
        ivec3 blk = ivec3(floor(center));
        // a face on the block's boundary was lit from the neighbour (level - 1), an inner face
        // of a model from the lamp's own cell (level)
        if (lightLevel > 0.0) lightLevel += abs(dot(wp - center, n)) > 0.45 ? 1.0 : 0.0;
        ivec2 c = blk.xz - org;
        blockY = blk.y;
        if (c.x >= 0 && c.y >= 0 && c.x < EMAP_TILE && c.y < EMAP_TILE) {
            // the quad's corner -> a corner of the block footprint, always counter-clockwise
            int corner = gl_VertexID & 3;
            vec2 cxz = vec2(c) + vec2((corner == 1 || corner == 2) ? 1.0 : 0.0, corner >= 2 ? 1.0 : 0.0);
            texel = vec2(emapTileOffset(emapLayer(blk.y))) + cxz;
            depth = 1.0 - float(blk.y) / 256.0 - lightLevel * 1e-4;
            kind = 1;
        }
    } else if ((id == BLK_BLIND_CLOSED || id == BLK_BLIND_OPEN) && dot(at_midBlock, at_midBlock) > 1e-6) {
        // window blinds: snapped onto the footprint; the lowest blind of a column wins
        ivec3 blk = ivec3(floor(wp + at_midBlock / 64.0));
        ivec2 c = blk.xz - org;
        blockY = blk.y;
        etype = id == BLK_BLIND_CLOSED ? 1 : 0;
        if (c.x >= 0 && c.y >= 0 && c.x < EMAP_TILE && c.y < EMAP_TILE) {
            int corner = gl_VertexID & 3;
            vec2 cxz = vec2(c) + vec2((corner == 1 || corner == 2) ? 1.0 : 0.0, corner >= 2 ? 1.0 : 0.0);
            texel = vec2(emapTileOffset(EMAP_BLIND)) + cxz;
            depth = float(blk.y) / 256.0 - 0.5;
            kind = 4;
        }
    } else if (abs(n.y) < 0.5) {
        // side faces: only those of full wall blocks at y 66 / 67 matter (the wall tiles);
        // in a top-down view they are lines anyway
        bool hasMid = dot(at_midBlock, at_midBlock) > 1e-6;
        if (isWallBlock(id) && hasMid) {
            ivec3 blk = ivec3(floor(wp + at_midBlock / 64.0));
            ivec2 c = blk.xz - org;
            if ((blk.y == EMAP_WALL_Y || blk.y == EMAP_WALL_Y + 1) && c.x >= 0 && c.y >= 0 && c.x < EMAP_TILE && c.y < EMAP_TILE) {
                int corner = gl_VertexID & 3;
                vec2 cxz = vec2(c) + vec2((corner == 1 || corner == 2) ? 1.0 : 0.0, corner >= 2 ? 1.0 : 0.0);
                texel = vec2(emapTileOffset(blk.y == EMAP_WALL_Y ? EMAP_WALL_LO : EMAP_WALL_HI)) + cxz;
                depth = 0.5;
                kind = 3;
            }
        }
    } else {
        // top / bottom faces: the cover heights (clamped to the tile: nothing spills over)
        vec2 c = clamp(wp.xz - vec2(org), vec2(0.0), vec2(float(EMAP_TILE)));
        texel = vec2(emapTileOffset(EMAP_COVER)) + c;
        depth = 1.0 - wp.y / 256.0;
        kind = 2;
    }
    if (kind == 0) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);          // outside the map: dropped
    } else {
        float res = float(4 * EMAP_TILE);
        gl_Position = vec4(texel / res * 2.0 - 1.0, clamp(depth, -0.999, 0.999), 1.0);
    }
#else
    // ---- the sun / moon shadow map ----------------------------------------------------
    if (id == BLK_WATER || id == BLK_GLASS || id == BLK_WINDOW || id == BLK_PUDDLE) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);          // water and glass cast no shadow
        return;
    }
  #ifdef WAVING_PLANTS
    if (id == BLK_PLANT || id == BLK_LEAVES || id == BLK_HANGING || id == BLK_SWAY_SOFT) {
        vec3 playerPos = (shadowModelViewInverse * viewPos).xyz;
        float sky = normalizeLightmap((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy).y;
        playerPos += wavingOffset(id, playerPos + cameraPosition, texcoord, mc_midTexCoord.xy, sky, 0.5);
        viewPos = shadowModelView * vec4(playerPos, 1.0);
    }
  #endif
    gl_Position = gl_ProjectionMatrix * viewPos;
    gl_Position.xyz = distortShadowClip(gl_Position.xyz);
#endif
}
#endif

#ifdef FRAGMENT_SHADER
in vec2 texcoord;
in vec4 glcolor;
#ifdef WORLD_HIDAMARI
flat in int kind;
flat in int etype;
flat in int blockY;
flat in vec2 midUv;
flat in vec2 halfUv;
flat in vec3 faceN;
in float worldY;
in float lightLevel;
#endif

void main() {
#ifdef WORLD_HIDAMARI
    if (kind == 1) {
        // does this face glow at all? (5 taps over its texture rectangle)
        float glowing = 0.0;
        for (int i = 0; i < 5; i++) {
            vec2 o = i == 0 ? vec2(0.0) : vec2((i & 1) == 0 ? -0.5 : 0.5, i < 3 ? -0.5 : 0.5);
            vec4 tx = textureLod(gtexture, midUv + o * halfUv, 0.0);
            if (tx.a > 0.1) glowing = max(glowing, emitterSurfaceMask(etype, tx.rgb, faceN));
        }
        if (glowing < 0.25) discard;
        float lvl = lightLevel > 0.5 ? min(lightLevel, 15.0) : EM_LEVEL[clamp(etype, 0, EM_COUNT - 1)];
        /* RENDERTARGETS: 0 */
        gl_FragData[0] = vec4(float(etype) / 255.0, float(clamp(blockY - 32, 0, 255)) / 255.0, (128.0 + floor(lvl + 0.5)) / 255.0, 1.0);
    } else if (kind == 3) {
        /* RENDERTARGETS: 0 */
        gl_FragData[0] = vec4(0.0, 0.0, 32.0 / 255.0, 1.0);
    } else if (kind == 4) {
        /* RENDERTARGETS: 0 */
        gl_FragData[0] = vec4(float(etype), float(clamp(blockY - 32, 0, 255)) / 255.0, 16.0 / 255.0, 1.0);
    } else {
        vec4 c = texture(gtexture, texcoord) * glcolor;
        if (c.a < 0.1) discard;
        float h16 = clamp(floor(worldY * 16.0 + 0.5), 0.0, 65535.0);
        float hi = floor(h16 / 256.0);
        /* RENDERTARGETS: 0 */
        gl_FragData[0] = vec4(hi / 255.0, (h16 - hi * 256.0) / 255.0, 64.0 / 255.0, 1.0);
    }
#else
    vec4 c = texture(gtexture, texcoord) * glcolor;
    if (c.a < 0.1) discard;
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = c;
#endif
}
#endif
