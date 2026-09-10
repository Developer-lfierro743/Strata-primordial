/**
 * Strata3D Camera Module
 * ======================
 * First-person camera with mouse look and WASD movement.
 * 
 * Strata3D is a proprietary rendering library for the Strata voxel engine.
 */

#ifndef STRATA3D_CAMERA_H
#define STRATA3D_CAMERA_H

#include "strata3d_math.h"

#ifdef __cplusplus
extern "C" {
#endif

/* =========================================================================
 * Camera
 * ========================================================================= */

typedef struct {
    /* Position */
    float x, y, z;
    
    /* Orientation */
    float yaw;      /* Horizontal rotation (radians) */
    float pitch;    /* Vertical rotation (radians, clamped) */
    
    /* Movement speeds */
    float moveSpeed;
    float sensitivity;
    
    /* Derived vectors (updated by str3d_camera_update) */
    Str3D_Vec3 forward;
    Str3D_Vec3 right;
    Str3D_Vec3 up;
} Str3D_Camera;

/**
 * Initializes the camera at a default position.
 */
static inline void str3d_camera_init(Str3D_Camera* cam, float x, float y, float z, float yaw, float pitch) {
    cam->x = x;
    cam->y = y;
    cam->z = z;
    cam->yaw = yaw;
    cam->pitch = pitch;
    cam->moveSpeed = 12.0f;
    cam->sensitivity = 0.002f;
    
    /* Compute initial vectors */
    cam->forward.x = cosf(pitch) * sinf(yaw);
    cam->forward.y = sinf(pitch);
    cam->forward.z = cosf(pitch) * cosf(yaw);
    cam->right = str3d_vec3_normalize(str3d_vec3_cross(cam->forward, str3d_vec3(0, 1, 0)));
    cam->up = str3d_vec3_cross(cam->right, cam->forward);
}

/**
 * Updates camera orientation from mouse deltas.
 */
static inline void str3d_camera_rotate(Str3D_Camera* cam, float dx, float dy) {
    cam->yaw += dx * cam->sensitivity;
    cam->pitch += dy * cam->sensitivity;
    
    /* Clamp pitch to prevent gimbal lock */
    if (cam->pitch >  1.5f) cam->pitch =  1.5f;
    if (cam->pitch < -1.5f) cam->pitch = -1.5f;
    
    /* Recompute direction vectors */
    cam->forward.x = cosf(cam->pitch) * sinf(cam->yaw);
    cam->forward.y = sinf(cam->pitch);
    cam->forward.z = cosf(cam->pitch) * cosf(cam->yaw);
    cam->forward = str3d_vec3_normalize(cam->forward);
    cam->right = str3d_vec3_normalize(str3d_vec3_cross(cam->forward, str3d_vec3(0, 1, 0)));
    cam->up = str3d_vec3_cross(cam->right, cam->forward);
}

/**
 * Moves the camera based on input axes.
 * forward/strafe: -1 to 1
 * deltaTime: seconds since last frame
 */
static inline void str3d_camera_move(Str3D_Camera* cam, float forward, float strafe, float dt) {
    float speed = cam->moveSpeed * dt;
    
    cam->x += (cam->forward.x * forward + cam->right.x * strafe) * speed;
    cam->y += (cam->forward.y * forward) * speed;
    cam->z += (cam->forward.z * forward + cam->right.z * strafe) * speed;
}

/**
 * Computes the view matrix for the camera.
 */
static inline Str3D_Mat4 str3d_camera_view_matrix(const Str3D_Camera* cam) {
    Str3D_Vec3 eye = { cam->x, cam->y, cam->z };
    Str3D_Vec3 center = str3d_vec3_add(eye, cam->forward);
    return str3d_mat4_lookAt(eye, center, str3d_vec3(0, 1, 0));
}

/**
 * Computes the full MVP matrix.
 * Returns column-major float[16] suitable for push constants.
 */
static inline void str3d_camera_compute_mvp(
    const Str3D_Camera* cam,
    float aspectRatio,
    float* outMvp   /* [16] */
) {
    Str3D_Mat4 view = str3d_camera_view_matrix(cam);
    Str3D_Mat4 proj = str3d_mat4_perspective(1.0472f, aspectRatio, 0.1f, 500.0f); /* ~60° FOV */
    Str3D_Mat4 mvp = str3d_mat4_multiply(proj, view);
    memcpy(outMvp, mvp.m, sizeof(float) * 16);
}

#ifdef __cplusplus
}
#endif

#endif /* STRATA3D_CAMERA_H */
