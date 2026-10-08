package com.deepana.saga.commondto;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentationConsistencyTest {

    private static final Pattern DOCUMENTED_TOPIC = Pattern.compile(
            "\\b(?:order|inventory|payment)\\.[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)*(?:-retry-\\d+|-dlt)?\\b");
    private static final Pattern TOPIC_LITERAL = Pattern.compile(
            "\"((?:order|inventory|payment)\\.[a-z0-9.]+)\"");
    private static final Pattern RETRY_SOURCE = Pattern.compile(
            "addRetryTopics\\(topics,\\s*\"([^\"]+)\"\\)");
    private static final Pattern CONSUMED_TOPICS = Pattern.compile(
            "CONSUMED_TOPICS\\s*=\\s*List\\.of\\((.*?)\\);", Pattern.DOTALL);
    private static final Pattern TEST_REFERENCE = Pattern.compile(
            "\\b([A-Za-z][A-Za-z0-9_]*Test)#([A-Za-z][A-Za-z0-9_]*)\\b");

    @Test
    void documentedTopicsAndFailureMatrixTestReferencesExist() throws IOException {
        Path root = repositoryRoot();
        Set<String> documentedTopics = new HashSet<>();
        for (Path markdown : documentationFiles(root)) {
            Matcher topics = DOCUMENTED_TOPIC.matcher(Files.readString(markdown, StandardCharsets.UTF_8));
            while (topics.find()) {
                documentedTopics.add(topics.group());
            }
        }

        Set<String> declaredTopics = declaredTopics(root);
        Set<String> missingTopics = new HashSet<>(documentedTopics);
        missingTopics.removeAll(declaredTopics);
        assertTrue(missingTopics.isEmpty(),
                () -> "Documented topics missing from Kafka NewTopic configurations: " + missingTopics);

        Path failureMatrix = root.resolve("docs/failure-matrix.md");
        String matrix = Files.readString(failureMatrix, StandardCharsets.UTF_8);
        List<Path> testSources = testSources(root);
        List<String> missingTests = new ArrayList<>();
        Matcher references = TEST_REFERENCE.matcher(matrix);
        while (references.find()) {
            String className = references.group(1);
            String methodName = references.group(2);
            Path source = testSources.stream()
                    .filter(path -> path.getFileName().toString().equals(className + ".java"))
                    .findFirst().orElse(null);
            if (source == null || !Pattern.compile(
                            "\\bvoid\\s+" + Pattern.quote(methodName) + "\\s*\\(")
                    .matcher(Files.readString(source, StandardCharsets.UTF_8)).find()) {
                missingTests.add(className + "#" + methodName);
            }
        }
        assertTrue(missingTests.isEmpty(),
                () -> "Failure matrix references missing test classes or methods: " + missingTests);
    }

    private Set<String> declaredTopics(Path root) throws IOException {
        Set<String> topics = new HashSet<>();
        try (var paths = Files.walk(root)) {
            for (Path source : paths
                    .filter(path -> path.getFileName().toString().equals("KafkaTopicsConfig.java"))
                    .toList()) {
                String config = Files.readString(source, StandardCharsets.UTF_8);
                Matcher literals = TOPIC_LITERAL.matcher(config);
                while (literals.find()) {
                    topics.add(literals.group(1));
                }

                Matcher retrySources = RETRY_SOURCE.matcher(config);
                while (retrySources.find()) {
                    addRetryTopics(topics, retrySources.group(1));
                }

                Matcher consumed = CONSUMED_TOPICS.matcher(config);
                if (consumed.find()) {
                    Matcher consumedLiterals = TOPIC_LITERAL.matcher(consumed.group(1));
                    while (consumedLiterals.find()) {
                        String topic = consumedLiterals.group(1);
                        topics.add(topic);
                        addRetryTopics(topics, topic);
                    }
                }
            }
        }
        return topics;
    }

    private void addRetryTopics(Set<String> topics, String source) {
        for (int attempt = 0; attempt < 3; attempt++) {
            topics.add(source + "-retry-" + attempt);
        }
        topics.add(source + "-dlt");
    }

    private List<Path> documentationFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        files.add(root.resolve("Readme.md"));
        try (var paths = Files.walk(root.resolve("docs"))) {
            files.addAll(paths.filter(path -> path.toString().endsWith(".md")).toList());
        }
        return files;
    }

    private List<Path> testSources(Path root) throws IOException {
        String testSourcePath = "src" + java.io.File.separator + "test" + java.io.File.separator + "java";
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().contains(testSourcePath)
                    && path.toString().endsWith(".java")).toList();
        }
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null
                && !(Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("Readme.md")))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Could not locate repository root from test working directory");
        }
        return current;
    }
}
