#version 450

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inColor;
layout(location = 2) in float inMaterial;

layout(location = 0) out vec3 fragColor;
layout(location = 1) out float voutMaterial;
layout(location = 2) out float voutFresnel;

layout(push_constant) uniform PushConstants {
    mat4 mvp;       // offset 0,  size 64
    vec3 camPos;    // offset 64, size 12
    float time;     // offset 76, size 4  (total 80)
} pc;

// Vertical bob for the water surface. Sampled at block centers so adjacent
// quads share one height along their shared edge: a continuous wave.
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
    voutMaterial = inMaterial;

    vec2 bp = vec2(floor(worldPos.x - 0.5) + 0.5, floor(worldPos.z - 0.5) + 0.5);
    worldPos.y += waterWave(bp, pc.time);

    // Fresnel / grazing light: the more edge-on the view, the more the
    // surface reflects the sky and the more transparent it becomes.
    vec3 viewDir = normalize(pc.camPos - worldPos);
    float ndv = max(dot(viewDir, vec3(0.0, 1.0, 0.0)), 0.0);
    voutFresnel = pow(1.0 - ndv, 2.5);
    vec3 deep = vec3(0.15, 0.40, 0.90);
    vec3 sky  = vec3(0.75, 0.88, 1.00);
    color = mix(deep, sky, voutFresnel * 0.9);

    gl_Position = pc.mvp * vec4(worldPos, 1.0);
    fragColor = color;
}
