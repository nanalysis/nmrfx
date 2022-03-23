package org.nmrfx.processor.datasets.vendor.rs2d;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RS2DProcUtil {
    private static final Logger log = LoggerFactory.getLogger(RS2DProcUtil.class);

    public static Optional<Path> getLastProcPath(Path datasetDir) {
        return findLastProcId(datasetDir).stream()
                .mapToObj(id -> datasetDir.resolve(RS2DData.PROC_DIR).resolve(String.valueOf(id)))
                .findFirst();
    }

    public static List<Integer> listProcIds(Path datasetDir) {
        try (Stream<Path> fileStream = Files.list(datasetDir.resolve(RS2DData.PROC_DIR))) {
            return fileStream.filter(Files::isDirectory)
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(StringUtils::isNumeric)
                    .mapToInt(Integer::parseInt)
                    .sorted()
                    .boxed()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("Unable to list processed directories", e);
            return Collections.emptyList();
        }
    }

    public static int findNextProcId(Path datasetDir) {
        OptionalInt max = findLastProcId(datasetDir);
        return max.isPresent() ? max.getAsInt() + 1 : 0;
    }

    public static Path findNextProcPath(Path datasetDir) {
        return datasetDir.resolve(RS2DData.PROC_DIR).resolve(String.valueOf(findNextProcId(datasetDir)));
    }

    /**
     * Get the last process id for this dataset.
     * Returns empty (not null) if no valid process dir was found.
     */
    public static OptionalInt findLastProcId(Path datasetDir) {
        try (Stream<Path> fileStream = Files.list(datasetDir.resolve(RS2DData.PROC_DIR))) {
            return fileStream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(StringUtils::isNumeric)
                    .mapToInt(Integer::parseInt)
                    .max();
        } catch (IOException e) {
            log.warn("Unable to list processed directories", e);
            return OptionalInt.empty();
        }
    }
}
