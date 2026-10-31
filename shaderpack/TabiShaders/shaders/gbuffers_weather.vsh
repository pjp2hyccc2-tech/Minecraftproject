#version 330 compatibility
out vec2 texcoord;
out vec2 lmcoord;
out vec4 glcolor;
void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = clamp(((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy - 1.0 / 32.0) * 16.0 / 15.0, 0.0, 1.0);
    glcolor = gl_Color;
    gl_Position = ftransform();
}
