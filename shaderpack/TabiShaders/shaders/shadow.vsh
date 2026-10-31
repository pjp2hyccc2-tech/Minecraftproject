#version 330 compatibility
#include "/lib/settings.glsl"
#include "/lib/shadow.glsl"

in vec4 mc_Entity;
out vec2 texcoord;
out vec4 glcolor;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    glcolor = gl_Color;
    gl_Position = ftransform();
    if (int(mc_Entity.x + 0.5) == 30) {          // water casts no shadow
        gl_Position = vec4(10.0, 10.0, 10.0, 1.0);
        return;
    }
    gl_Position.xyz = distortShadow(gl_Position.xyz);
}
