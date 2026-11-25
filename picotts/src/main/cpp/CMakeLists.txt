cmake_minimum_required(VERSION 3.22.1)

project("picotts")

# 16 KB page alignment for Android 15+.
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

file(GLOB PICO_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/pico/*.c")

add_library(picotts SHARED pico_jni.c ${PICO_SOURCES})
target_include_directories(picotts PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/pico")
target_link_libraries(picotts m)
