#version 330 compatibility
out vec4 glcolor;
void main() {
    glcolor = gl_Color;
    gl_Position = ftransform();
}
