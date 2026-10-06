# Mac (host) build of the reference-memory embedding tool. Mirrors app/src/main/cpp/CMakeLists.txt so the
# vectors match the phone's: static llama/ggml/mtmd from the same submodule, CPU only (no Metal, no
# Accelerate BLAS), the same ARM feature level as the Pixel 8 build (the M4 supports all of it).
cmake_minimum_required(VERSION 3.22)
project(refmem_tools C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../third_party/llama.cpp)

set(BUILD_SHARED_LIBS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD      ON  CACHE BOOL "" FORCE)
set(LLAMA_CURL            OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL         OFF CACHE BOOL "" FORCE)

set(GGML_NATIVE           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP           OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE        OFF CACHE BOOL "" FORCE)
set(GGML_METAL            OFF CACHE BOOL "" FORCE)
set(GGML_BLAS             OFF CACHE BOOL "" FORCE)
set(GGML_ACCELERATE       OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+i8mm+fp16" CACHE STRING "" FORCE)

add_subdirectory(${LLAMA_DIR} build-llama)

add_executable(embed_images embed_images.cpp)
target_include_directories(embed_images PRIVATE ${LLAMA_DIR}/include ${LLAMA_DIR}/tools/mtmd)
target_link_libraries(embed_images PRIVATE mtmd llama ggml)
