// ============================================================== shared uniforms + helpers
uniform vec3 cameraPosition;
uniform vec3 sunPosition;
uniform vec3 shadowLightPosition;
uniform vec3 upPosition;
uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform float frameTimeCounter;
uniform float rainStrength;
uniform float viewWidth;
uniform float viewHeight;
uniform float nightVision;
uniform float far;
uniform int frameCounter;
uniform int isEyeInWater;
uniform ivec2 eyeBrightnessSmooth;

const float PI = 3.14159265;

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
vec3 toLinear(vec3 c) { return pow(c, vec3(2.2)); }
vec3 toSRGB(vec3 c) { return pow(max(c, 0.0), vec3(1.0 / 2.2)); }

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
// interleaved gradient noise, animated
float ign(vec2 px) {
    px += 5.588238 * float(frameCounter % 64);
    return fract(52.9829189 * fract(dot(px, vec2(0.06711056, 0.00583715))));
}

vec3 sunDirW() { return normalize(mat3(gbufferModelViewInverse) * sunPosition); }
vec3 lightDirW() { return normalize(mat3(gbufferModelViewInverse) * shadowLightPosition); }

// ---- time of day from the sun elevation (sin of the angle above the horizon)
float tDay(float h)    { return smoothstep(0.0, 0.32, h); }
float tGolden(float h) { return smoothstep(-0.07, 0.02, h) * (1.0 - smoothstep(0.06, 0.34, h)); }
float tBlue(float h)   { return smoothstep(-0.24, -0.10, h) * (1.0 - smoothstep(-0.06, 0.02, h)); }
float tNight(float h)  { return 1.0 - smoothstep(-0.26, -0.06, h); }

// ---- scenes along the journey (by position on the map, x grows from the village to Kobe)
float band(float x, float a, float b, float s) { return smoothstep(a - s, a + s, x) * (1.0 - smoothstep(b - s, b + s, x)); }
float rVillage() { return 1.0 - smoothstep(410.0, 490.0, cameraPosition.x); }
float rTown()    { return band(cameraPosition.x, 450.0, 800.0, 40.0); }
float rMount()   { return band(cameraPosition.x, 800.0, 1800.0, 60.0); }
float rSea()     { return band(cameraPosition.x, 1800.0, 3150.0, 80.0); }
float rKobe()    { return smoothstep(3070.0, 3230.0, cameraPosition.x); }

// ---- light colours
vec3 sunLight(float h) {
    vec3 noon = vec3(1.00, 0.93, 0.84) * 2.7;
    vec3 low  = vec3(1.00, 0.46, 0.17) * 2.0;
    vec3 c = mix(low, noon, smoothstep(0.03, 0.45, h)) * smoothstep(-0.035, 0.05, h);
    vec3 moon = vec3(0.32, 0.45, 0.80) * 0.16 * smoothstep(-0.03, -0.14, h);
    return (h > -0.03 ? c : moon) * (1.0 - rainStrength * 0.88);
}
vec3 ambientSky(float h) {
    vec3 day = vec3(0.40, 0.54, 0.82) * 0.95;
    vec3 gold = vec3(0.62, 0.44, 0.46) * 0.62;
    vec3 blue = vec3(0.16, 0.20, 0.42) * 0.30;
    vec3 night = vec3(0.045, 0.065, 0.13) * 0.55;
    vec3 c = night;
    c = mix(c, blue, smoothstep(-0.26, -0.10, h));
    c = mix(c, gold, smoothstep(-0.08, 0.02, h));
    c = mix(c, day, smoothstep(0.06, 0.35, h));
    c = mix(c, vec3(luma(c)) * vec3(0.85, 0.92, 1.0), rainStrength * 0.7);
    return c;
}
const vec3 LAMP = vec3(1.00, 0.56, 0.24);      // warm 2700 K street lamps, chochin, windows

vec2 encodeNormal(vec3 n) {
    n /= abs(n.x) + abs(n.y) + abs(n.z);
    vec2 e = n.z >= 0.0 ? n.xy : (1.0 - abs(n.yx)) * vec2(n.x >= 0.0 ? 1.0 : -1.0, n.y >= 0.0 ? 1.0 : -1.0);
    return e * 0.5 + 0.5;
}
vec3 decodeNormal(vec2 e) {
    e = e * 2.0 - 1.0;
    vec3 n = vec3(e, 1.0 - abs(e.x) - abs(e.y));
    float t = max(-n.z, 0.0);
    n.xy += vec2(n.x >= 0.0 ? -t : t, n.y >= 0.0 ? -t : t);
    return normalize(n);
}
