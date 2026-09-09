#version 450

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inColor;
layout(location = 2) in float inMaterial;

layout(location = 0) out vec3 fragColor;

layout(push_constant) uniform PushConstants {
    mat4 mvp;       // offset 0,  size 64
    vec3 camPos;    // offset 64, size 12
    float time;     // offset 76, size 4  (total 80)
} pc;

// Vertical bob for the water surface. The wave is sampled at block centers
// (floor(worldPos - 0.5) + 0.5), so adjacent quads share the same height at
// their shared edge: the result is a continuous, crack-free wave surface.
float waterWave(vec2 p, float t) {
    float h = 0.0;
    h += sin(p.x * 0.60 + t * 1.10) * 0.10;
    h += sin(p.y * 0.45 + t * 0.85) * 0.10;
    h += sin((p.x + p.y) * 0.35 + t * 0.60) * 0.06;
    return h;
}

void main() {
    vec3 worldPos = inPosition;
    vec3 color = inColor;

    if (inMaterial > 0.5) {
        // Water surface: animate the height and brighten toward the horizon
        // (fresnel / grazing light) so far water looks like sky reflection.
        vec2 bp = vec2(floor(worldPos.x - 0.5) + 0.5, floor(worldPos.z - 0.5) + 0.5);
        worldPos.y += waterWave(bp, pc.time);

        vec3 viewDir = normalize(pc.camPos - worldPos);
        float ndv = max(dot(viewDir, vec3(0.0, 1.0, 0.0)), 0.0);
        float fresnel = pow(1.0 - ndv, 2.5);
        vec3 deep = vec3(0.15, 0.40, 0.90);
        vec3 sky  = vec3(0.75, 0.88, 1.00);
        color = mix(deep, sky, fresnel * 0.9);
    }

    gl_Position = pc.mvp * vec4(worldPos, 1.0);
    fragColor = color;
}
