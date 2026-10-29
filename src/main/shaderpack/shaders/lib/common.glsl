// =====================================================================================
//  HuntCraft Shaders - common helpers (math, encoding, dithering, noise, space transforms)
// =====================================================================================

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
float saturate(float x) { return clamp(x, 0.0, 1.0); }
vec2 saturate(vec2 x) { return clamp(x, vec2(0.0), vec2(1.0)); }
vec3 saturate(vec3 x) { return clamp(x, vec3(0.0), vec3(1.0)); }
vec4 saturate(vec4 x) { return clamp(x, vec4(0.0), vec4(1.0)); }
vec3 saturate3(vec3 x) { return clamp(x, vec3(0.0), vec3(1.0)); }
float pow2(float x) { return x * x; }
float pow3(float x) { return x * x * x; }
vec3 srgbToLinear(vec3 c) { return pow(max(c, vec3(0.0)), vec3(2.2)); }
vec3 linearToSrgb(vec3 c) { return pow(max(c, vec3(0.0)), vec3(1.0 / 2.2)); }

// ---- octahedral normal encoding (world space normal <-> 2 x 16 bit) -------------------
vec2 hcOctWrap(vec2 v) {
    return (1.0 - abs(v.yx)) * vec2(v.x >= 0.0 ? 1.0 : -1.0, v.y >= 0.0 ? 1.0 : -1.0);
}
vec2 encodeNormal(vec3 n) {
    n /= (abs(n.x) + abs(n.y) + abs(n.z) + 1e-6);
    vec2 p = n.z >= 0.0 ? n.xy : hcOctWrap(n.xy);
    return p * 0.5 + 0.5;
}
vec3 decodeNormal(vec2 f) {
    f = f * 2.0 - 1.0;
    vec3 n = vec3(f.x, f.y, 1.0 - abs(f.x) - abs(f.y));
    float t = saturate(-n.z);
    n.x += n.x >= 0.0 ? -t : t;
    n.y += n.y >= 0.0 ? -t : t;
    return normalize(n);
}

// ---- material ids (colortex2.r * 255) --------------------------------------------------
#define MAT_NONE 0
#define MAT_TERRAIN 1
#define MAT_FOLIAGE 2
#define MAT_WATER 3
#define MAT_GLASS 4
#define MAT_EMISSIVE 5
#define MAT_ENTITY 6
#define MAT_HAND 7
#define MAT_PARTICLE 8
#define MAT_GLOW 9
#define MAT_SNOW 10
#define MAT_ICE 11
#define MAT_LAVA 12
#define MAT_SKY 13

float packMat(int id) { return (float(id) + 0.5) / 255.0; }
int unpackMat(float v) { return int(v * 255.0); }

// ---- ordered dithering -----------------------------------------------------------------
float bayer2(vec2 a) { a = floor(a); return fract(a.x * 0.5 + a.y * a.y * 0.75); }
float bayer4(vec2 a) { return bayer2(0.5 * a) * 0.25 + bayer2(a); }
float bayer8(vec2 a) { return bayer4(0.5 * a) * 0.25 + bayer2(a); }
float bayer16(vec2 a) { return bayer8(0.5 * a) * 0.25 + bayer2(a); }
float bayer32(vec2 a) { return bayer16(0.5 * a) * 0.25 + bayer2(a); }
float bayer64(vec2 a) { return bayer32(0.5 * a) * 0.25 + bayer2(a); }

// ---- hashes / noise --------------------------------------------------------------------
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}
float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * valueNoise(p);
        p = p * 2.03 + vec2(17.3, 9.1);
        a *= 0.5;
    }
    return v;
}
float noiseTex(vec2 uv) { return texture(noisetex, uv).r; }

// ---- space transforms -------------------------------------------------------------------
float linearDepth(float depth) {
    return (2.0 * near) / (far + near - depth * (far - near));
}
// screen uv + raw depth -> view space position
vec3 screenToView(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 v = gbufferProjectionInverse * clip;
    return v.xyz / v.w;
}
// view space -> screen uv (and depth)
vec3 viewToScreen(vec3 viewPos) {
    vec4 clip = gbufferProjection * vec4(viewPos, 1.0);
    return (clip.xyz / clip.w) * 0.5 + 0.5;
}
vec3 viewToPlayer(vec3 viewPos) { return (gbufferModelViewInverse * vec4(viewPos, 1.0)).xyz; }
vec3 playerToView(vec3 playerPos) { return (gbufferModelView * vec4(playerPos, 1.0)).xyz; }
vec3 viewDirToWorld(vec3 v) { return normalize(mat3(gbufferModelViewInverse) * v); }

// ---- light directions (world space) -----------------------------------------------------
vec3 sunDirWorld() { return viewDirToWorld(sunPosition); }
vec3 moonDirWorld() { return viewDirToWorld(moonPosition); }
vec3 lightDirWorld() { return viewDirToWorld(shadowLightPosition); }

// ---- lightmap normalisation ------------------------------------------------------------
vec2 normalizeLightmap(vec2 lm) {
    return saturate((lm - 1.0 / 32.0) * (32.0 / 30.0));
}

// ---- misc ------------------------------------------------------------------------------
float henyeyGreenstein(float cosTheta, float g) {
    float g2 = g * g;
    return (1.0 - g2) / (4.0 * PI * pow(1.0 + g2 - 2.0 * g * cosTheta, 1.5));
}
mat2 rot2(float a) {
    float s = sin(a), c = cos(a);
    return mat2(c, -s, s, c);
}
