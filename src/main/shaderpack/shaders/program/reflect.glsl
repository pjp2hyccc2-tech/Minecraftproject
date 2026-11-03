// =====================================================================================
//  HitoriShaders - composite1: reflections, refraction
//   Screen-space reflections for everything glossy: the wet street mirroring the street lamp
//   and the vending machine (streaked by the wet grain of the asphalt), the puddles (black
//   mirrors, rain rings), the bath water (dark and still: the tiles, the frosted window's glow),
//   the wet bathroom tiles, the old varnished floor (soft), the genkan tiles, porcelain, chrome,
//   the mirrors, the dead TV's curved glass (the room - and you - in it). The windows: the rain
//   on them bends the street lamp into points, the glass mirrors the room a little. Frosted
//   glass blurs what is behind it. Rays that leave the screen fall back to the overcast outside
//   and to the room's blurred tone inside; the lamps' own highlights were already added by the
//   forward pass (they reach off-screen lamps too).
//   colortex0 -> colortex0
// =====================================================================================
#include "/lib/header.glsl"

#ifdef VERTEX_SHADER
out vec2 texcoord;
void main() {
    gl_Position = ftransform();
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
}
#endif

#ifdef FRAGMENT_SHADER
in vec2 texcoord;

const bool colortex0MipmapEnabled = true;

float viewZ(float depth) {
    return -gbufferProjection[3][2] / (depth * 2.0 - 1.0 + gbufferProjection[2][2]);
}

// march the reflected ray through the depth buffer. viewP: start (view space), R: unit dir
bool traceScreen(vec3 viewP, vec3 R, float dither, out vec2 hitUv, out float hitT) {
    hitUv = vec2(0.0);
    hitT = 0.0;
    float stride = 0.05 + 0.022 * -viewP.z;
    float t = stride * (0.5 + dither);
    float tPrev = 0.0;
    for (int i = 0; i < SSR_STEPS; i++) {
        vec3 q = viewP + R * t;
        if (q.z > -near * 1.5) return false;                // behind the camera
        vec3 sc = viewToScreen(q);
        if (sc.x < 0.0 || sc.x > 1.0 || sc.y < 0.0 || sc.y > 1.0) return false;
        float d = textureLod(depthtex0, sc.xy, 0.0).r;
        if (d < 1.0 && !isHandDepth(d)) {
            float delta = viewZ(d) - q.z;                     // > 0: the ray went behind a surface
            float thick = max(0.25, (t - tPrev) * 2.5 + 0.02 * -q.z);
            if (delta > 0.0 && delta < thick) {
                // refine between the last two steps
                float a = tPrev;
                float b = t;
                for (int k = 0; k < 4; k++) {
                    float m = 0.5 * (a + b);
                    vec3 qm = viewP + R * m;
                    vec3 sm = viewToScreen(qm);
                    float dm = textureLod(depthtex0, sm.xy, 0.0).r;
                    if (viewZ(dm) - qm.z > 0.0) b = m; else a = m;
                }
                vec3 qh = viewP + R * b;
                hitUv = viewToScreen(qh).xy;
                hitT = b;
                return true;
            }
        }
        tPrev = t;
        t += stride;
        stride *= 1.38;
    }
    return false;
}

// what a reflection sees where the screen has nothing: the sky / the room's tone
vec3 reflectionFallback(vec3 Rw, float outside, vec2 uv) {
    vec3 sky = skyGradient(normalize(vec3(Rw.x, max(Rw.y, 0.02), Rw.z)));
    vec3 room = textureLod(colortex0, uv, 7.0).rgb * 0.30;
    return mix(room, sky, outside);
}

