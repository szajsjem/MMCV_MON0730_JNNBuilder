cmake_minimum_required(VERSION 3.18.0)
include(ExternalProject)
project(BeeDnnLinkLib)

# Set C++ standard
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find JNI
find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})
if (JNI_FOUND)
    message(STATUS "JAVA_INCLUDE_PATH2=${JAVA_INCLUDE_PATH2}")
else ()
    message(FATAL_ERROR "I really need JNI.")
endif ()

set(BEEDNN_LIB_PATH ${BEEDNN_INSTALL_DIR}/src/BeeDNN)
set(BEEDNN_LIB_PATH_DEBUG ${BEEDNN_INSTALL_DIR}/src/BeeDNN)

# Download and prepare external project
ExternalProject_Add(project_BeeDNN
    GIT_REPOSITORY "https://github.com/szajsjem/BeeDNN.git"
    UPDATE_COMMAND git pull "https://github.com/szajsjem/BeeDNN.git"
    CMAKE_ARGS
        -DCMAKE_INSTALL_PREFIX=${BEEDNN_INSTALL_DIR}
        -DCMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE}
        -DCMAKE_CXX_COMPILER=${CMAKE_CXX_COMPILER}
        -DCMAKE_GENERATOR=${CMAKE_GENERATOR}
        -DCMAKE_GENERATOR_PLATFORM=${CMAKE_GENERATOR_PLATFORM}
        -DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}
        -DBUILD_TESTING=OFF
    BUILD_BYPRODUCTS 
        ${BEEDNN_LIB_PATH}
        ${BEEDNN_LIB_PATH_DEBUG}
)

# Import BeeDNN library
add_library(BeeDNN STATIC IMPORTED GLOBAL)
set_target_properties(BeeDNN PROPERTIES
    IMPORTED_LOCATION ${BEEDNN_LIB_PATH}
    IMPORTED_LOCATION_DEBUG ${BEEDNN_LIB_PATH_DEBUG}
    IMPORTED_CONFIGURATIONS "RELEASE;DEBUG"
)
add_dependencies(BeeDNN project_BeeDNN)

message(STATUS ${BEEDNN_INSTALL_DIR})


# Create our JNI wrapper library
set(SOURCE_FILES
    pl_szajsjem_jni_BeeDnnLibdll.h
    pl_szajsjem_jni_BeeDnnLibdll.cpp
)
add_library(BeeDnnLibd SHARED ${SOURCE_FILES})

# Include directories for our library
target_include_directories(BeeDnnLibd PRIVATE
    ${BEEDNN_INSTALL_DIR}/src
    ${JNI_INCLUDE_DIRS}
)

# Link against BeeDNN
target_link_libraries(BeeDnnLibd PRIVATE BeeDNN)

# Ensure the library directory exists
file(MAKE_DIRECTORY ${BEEDNN_INSTALL_DIR}/src)

# Copy BeeDNN headers after build
add_custom_command(TARGET BeeDnnLibd POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_directory
    ${BEEDNN_INSTALL_DIR}/src/
    ${CMAKE_BINARY_DIR}/include/BeeDNN/
)