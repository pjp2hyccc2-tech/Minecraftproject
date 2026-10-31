#version 330 compatibility
#include "/lib/common.glsl"
out vec2 texcoord;
out vec4 glcolor;
out vec3 normalV;
out vec3 playerPos;
void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    glcolor = gl_Color;
    vec3 n = gl_NormalMatrix * gl_Normal;
    normalV = dot(n, n) > 1e-5 ? normalize(n) : vec3(0.0, 1.0, 0.0);
    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
    playerPos = (gbufferModelViewInverse * viewPos).xyz;
    gl_Position = ftransform();
}
