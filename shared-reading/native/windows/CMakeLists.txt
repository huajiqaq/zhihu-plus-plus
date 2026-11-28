# Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
# SPDX-License-Identifier: AGPL-3.0-only
cmake_minimum_required(VERSION 3.24)
project(ZhihuReadingHost LANGUAGES CXX)
if(NOT WIN32)
    message(FATAL_ERROR "The Windows speech host requires the Windows SDK")
endif()
add_executable(zhihu-reading-host ReadingHost.cpp)
target_compile_features(zhihu-reading-host PRIVATE cxx_std_20)
target_compile_definitions(zhihu-reading-host PRIVATE UNICODE _UNICODE NOMINMAX WIN32_LEAN_AND_MEAN)
target_compile_options(zhihu-reading-host PRIVATE /EHsc /utf-8 /W4)
target_link_libraries(zhihu-reading-host PRIVATE windowsapp)
set_target_properties(zhihu-reading-host PROPERTIES
    MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/out"
    RUNTIME_OUTPUT_DIRECTORY_RELEASE "${CMAKE_BINARY_DIR}/out"
    RUNTIME_OUTPUT_DIRECTORY_DEBUG "${CMAKE_BINARY_DIR}/out")
