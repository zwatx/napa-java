cmake_minimum_required(VERSION 3.24)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
project(napa_jni LANGUAGES C CXX)

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE Release)
endif()

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# NAPA's C core, built by its own CMakeLists as a static, position-independent
# library and linked into napa_jni
set(BUILD_SHARED_LIBS OFF)
add_subdirectory(NAPA EXCLUDE_FROM_ALL)

find_package(JNI REQUIRED)
find_package(Java REQUIRED COMPONENTS Development)
include(UseJava)

# Generates org_photonvision_napa_NapaJNI.h from the Java bindings, so the two
# can't drift apart
add_jar(
    napa_java_headers
    SOURCES src/main/java/org/photonvision/napa/NapaJNI.java
    GENERATE_NATIVE_HEADERS napa_jni_headers
)

set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)

add_library(napa_jni SHARED src/main/native/napa_jni.cpp)
target_link_libraries(napa_jni PRIVATE napa::napa napa_jni_headers)
if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    # On Linux, export only the JNI functions, not NAPA's C API from the static
    # library
    target_link_options(napa_jni PRIVATE -Wl,--exclude-libs,ALL)
endif()
