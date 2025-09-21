cmake_minimum_required(VERSION 3.10)
project(voicechanger-native)

# Set the library name
add_library(native_audio SHARED
    src/main/cpp/native_audio.cpp
)

find_library(log-lib log)

target_include_directories(native_audio PRIVATE ${CMAKE_SOURCE_DIR}/src/main/cpp)
target_link_libraries(native_audio ${log-lib})

# Use FetchContent to pull Oboe for low-latency audio
include(FetchContent)
FetchContent_Declare(
    oboe
    GIT_REPOSITORY https://github.com/oboe/oboe.git
    GIT_TAG        v1.7.3
)
FetchContent_MakeAvailable(oboe)

target_link_libraries(native_audio oboe)

set_target_properties(native_audio PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON)
