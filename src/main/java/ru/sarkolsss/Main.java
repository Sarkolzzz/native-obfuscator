package ru.sarkolsss;

import ru.sarkolsss.core.TranspilerEngine;
import ru.sarkolsss.utils.Logger;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            Logger.error("Usage: java2cpp <input.jar> <output.jar> [skip]");
            Logger.info("Options:");
            Logger.info("  skip  - Transpile all methods without @Native annotation check");
            System.exit(1);
        }

        boolean skipAnnotationCheck = false;
        if (args.length >= 3 && "skip".equalsIgnoreCase(args[2])) {
            skipAnnotationCheck = true;
            Logger.info("Skip annotation check enabled");
        }

        TranspilerEngine engine = new TranspilerEngine(
                Paths.get(args[0]),
                Paths.get(args[1]),
                skipAnnotationCheck
        );

        engine.execute();

        Logger.success("Transpilation completed successfully!");
    }
}