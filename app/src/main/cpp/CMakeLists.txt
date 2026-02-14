# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/configure-cmake

cmake_minimum_required(VERSION 3.10.2)

# Set the path to your BoringSSL installation within the project
set(BORINGSSL_DIR ${CMAKE_CURRENT_SOURCE_DIR}/boringssl)

# Add BoringSSL include directories
include_directories(${BORINGSSL_DIR}/include)

# Add your native library
add_library( # Sets the name of the library.
             native-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             native-lib.cpp )

# Import the BoringSSL static libraries for each ABI
# CMAKE_ANDROID_ARCH_ABI will be automatically set by the NDK build system
add_library(boringssl-crypto STATIC IMPORTED)
set_target_properties(boringssl-crypto PROPERTIES IMPORTED_LOCATION
    ${BORINGSSL_DIR}/lib/${CMAKE_ANDROID_ARCH_ABI}/libcrypto.a)

add_library(boringssl-ssl STATIC IMPORTED)
set_target_properties(boringssl-ssl PROPERTIES IMPORTED_LOCATION
    ${BORINGSSL_DIR}/lib/${CMAKE_ANDROID_ARCH_ABI}/libssl.a)

# Link your native library with BoringSSL and other necessary NDK libraries
target_link_libraries( # Specifies the target library for the link command.
                       native-lib

                       # Links the BoringSSL libraries
                       boringssl-ssl
                       boringssl-crypto

                       # Other NDK libraries you might need
                       log    # For Android logging (__android_log_print)
                       m      # Math library, often needed by crypto functions
                       )
