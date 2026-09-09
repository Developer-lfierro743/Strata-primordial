#define CIMGUI_DEFINE_ENUMS_AND_STRUCTS
#include <cimgui.h>

int main() {
    ImGuiContext* ctx = igCreateContext(nullptr);
    if (ctx) {
        igDestroyContext(ctx);
    }
    return 0;
}
