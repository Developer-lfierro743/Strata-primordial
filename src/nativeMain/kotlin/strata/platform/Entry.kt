package strata.platform

import kotlinx.cinterop.*
import sdl3.*

@CName("SDL_main")
fun main() {
    println("Strata Primordial - Native entry point")
    println("KMP + SDL3 + Vulkan voxel engine")

    if (SDL_Init(SDL_INIT_VIDEO or SDL_INIT_EVENTS) != 0) {
        println("SDL_Init failed: ${SDL_GetError()?.toKString()}")
        return
    }

    val window = SDL_CreateWindow(
        "Strata Primordial",
        1280,
        720,
        SDL_WINDOW_VULKAN or SDL_WINDOW_RESIZABLE
    )

    if (window == null) {
        println("SDL_CreateWindow failed: ${SDL_GetError()?.toKString()}")
        SDL_Quit()
        return
    }

    println("SDL3 window created successfully")
    println("Vulkan support: SDL3 has Vulkan surface support enabled")

    var running = true
    val event = alloc<SDL_Event>()

    // Create game instance
    val game = strata.game.Game()

    var lastTime = SDL_GetTicks()
    var frameCount = 0

    while (running) {
        // Process events
        while (SDL_PollEvent(event.ptr) != 0) {
            when (event.type) {
                SDL_EVENT_QUIT -> running = false
                SDL_EVENT_KEY_DOWN -> {
                    when (event.key.keysym.sym) {
                        SDLK_ESCAPE -> running = false
                        SDLK_w -> game.movePlayer(0f, 0f, -1f)
                        SDLK_s -> game.movePlayer(0f, 0f, 1f)
                        SDLK_a -> game.movePlayer(-1f, 0f, 0f)
                        SDLK_d -> game.movePlayer(1f, 0f, 0f)
                    }
                }
            }
        }

        val currentTime = SDL_GetTicks()
        val deltaTime = (currentTime - lastTime) / 1000f
        lastTime = currentTime

        // Update game logic
        game.update(deltaTime)

        frameCount++

        // Print position every 60 frames (~1 second at 60fps)
        if (frameCount % 60 == 0) {
            val pos = game.getPlayerPosition()
            if (pos != null) {
                print("Position: x=%.1f y=%.1f z=%.1f\r".format(pos.x, pos.y, pos.z))
            }
        }

        SDL_Delay(16) // ~60fps
    }

    println("\nShutting down Strata Primordial")
    SDL_DestroyWindow(window)
    SDL_Quit()
}
