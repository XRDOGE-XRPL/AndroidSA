cmake_minimum_required(VERSION 3.22.1)
project(androidsa_native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(
    androidsa
    SHARED
    native-lib.cpp
    native/network/RpcClient.cpp
    native/logging/NativeLogger.cpp
)

target_include_directories(
    androidsa
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)
