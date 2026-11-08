cmake_minimum_required(VERSION 3.22.1)

project(nexora_native LANGUAGES C CXX)

add_library(
    nexora_native
    SHARED
    native_bridge.cpp
)

target_compile_features(nexora_native PRIVATE cxx_std_17)

# mGBA will be integrated here as a pinned upstream dependency after its
# Android-specific build options and required dependencies are validated.
