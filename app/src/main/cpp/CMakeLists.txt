cmake_minimum_required(VERSION 3.22.1)
project(androidsa LANGUAGES CXX)
include(CTest)

add_library(
    androidsa
    SHARED
    native-lib.cpp
    native/logging/Logger.cpp
    native/network/ClientState.cpp
)

target_compile_features(androidsa PRIVATE cxx_std_20)

target_include_directories(androidsa PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
if (ANDROID)
    find_library(log-lib log)
    target_link_libraries(androidsa PRIVATE ${log-lib})
endif()

if (NOT ANDROID)
    option(ANDROIDSA_ENABLE_NATIVE_TESTS "Build native host tests" ON)
    if (ANDROIDSA_ENABLE_NATIVE_TESTS)
        add_executable(
            client_state_test
            native/network/ClientStateTest.cpp
            native/network/ClientState.cpp
            native/logging/Logger.cpp
        )

        target_compile_features(client_state_test PRIVATE cxx_std_20)
        target_include_directories(client_state_test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
        add_test(NAME client_state_test COMMAND client_state_test)
    endif()
endif()
