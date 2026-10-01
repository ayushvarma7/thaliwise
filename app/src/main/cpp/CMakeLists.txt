cmake_minimum_required(VERSION 3.22)
project(identify_native C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Path from app/src/main/cpp up to project root, then into the submodule.
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp)

# One static llama/ggml/mtmd build, linked into a single libvlm-bridge.so
set(BUILD_SHARED_LIBS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD      ON  CACHE BOOL "" FORCE)   # vision support (libmtmd only)
set(LLAMA_CURL            OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL         OFF CACHE BOOL "" FORCE)

# Portable Android settings from llama.cpp docs/android.md
set(GGML_NATIVE           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP           OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE        OFF CACHE BOOL "" FORCE)

# Pixel 8 (Tensor G3) supports dotprod + i8mm + fp16. Enables fast Q4_0 kernels.
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+i8mm+fp16" CACHE STRING "" FORCE)

add_subdirectory(${LLAMA_DIR} build-llama)

add_library(vlm-bridge SHARED vlm_bridge.cpp)
target_include_directories(vlm-bridge PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/tools/mtmd)
target_link_libraries(vlm-bridge PRIVATE mtmd llama ggml android log)
