#version 450

layout(location = 0) in vec3 fragColor;
layout(location = 1) in float voutMaterial;
layout(location = 2) in float voutFresnel;

layout(location = 0) out vec4 outColor;

void main() {
    // Water (material flag 1) is semi-transparent. Alpha comes straight from
    // the fresnel term computed in the vertex shader — grazing views reflect
    // the sky (nearly opaque), overhead views are clearer so the lake bed
    // shows through. Decoupled from the deep/sky palette, so tuning colors
    // can never silently change opacity. Clamped to a window so water never
    // becomes invisible or a solid sheet.
    if (voutMaterial > 0.5) {
        float alpha = 0.55 + voutFresnel * 0.30;   // 0.55 (overhead) .. 0.85 (grazing)
        outColor = vec4(fragColor, alpha);
    } else {
        outColor = vec4(fragColor, 1.0);
    }
}
