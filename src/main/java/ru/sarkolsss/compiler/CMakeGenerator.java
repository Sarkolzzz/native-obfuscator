package ru.sarkolsss.compiler;

import ru.sarkolsss.utils.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CMakeGenerator {
    private final Path workDir;

    public CMakeGenerator(Path workDir) {
        this.workDir = workDir;
    }

    public void generate() {
        Path cmakeFile = workDir.resolve("cpp_src").resolve("CMakeLists.txt");

        StringBuilder cmake = new StringBuilder();
        cmake.append("cmake_minimum_required(VERSION 3.20)\n");
        cmake.append("project(java2cpp_native)\n\n");
        cmake.append("set(CMAKE_CXX_STANDARD 17)\n");
        cmake.append("set(CMAKE_CXX_STANDARD_REQUIRED ON)\n\n");
        cmake.append("find_package(JNI REQUIRED)\n\n");
        cmake.append("include_directories(${JNI_INCLUDE_DIRS})\n\n");
        cmake.append("add_library(java2cpp_native SHARED native.cpp)\n");
        cmake.append("target_link_libraries(java2cpp_native ${JNI_LIBRARIES})\n\n");
        cmake.append("set_target_properties(java2cpp_native PROPERTIES\n");
        cmake.append("    OUTPUT_NAME \"java2cpp_native\"\n");
        cmake.append("    PREFIX \"\"\n");
        cmake.append("    SUFFIX \".dll\"\n");
        cmake.append(")\n");

        try {
            Files.writeString(cmakeFile, cmake.toString());
            Logger.detail("CMakeLists.txt generated");
        } catch (IOException e) {
            Logger.error("Failed to generate CMake file: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}