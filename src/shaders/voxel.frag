#version 450

layout(location = 0) in vec3 fragColor;
layout(location = 0) out vec4 outColor;

void main() {
    // Opaque world pass (terrain, sky, UI shared shader): no alpha.
    // Water no longer renders here — it is drawn in the transparent pass
    // (water.vert/water.frag) with real alpha blending + fresnel.
    outColor = vec4(fragColor, 1.0);
}
