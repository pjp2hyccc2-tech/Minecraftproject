// =====================================================================================
//  MeikonShaders - deferred: paints the sky gradient behind everything opaque
//  (runs after opaque terrain / entities, before translucents, so water and glass
//  blend over the right sky). Sky objects (moon, stars) already sit in colortex0.
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

void main() {
    vec3 color = texture(colortex0, texcoord).rgb;
    float depth = texture(depthtex0, texcoord).r;
    if (depth >= 1.0) {
        vec3 dirW = viewDirToWorld(screenToView(texcoord, 1.0));
        color = skyGradient(dirW) + max(color, vec3(0.0));
    }
    /* RENDERTARGETS: 0 */
    gl_FragData[0] = vec4(color, 1.0);
}
#endif
