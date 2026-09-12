cmake_minimum_required(VERSION 3.22.1)
project(androidsa LANGUAGES CXX)

add_library(
    androidsa
    SHARED
    native-lib.cpp
    native/logging/Logger.cpp
    native/network/ClientState.cpp
)

target_compile_features(androidsa PRIVATE cxx_std_20)

find_library(log-lib log)

target_include_directories(androidsa PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(androidsa PRIVATE ${log-lib})
