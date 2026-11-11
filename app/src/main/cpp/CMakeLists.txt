cmake_minimum_required(VERSION 3.22.1)

project(nexora_native LANGUAGES C CXX)

include(FetchContent)

# Build the upstream GBA + GB/GBC cores. Keep mGBA as a separate shared library
# so the LGPL-licensed blip_buf component is not statically folded into NEXORA.
set(LIBMGBA_ONLY ON CACHE BOOL "Build only the mGBA library" FORCE)
set(M_CORE_GBA ON CACHE BOOL "Build the Game Boy Advance core" FORCE)
set(M_CORE_GB ON CACHE BOOL "Build the Game Boy and Game Boy Color core" FORCE)
set(DISABLE_DEPS ON CACHE BOOL "Avoid optional desktop dependencies" FORCE)
set(ENABLE_SCRIPTING OFF CACHE BOOL "Disable scripting for the Android core" FORCE)
set(BUILD_STATIC OFF CACHE BOOL "Use a shared mGBA library" FORCE)
set(BUILD_SHARED ON CACHE BOOL "Use a shared mGBA library" FORCE)
set(BUILD_TEST OFF CACHE BOOL "Disable upstream test executables in the app build" FORCE)
set(BUILD_SUITE OFF CACHE BOOL "Disable upstream test suite in the app build" FORCE)
set(BUILD_CINEMA OFF CACHE BOOL "Disable upstream video tests in the app build" FORCE)
set(BUILD_QT OFF CACHE BOOL "Do not build the Qt frontend" FORCE)
set(BUILD_SDL OFF CACHE BOOL "Do not build the SDL frontend" FORCE)
set(BUILD_EXAMPLE OFF CACHE BOOL "Do not build example frontends" FORCE)
set(BUILD_PERF OFF CACHE BOOL "Do not build profiling tools" FORCE)
set(BUILD_PYTHON OFF CACHE BOOL "Do not build Python bindings" FORCE)

FetchContent_Declare(
    mgba
    GIT_REPOSITORY https://github.com/mgba-emu/mgba.git
    GIT_TAG 26b7884bc25a5933960f3cdcd98bac1ae14d42e2
    GIT_SHALLOW FALSE
)

FetchContent_MakeAvailable(mgba)

if(NOT TARGET mgba)
    message(FATAL_ERROR "The pinned mGBA source did not create the expected 'mgba' target.")
endif()

set_target_properties(mgba PROPERTIES POSITION_INDEPENDENT_CODE ON VERSION "" SOVERSION "")

add_library(
    nexora_native
    SHARED
    native_bridge.cpp
)

target_compile_features(nexora_native PRIVATE cxx_std_17)
target_compile_definitions(nexora_native PRIVATE NEXORA_MGBA_VERSION="0.10.5")
target_link_libraries(nexora_native PRIVATE mgba log)