// rough wet ground: the far lights (the street lamp, the vending machine down the street)
// smeared into long streaks toward the viewer - the blurred screen where the reflected
// direction meets the far scene, stretched vertically
vec3 glossyFar(vec3 viewP, vec3 Rv, float rough, float spreadK) {
    vec3 sum = vec3(0.0);
    float wsum = 0.0;
    for (int i = 0; i < 3; i++) {
        float dist = 10.0 + 12.0 * float(i);
        vec3 fp = viewP + Rv * dist;
        if (fp.z > -1.0) continue;
        vec3 sc = viewToScreen(fp);
        if (sc.x < 0.0 || sc.x > 1.0 || sc.y < 0.0 || sc.y > 1.0) continue;
        float lod = 3.0 + rough * 5.0;
        float sp = spreadK * (0.05 + 0.08 * float(i));
        vec3 c = textureLod(colortex0, sc.xy, lod).rgb * 0.4
               + textureLod(colortex0, sc.xy + vec2(0.0, sp), lod + 0.5).rgb * 0.3
               + textureLod(colortex0, sc.xy - vec2(0.0, sp), lod + 0.5).rgb * 0.3;
        vec2 e = min(sc.xy, 1.0 - sc.xy);
        float w = smoothstep(0.0, 0.10, min(e.x, e.y));
        sum += c * w;
        wsum += 1.0;
    }
    return wsum > 0.0 ? sum / wsum : vec3(0.0);
}

