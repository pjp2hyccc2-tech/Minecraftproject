#version 150

uniform sampler2D Sampler0;
uniform float Warmth;
uniform float Saturation;
uniform float Vignette;
uniform float Contrast;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(Sampler0, texCoord).rgb;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(l), c, Saturation);
    // warm: golden highlights, soft amber shadows / cool: blue shadows, clean highlights
    vec3 warmHi = vec3(1.10, 1.01, 0.84);
    vec3 warmLo = vec3(1.06, 0.98, 0.90);
    vec3 coolHi = vec3(0.95, 1.00, 1.06);
    vec3 coolLo = vec3(0.86, 0.95, 1.14);
    vec3 tint;
    if (Warmth >= 0.0) {
        tint = mix(vec3(1.0), mix(warmLo, warmHi, l), Warmth);
    } else {
        tint = mix(vec3(1.0), mix(coolLo, coolHi, l), -Warmth);
    }
    c *= tint;
    vec3 s = c * c * (3.0 - 2.0 * c);
    c = mix(c, s, Contrast);
    vec2 d = texCoord - 0.5;
    float v = 1.0 - Vignette * smoothstep(0.30, 0.80, length(d * vec2(1.25, 1.0)));
    fragColor = vec4(clamp(c * v, 0.0, 1.0), 1.0);
}
