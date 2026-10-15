cmake_minimum_required(VERSION 3.22.1)
project(voxel_native LANGUAGES CXX)

add_library(voxel_native SHARED
        voxel_mesher.cpp
        voxel_volume.cpp
        audio_synth.cpp)

target_compile_features(voxel_native PRIVATE cxx_std_17)
target_compile_options(voxel_native PRIVATE -Wall -Wextra -Wno-unused-parameter)

find_library(log-lib log)
target_link_libraries(voxel_native ${log-lib})
