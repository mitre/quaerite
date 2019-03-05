package org.mitre.quaerite.cli;

import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TestGenerateExperiments {
    static Path JSON;
    @BeforeAll
    public static void setUp() throws Exception {
        JSON = Files.createTempFile("quaerite-", ".json");
        Files.copy(
                TestGenerateExperiments.class.getClass().getResourceAsStream("/test-documents/qf.json"),
                JSON, StandardCopyOption.REPLACE_EXISTING);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        Files.delete(JSON);
    }

    @Test
    public void testSimple() throws Exception {
        GenerateExperiments.main(new String[]{
                "-i", JSON.toAbsolutePath().toString(),
                "-o", "C:/data/tmp.json"
        });
    }
}
