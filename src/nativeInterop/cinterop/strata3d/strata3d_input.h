/**
 * Strata3D Input Module
 * =====================
 * SDL3 input abstraction for keyboard, mouse, and window events.
 * 
 * Strata3D is a proprietary rendering library for the Strata voxel engine.
 */

#ifndef STRATA3D_INPUT_H
#define STRATA3D_INPUT_H

#include <SDL3/SDL.h>
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/* =========================================================================
 * Key Codes (matching SDL3)
 * ========================================================================= */

#define STR3D_KEY_W          0x57
#define STR3D_KEY_A          0x41
#define STR3D_KEY_S          0x53
#define STR3D_KEY_D          0x44
#define STR3D_KEY_SPACE      0x20
#define STR3D_KEY_SHIFT      0x400000E1
#define STR3D_KEY_F1         0x4000003A
#define STR3D_KEY_F2         0x4000003B
#define STR3D_KEY_F3         0x4000003C
#define STR3D_KEY_F4         0x4000003D
#define STR3D_KEY_F5         0x4000003E
#define STR3D_KEY_F11        0x40000046
#define STR3D_KEY_TAB        0x09
#define STR3D_KEY_ESCAPE     0x1B
#define STR3D_KEY_RETURN     0x0D
#define STR3D_KEY_F6         0x4000003F
#define STR3D_KEY_BACKSPACE  0x08

/* =========================================================================
 * Event Types
 * ========================================================================= */

#define STR3D_EVENT_QUIT             0x100
#define STR3D_EVENT_WINDOW_RESIZED   0x206
#define STR3D_EVENT_KEY_DOWN         0x300
#define STR3D_EVENT_KEY_UP           0x301
#define STR3D_EVENT_TEXT_INPUT       0x303

/* =========================================================================
 * Input State
 * ========================================================================= */

typedef struct {
    /* Window state */
    int         windowWidth;
    int         windowHeight;
    int         windowWidthPixels;
    int         windowHeightPixels;
    bool        quitRequested;
    bool        relativeMouse;
    bool        textInputActive;
    
    /* Mouse state */
    int         mouseX;
    int         mouseY;
    
    /* Event buffers */
    uint32_t    eventType;
    uint32_t    eventKey;
    char        textInput[32];
} Str3D_Input;

/* =========================================================================
 * Input API
 * ========================================================================= */

/**
 * Initializes the input state.
 */
static inline void str3d_input_init(Str3D_Input* input) {
    memset(input, 0, sizeof(Str3D_Input));
}

/**
 * Polls for a single SDL event.
 * Returns the event type (0 if no event).
 * 
 * This should be called in a loop during the game loop:
 *   while (str3d_input_poll(&input)) { ... }
 */
static inline uint32_t str3d_input_poll(Str3D_Input* input, SDL_Window* window) {
    SDL_Event event;
    if (!SDL_PollEvent(&event)) return 0;
    
    input->eventType = event.type;
    
    switch (event.type) {
        case SDL_EVENT_QUIT:
            input->quitRequested = true;
            break;
            
        case SDL_EVENT_WINDOW_RESIZED:
            SDL_GetWindowSize(window, &input->windowWidth, &input->windowHeight);
            break;
            
        case SDL_EVENT_KEY_DOWN:
            input->eventKey = event.key.key;
            if (input->eventKey == STR3D_KEY_F11) {
                Uint32 flags = SDL_GetWindowFlags(window);
                if (flags & SDL_WINDOW_FULLSCREEN) {
                    SDL_SetWindowFullscreen(window, false);
                } else {
                    SDL_SetWindowFullscreen(window, true);
                }
            }
            break;
            
        case SDL_EVENT_KEY_UP:
            input->eventKey = event.key.key;
            break;
            
        case SDL_EVENT_TEXT_INPUT:
            strncpy(input->textInput, event.text.text, sizeof(input->textInput) - 1);
            input->textInput[sizeof(input->textInput) - 1] = '\0';
            break;
    }
    
    return event.type;
}

/**
 * Returns true if the given key is currently pressed.
 */
static inline bool str3d_key_down(uint32_t key) {
    return SDL_GetKeyboardState(NULL)[key];
}

/**
 * Gets the current mouse position.
 */
static inline void str3d_get_mouse(int* x, int* y) {
    SDL_GetMouseState((float*)x, (float*)y);
}

/**
 * Sets relative mouse mode (for FPS camera).
 */
static inline void str3d_set_relative_mouse(SDL_Window* window, bool relative) {
    SDL_SetWindowRelativeMouseMode(window, relative);
}

/**
 * Enables or disables text input (for chat/console).
 */
static inline void str3d_set_text_input(SDL_Window* window, bool enabled) {
    if (enabled) {
        SDL_StartTextInput(window);
    } else {
        SDL_StopTextInput(window);
    }
}

/**
 * Gets the current window size in logical units.
 */
static inline void str3d_get_window_size(SDL_Window* window, int* w, int* h) {
    SDL_GetWindowSize(window, w, h);
}

/**
 * Gets the current window size in physical pixels (for Vulkan extent).
 */
static inline void str3d_get_window_size_pixels(SDL_Window* window, int* w, int* h) {
    SDL_GetWindowSizeInPixels(window, w, h);
}

/**
 * Gets SDL ticks in milliseconds.
 */
static inline uint32_t str3d_get_ticks(void) {
    return SDL_GetTicks();
}

#ifdef __cplusplus
}
#endif

#endif /* STRATA3D_INPUT_H */
