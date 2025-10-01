package ru.sarkolsss.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String GRAY = "\u001B[90m";
    private static final String BOLD = "\u001B[1m";

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private static String timestamp() {
        return GRAY + "[" + LocalDateTime.now().format(TIME_FORMATTER) + "] " + RESET;
    }

    public static void info(String message) {
        System.out.println(timestamp() + CYAN + BOLD + "[INFO]" + RESET + " " + message);
    }

    public static void step(String message) {
        System.out.println(timestamp() + BLUE + BOLD + "[STEP]" + RESET + " " + message);
    }

    public static void detail(String message) {
        System.out.println(timestamp() + GRAY + "  → " + message + RESET);
    }

    public static void compile(String message) {
        if (!message.trim().isEmpty() && !message.contains("Microsoft") &&
                !message.contains("Copyright")) {
            System.out.println(timestamp() + GRAY + "    " + message + RESET);
        }
    }

    public static void success(String message) {
        System.out.println(timestamp() + GREEN + BOLD + "[SUCCESS]" + RESET + " ✓ " + message);
    }

    public static void error(String message) {
        System.err.println(timestamp() + RED + BOLD + "[ERROR]" + RESET + " ✗ " + message);
    }

    public static void cleanup(String message) {
        System.out.println(timestamp() + YELLOW + "[CLEANUP]" + RESET + " " + message);
    }

    public static void warning(String message) {
        System.out.println(timestamp() + YELLOW + BOLD + "[WARNING]" + RESET + " " + message);
    }
}