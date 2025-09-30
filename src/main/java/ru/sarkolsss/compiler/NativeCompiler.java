package ru.sarkolsss.compiler;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class NativeCompiler {

    private String getJavaHome() {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) {
            javaHome = System.getProperty("java.home");
        }
        return javaHome;
    }

    private int getProcessorCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    public String compile(String cppFilePath, String outputDir) throws Exception {
        return compileWithCMake(cppFilePath, outputDir);
    }

    public String compileWithCMake(String cppFilePath, String outputDir) throws Exception {
        String baseName = new File(cppFilePath).getName().replace(".cpp", "");
        int processorCount = getProcessorCount();

        String cmake = generateCMakeLists(baseName, cppFilePath);
        Files.write(Paths.get(outputDir, "CMakeLists.txt"), cmake.getBytes());

        File buildDir = new File(outputDir, "build");
        buildDir.mkdirs();

        List<String> cmakeGenArgs = new ArrayList<>(Arrays.asList(
                "cmake", "..",
                "-DCMAKE_BUILD_TYPE=Release"
        ));

        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            if (hasVisualStudio2022()) {
                cmakeGenArgs.add("-G");
                cmakeGenArgs.add("Visual Studio 17 2022");
                cmakeGenArgs.add("-A");
                cmakeGenArgs.add("x64");
            } else if (hasVisualStudio2019()) {
                cmakeGenArgs.add("-G");
                cmakeGenArgs.add("Visual Studio 16 2019");
                cmakeGenArgs.add("-A");
                cmakeGenArgs.add("x64");
            } else if (hasNinja()) {
                cmakeGenArgs.add("-G");
                cmakeGenArgs.add("Ninja");
            }
        }

        System.out.println("Configuring with CMake...");
        ProcessBuilder cmakeGen = new ProcessBuilder(cmakeGenArgs);
        cmakeGen.directory(buildDir);
        cmakeGen.redirectErrorStream(true);

        Process genProcess = cmakeGen.start();
        printProcessOutput(genProcess);

        if (genProcess.waitFor() != 0) {
            throw new Exception("CMake generation failed");
        }

        List<String> buildArgs = new ArrayList<>(Arrays.asList(
                "cmake", "--build", ".",
                "--config", "Release"
        ));

        if (os.contains("win")) {
            buildArgs.add("--parallel");
            buildArgs.add(String.valueOf(processorCount));
            buildArgs.add("--");
            buildArgs.add("/p:CL_MPcount=" + processorCount);
        } else {
            buildArgs.add("--parallel");
            buildArgs.add(String.valueOf(processorCount));
        }

        System.out.println("Building with " + processorCount + " processors...");
        ProcessBuilder cmakeBuild = new ProcessBuilder(buildArgs);
        cmakeBuild.directory(buildDir);
        cmakeBuild.redirectErrorStream(true);

        Process buildProcess = cmakeBuild.start();
        printProcessOutput(buildProcess);

        if (buildProcess.waitFor() != 0) {
            throw new Exception("CMake build failed");
        }

        String libExtension = getLibraryExtension();
        File libFile = findFile(buildDir, baseName + libExtension);

        if (libFile == null) {
            throw new Exception("Library not found: " + baseName + libExtension);
        }

        System.out.println("Compiled: " + libFile.getAbsolutePath());
        return libFile.getAbsolutePath();
    }

    private boolean hasVisualStudio2022() {
        String programFiles = System.getenv("ProgramFiles");
        if (programFiles == null) programFiles = "C:\\Program Files";

        File vs2022 = new File(programFiles, "Microsoft Visual Studio\\2022");
        return vs2022.exists();
    }

    private boolean hasVisualStudio2019() {
        String programFiles = System.getenv("ProgramFiles");
        if (programFiles == null) programFiles = "C:\\Program Files";

        File vs2019 = new File(programFiles, "Microsoft Visual Studio\\2019");
        return vs2019.exists();
    }

    private boolean hasNinja() {
        try {
            Process process = new ProcessBuilder("ninja", "--version").start();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String generateCMakeLists(String libName, String sourceFile) {
        int processorCount = getProcessorCount();
        String os = System.getProperty("os.name").toLowerCase();

        StringBuilder cmake = new StringBuilder();
        cmake.append("cmake_minimum_required(VERSION 3.16)\n");
        cmake.append("project(").append(libName).append(" LANGUAGES CXX)\n\n");
        cmake.append("set(CMAKE_CXX_STANDARD 17)\n");
        cmake.append("set(CMAKE_CXX_STANDARD_REQUIRED ON)\n");
        cmake.append("set(CMAKE_POSITION_INDEPENDENT_CODE ON)\n");
        cmake.append("set(CMAKE_CXX_EXTENSIONS OFF)\n\n");

        if (os.contains("win")) {
            cmake.append("if(MSVC)\n");
            cmake.append("    set(CMAKE_CXX_FLAGS \"${CMAKE_CXX_FLAGS} /O2 /GL /MP").append(processorCount).append("\")\n");
            cmake.append("    set(CMAKE_SHARED_LINKER_FLAGS \"${CMAKE_SHARED_LINKER_FLAGS} /LTCG\")\n");
            cmake.append("    set(CMAKE_EXE_LINKER_FLAGS \"${CMAKE_EXE_LINKER_FLAGS} /LTCG\")\n");
            cmake.append("endif()\n\n");
        } else {
            cmake.append("set(CMAKE_CXX_FLAGS_RELEASE \"${CMAKE_CXX_FLAGS_RELEASE} -O3 -march=native -flto\")\n\n");
        }

        cmake.append("find_package(JNI REQUIRED)\n");
        cmake.append("include_directories(${JNI_INCLUDE_DIRS})\n\n");
        cmake.append("add_library(").append(libName).append(" SHARED ").append(new File(sourceFile).getName()).append(")\n");
        cmake.append("target_link_libraries(").append(libName).append(" ${JNI_LIBRARIES})\n");
        cmake.append("set_target_properties(").append(libName).append(" PROPERTIES PREFIX \"\")\n");

        return cmake.toString();
    }

    private String getLibraryExtension() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return ".dll";
        if (os.contains("mac")) return ".dylib";
        return ".so";
    }

    private File findFile(File dir, String name) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().contains(name.replace(".dll", "").replace(".so", "").replace(".dylib", ""))) {
                        String fileName = file.getName().toLowerCase();
                        if (fileName.endsWith(".dll") || fileName.endsWith(".so") || fileName.endsWith(".dylib")) {
                            return file;
                        }
                    }
                    if (file.isDirectory()) {
                        File found = findFile(file, name);
                        if (found != null) return found;
                    }
                }
            }
        }
        return null;
    }

    private void printProcessOutput(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
    }
}