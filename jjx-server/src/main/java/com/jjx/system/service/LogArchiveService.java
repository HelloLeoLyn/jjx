// ==================== LogArchiveService.java ====================
package com.jjx.system.service;


import cn.hutool.json.JSONUtil;
import com.jjx.system.config.LogCleanProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogArchiveService {

    private final LogCleanProperties properties;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public void archiveOperLogs(List<?> logs) {
        archiveToFile("oper", logs);
    }

    public void archiveLoginLogs(List<?> logs) {
        archiveToFile("login", logs);
    }

    public void archiveErrorLogs(List<?> logs) {
        archiveToFile("error", logs);
    }

    private void archiveToFile(String subDir, List<?> logs) {
        if (!properties.getArchiveBeforeDelete() || logs.isEmpty()) {
            return;
        }

        try {
            Path archiveDir = Paths.get(properties.getArchivePath(), subDir,
                    String.valueOf(LocalDate.now().getYear()),
                    String.format("%02d", LocalDate.now().getMonthValue()));
            Files.createDirectories(archiveDir);

            String fileName = String.format("%s_log_%s.jsonl", subDir, LocalDate.now().format(DATE_FORMAT));
            Path filePath = archiveDir.resolve(fileName);

            try (BufferedWriter writer = Files.newBufferedWriter(filePath,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (Object log : logs) {
                    writer.write(JSONUtil.toJsonStr(log));
                    writer.newLine();
                }
            }
            log.info("归档日志: {}, 条数: {}", filePath, logs.size());
        } catch (IOException e) {
            log.error("归档失败", e);
        }
    }
}

