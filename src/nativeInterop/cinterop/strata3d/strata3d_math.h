/**
 * Strata3D Math Module
 * ====================
 * Matrix and vector math for 3D rendering.
 * 
 * Strata3D is a proprietary rendering library for the Strata voxel engine.
 * Inspired by Minecraft's Blaze3D, it provides a clean abstraction over Vulkan.
 */

#ifndef STRATA3D_MATH_H
#define STRATA3D_MATH_H

#include <stdint.h>
#include <stdbool.h>
#include <math.h>

#ifdef __cplusplus
extern "C" {
#endif

/* =========================================================================
 * Vector Types
 * ========================================================================= */

typedef struct { float x, y, z; } Str3D_Vec3;
typedef struct { float x, y, z, w; } Str3D_Vec4;

static inline Str3D_Vec3 str3d_vec3(float x, float y, float z) {
    return (Str3D_Vec3){ x, y, z };
}

static inline Str3D_Vec3 str3d_vec3_add(Str3D_Vec3 a, Str3D_Vec3 b) {
    return (Str3D_Vec3){ a.x + b.x, a.y + b.y, a.z + b.z };
}

static inline Str3D_Vec3 str3d_vec3_sub(Str3D_Vec3 a, Str3D_Vec3 b) {
    return (Str3D_Vec3){ a.x - b.x, a.y - b.y, a.z - b.z };
}

static inline Str3D_Vec3 str3d_vec3_scale(Str3D_Vec3 v, float s) {
    return (Str3D_Vec3){ v.x * s, v.y * s, v.z * s };
}

static inline float str3d_vec3_dot(Str3D_Vec3 a, Str3D_Vec3 b) {
    return a.x * b.x + a.y * b.y + a.z * b.z;
}

static inline Str3D_Vec3 str3d_vec3_cross(Str3D_Vec3 a, Str3D_Vec3 b) {
    return (Str3D_Vec3){
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x
    };
}

static inline float str3d_vec3_length(Str3D_Vec3 v) {
    return sqrtf(v.x * v.x + v.y * v.y + v.z * v.z);
}

static inline Str3D_Vec3 str3d_vec3_normalize(Str3D_Vec3 v) {
    float len = str3d_vec3_length(v);
    if (len < 1e-6f) return (Str3D_Vec3){ 0, 0, 0 };
    float inv = 1.0f / len;
    return (Str3D_Vec3){ v.x * inv, v.y * inv, v.z * inv };
}

/* =========================================================================
 * 4x4 Matrix (column-major, Vulkan clip space)
 * ========================================================================= */

typedef struct { float m[16]; } Str3D_Mat4;

/**
 * Returns a 4x4 identity matrix.
 */
static inline Str3D_Mat4 str3d_mat4_identity(void) {
    Str3D_Mat4 r;
    memset(&r, 0, sizeof(r));
    r.m[0] = r.m[5] = r.m[10] = r.m[15] = 1.0f;
    return r;
}

/**
 * Multiplies two 4x4 matrices: result = a * b.
 * Matrices are column-major: m[col*4 + row].
 */
static inline Str3D_Mat4 str3d_mat4_multiply(Str3D_Mat4 a, Str3D_Mat4 b) {
    Str3D_Mat4 r;
    for (int col = 0; col < 4; col++) {
        for (int row = 0; row < 4; row++) {
            float sum = 0.0f;
            for (int k = 0; k < 4; k++) {
                sum += a.m[k * 4 + row] * b.m[col * 4 + k];
            }
            r.m[col * 4 + row] = sum;
        }
    }
    return r;
}

/**
 * Creates a perspective projection matrix.
 * - fovY: vertical field of view in radians
 * - aspect: width / height
 * - zNear: near clipping plane (must be > 0)
 * - zFar: far clipping plane (must be > zNear)
 * 
 * Vulkan clip space: Z in [0, 1], Y is flipped.
 */
static inline Str3D_Mat4 str3d_mat4_perspective(float fovY, float aspect, float zNear, float zFar) {
    Str3D_Mat4 r;
    memset(&r, 0, sizeof(r));
    
    float tanHalfFov = tanf(fovY * 0.5f);
    float zRange = zFar - zNear;
    
    r.m[0]  = 1.0f / (aspect * tanHalfFov);
    r.m[5]  = -1.0f / tanHalfFov;  /* Negative for Vulkan Y-flip */
    r.m[10] = zFar / zRange;
    r.m[11] = 1.0f;
    r.m[14] = -(zFar * zNear) / zRange;
    
    return r;
}

/**
 * Creates a look-at view matrix.
 * eye: camera position
 * center: target position
 * up: up vector (typically 0, 1, 0)
 */
static inline Str3D_Mat4 str3d_mat4_lookAt(Str3D_Vec3 eye, Str3D_Vec3 center, Str3D_Vec3 up) {
    Str3D_Vec3 f = str3d_vec3_normalize(str3d_vec3_sub(center, eye));
    Str3D_Vec3 s = str3d_vec3_normalize(str3d_vec3_cross(f, up));
    Str3D_Vec3 u = str3d_vec3_cross(s, f);
    
    Str3D_Mat4 r = str3d_mat4_identity();
    r.m[0]  =  s.x;
    r.m[4]  =  s.y;
    r.m[8]  =  s.z;
    r.m[1]  =  u.x;
    r.m[5]  =  u.y;
    r.m[9]  =  u.z;
    r.m[2]  = -f.x;
    r.m[6]  = -f.y;
    r.m[10] = -f.z;
    r.m[12] = -str3d_vec3_dot(s, eye);
    r.m[13] = -str3d_vec3_dot(u, eye);
    r.m[14] =  str3d_vec3_dot(f, eye);
    
    return r;
}

/**
 * Translates a matrix by a 3D vector.
 */
static inline Str3D_Mat4 str3d_mat4_translate(Str3D_Mat4 m, Str3D_Vec3 t) {
    Str3D_Mat4 r = m;
    r.m[12] += m.m[0] * t.x + m.m[4] * t.y + m.m[8]  * t.z;
    r.m[13] += m.m[1] * t.x + m.m[5] * t.y + m.m[9]  * t.z;
    r.m[14] += m.m[2] * t.x + m.m[6] * t.y + m.m[10] * t.z;
    r.m[15] += m.m[3] * t.x + m.m[7] * t.y + m.m[11] * t.z;
    return r;
}

/**
 * Scales a matrix by a 3D vector.
 */
static inline Str3D_Mat4 str3d_mat4_scale(Str3D_Mat4 m, Str3D_Vec3 s) {
    Str3D_Mat4 r = m;
    r.m[0] *= s.x; r.m[1] *= s.x; r.m[2]  *= s.x; r.m[3]  *= s.x;
    r.m[4] *= s.y; r.m[5] *= s.y; r.m[6]  *= s.y; r.m[7]  *= s.y;
    r.m[8] *= s.z; r.m[9] *= s.z; r.m[10] *= s.z; r.m[11] *= s.z;
    return r;
}

/**
 * Transposes a 4x4 matrix.
 */
static inline Str3D_Mat4 str3d_mat4_transpose(Str3D_Mat4 m) {
    Str3D_Mat4 r;
    for (int i = 0; i < 4; i++)
        for (int j = 0; j < 4; j++)
            r.m[j * 4 + i] = m.m[i * 4 + j];
    return r;
}

#ifdef __cplusplus
}
#endif

#endif /* STRATA3D_MATH_H */