void main() {
    vec3 color = texture(colortex0, texcoord).rgb;
#ifdef REFLECTIONS
    float depth = texture(depthtex0, texcoord).r;
    if (depth < 1.0 && !isHandDepth(depth)) {
        vec4 alb = texture(colortex5, texcoord);
        int mat = int(floor(alb.a * 255.0 + 0.5));
        vec4 nm = texture(colortex6, texcoord);
        vec3 N = octDecode(nm.rg);
        vec3 viewP = screenToView(texcoord, depth);
        vec3 P = viewToPlayer(viewP);
        vec3 wp = P + cameraPosition;
        float wet = surfaceWetness(wp, N, nm.a, mat);
        vec4 surf = materialSurface(mat, wet);

        // ---- frosted glass: what is behind it, blurred to shapes and glows -------------------
        if (mat == MAT_FROSTED) {
            vec3 blur = textureLod(colortex0, texcoord, 3.2).rgb * 0.55 + textureLod(colortex0, texcoord, 4.4).rgb * 0.45;
            color = mix(color, max(blur, color * 0.6), 0.70);
        }

        if (surf.w > 0.0) {
            float puddle = puddleAmount(wp, mat, N) * wet;
            float rough = mix(surf.x, 0.05, puddle);
            vec3 albedo = toLinear(alb.rgb);
            float dither = ign(gl_FragCoord.xy + float(frameCounter % 4) * 17.0);

            // ---- a window: the rain on it bends the street lamp into points ----------------------
            if (mat == MAT_GLASS_RAIN) {
                vec3 a = abs(N);
                vec3 flatN = a.x > a.y && a.x > a.z ? vec3(sign(N.x), 0.0, 0.0) : (a.z > a.y ? vec3(0.0, 0.0, sign(N.z)) : vec3(0.0, sign(N.y), 0.0));
                vec3 bend = mat3(gbufferModelView) * (N - flatN);
                vec2 off = bend.xy * 0.060 / max(-viewP.z * 0.15, 1.0);
                if (dot(off, off) > 1e-8) {
                    // a drop is a tiny lens: it shows a small, flipped, slightly blurred bit of what is behind
                    color = textureLod(colortex0, texcoord - off, 0.8).rgb * 1.05;
                }
            }

            vec3 V = normalize(viewP);
            vec3 Nv = normalize(mat3(gbufferModelView) * N);
            if (dot(Nv, -V) < 0.0) Nv = -Nv;                  // glass seen from its back side
            vec3 Rv = reflect(V, Nv);
            float NdotV = saturate(dot(Nv, -V));
            vec3 Rw = mat3(gbufferModelViewInverse) * Rv;
            float outside = 1.0;
  #ifdef WORLD_SHIRAKABA
            outside = openSky(wp + N * 0.3 + vec3(0.0, 0.1, 0.0));
  #endif
            vec2 hitUv;
            float hitT;
            vec3 refl = reflectionFallback(Rw, outside, texcoord);
            bool roughWet = mat == MAT_ASPHALT || mat == MAT_CONCRETE || mat == MAT_WET;
            if (roughWet && Rv.z < 0.2) {
                refl = max(refl, glossyFar(viewP, Rv, rough, rough * (1.0 - puddle) * 3.0));
            }
            if (Rv.z < 0.35 && traceScreen(viewP + Nv * 0.02, Rv, dither, hitUv, hitT)) {
                // blur with roughness and distance; wet asphalt streaks vertically
                float lod = clamp(rough * 11.0 * saturate(hitT / 3.0 + 0.2), 0.0, 6.0);
                vec3 hc = textureLod(colortex0, hitUv, lod).rgb;
                if (roughWet) {
                    float spread = rough * (1.0 - puddle) * 0.07 * saturate(hitT / 6.0 + 0.25);
                    hc = hc * 0.30
                       + textureLod(colortex0, hitUv + vec2(0.0, spread), lod).rgb * 0.22
                       + textureLod(colortex0, hitUv - vec2(0.0, spread), lod).rgb * 0.22
                       + textureLod(colortex0, hitUv + vec2(0.0, spread * 2.2), lod + 1.0).rgb * 0.13
                       + textureLod(colortex0, hitUv - vec2(0.0, spread * 2.2), lod + 1.0).rgb * 0.13;
                }
                // fade toward the screen edges and for rays heading back at the camera
                vec2 e = min(hitUv, 1.0 - hitUv);
                float fade = smoothstep(0.0, 0.07, min(e.x, e.y)) * (1.0 - smoothstep(0.10, 0.35, Rv.z));
                // hits on the hand are not reflections
                float hd = texture(depthtex0, hitUv).r;
                fade *= isHandDepth(hd) ? 0.0 : 1.0;
                refl = mix(refl, hc, fade);
            }

            // ---- put it on the surface ---------------------------------------------------------
            float F = fresnelSchlick(surf.y, NdotV);
            if (mat == MAT_STEEL || mat == MAT_CHROME) {
                // metal: the reflection is the colour
                vec3 tint = mix(vec3(0.62, 0.64, 0.66), albedo * 1.4, 0.35);
                color = color * (1.0 - 0.55 * surf.w) + refl * tint * F * surf.w;
            } else if (mat == MAT_MIRROR) {
                // an old mirror: silver going dark at the edges, a little dirty
                color = mix(color, refl * vec3(0.82, 0.86, 0.88), 0.80);
            } else if (mat == MAT_PUDDLE || mat == MAT_WATER) {
                // black water: Fresnel of water; the lamps are bright enough to show in it
                float Fw = 0.02 + 0.98 * pow(1.0 - NdotV, 5.0);
                float k = mat == MAT_WATER ? 1.3 : 1.6;
                color = color * (1.0 - Fw) + refl * max(Fw, 0.035) * k;
            } else if (mat == MAT_GLASS_RAIN) {
                float Fg = 0.04 + 0.96 * pow(1.0 - NdotV, 5.0);
                color += refl * Fg * 1.1;
            } else if (mat == MAT_CRT_GLASS) {
                // the dead TV: dark green-grey curved glass, the room - and you - in it
                float Fg = 0.05 + 0.95 * pow(1.0 - NdotV, 5.0);
                color = color * (1.0 - Fg) + refl * vec3(0.78, 0.86, 0.82) * max(Fg, 0.10);
            } else {
                // dielectrics: floors, wet ground, tiles, porcelain, varnish
                float k = surf.w * mix(1.0, 1.5, puddle);
                color = color * (1.0 - F * k * 0.5) + refl * F * k;
            }
        }
    }
#endif
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(max(color, vec3(0.0)), 1.0);
}
#endif
