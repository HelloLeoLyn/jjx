package com.jjx.inventory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryStockMutationBoundaryTest {

    @Test
    void quantityWritesStayInsideMutationService() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().endsWith("InventoryStockMutationServiceImpl.java"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path);
                            if (source.contains("stockItemMapper.insert(")
                                    || source.contains("stockItemMapper.updateById(")
                                    || source.contains("stockItemMapper.deductStock(")) {
                                violations.add(path.toString());
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        assertTrue(violations.isEmpty(), "库存数量存在绕过统一入口的写点: " + violations);
    }
}
