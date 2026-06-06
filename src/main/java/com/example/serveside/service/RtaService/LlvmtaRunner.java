package com.example.serveside.service.RtaService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * 负责从 Spring Boot 中调用 LLVMTA 脚本。
 *
 * 主要功能：
 * 1. 检查 LLVMTA 环境；
 * 2. 检查 testcase 必需文件；
 * 3. 从 CoreInfo.json 自动识别核心数量；
 * 4. 调用 runf.py；
 * 5. 收集运行日志；
 * 6. 返回新生成的 WCET.json 路径。
 */
@Service
public class LlvmtaRunner {

    /**
     * LLVMTA 使用固定的 dirforgdb 等中间目录。
     * 为避免多个请求互相覆盖中间结果，
     * 当前限制同一时刻只能运行一个 LLVMTA 分析。
     */
    private final ReentrantLock llvmtaLock =
            new ReentrantLock();

    private final ObjectMapper objectMapper;

    /**
     * LLVMTA 根目录。
     *
     * 默认：
     * /home/当前用户/llvmta
     */
    private final Path llvmtaHome;

    /**
     * LLVMTA testcases 目录。
     */
    private final Path llvmtaTestcasesDirectory;

    /**
     * 当前使用的运行脚本。
     *
     * 当前本地脚本名称为 runf.py。
     * 以后可以通过配置改为 run.py。
     */
    private final Path runScript;

    /**
     * Python 命令，默认 python3。
     */
    private final String pythonCommand;

    public LlvmtaRunner(
            ObjectMapper objectMapper,
            @Value("${llvmta.home:}") String configuredHome,
            @Value("${llvmta.script:runf.py}") String configuredScript,
            @Value("${llvmta.python:python3}") String configuredPython
    ) {
        this.objectMapper = objectMapper;

        String homeValue = configuredHome;

        if (homeValue == null
                || homeValue.trim().isEmpty()) {

            homeValue = Paths.get(
                    System.getProperty("user.home"),
                    "llvmta"
            ).toString();
        }

        String scriptValue = configuredScript;

        if (scriptValue == null
                || scriptValue.trim().isEmpty()) {

            scriptValue = "runf.py";
        }

        String pythonValue = configuredPython;

        if (pythonValue == null
                || pythonValue.trim().isEmpty()) {

            pythonValue = "python3";
        }

        this.llvmtaHome = Paths.get(homeValue)
                .toAbsolutePath()
                .normalize();

        this.llvmtaTestcasesDirectory =
                llvmtaHome.resolve("testcases")
                        .normalize();

        this.runScript =
                llvmtaTestcasesDirectory
                        .resolve(scriptValue)
                        .normalize();

        this.pythonCommand = pythonValue.trim();
    }

    /**
     * 运行 LLVMTA。
     *
     * 核心数量不再由前端传入，而是从：
     *
     * testcasePath/CoreInfo.json
     *
     * 自动读取。
     *
     * @param testcasePath testcase 目录
     * @param logConsumer  日志输出函数
     * @return 本次生成的 WCET.json 路径
     */
    public Path run(
            String testcasePath,
            Consumer<String> logConsumer
    ) throws IOException, InterruptedException {

        llvmtaLock.lock();

        try {
            Consumer<String> logger =
                    logConsumer == null
                            ? message -> {
                            }
                            : logConsumer;

            if (testcasePath == null
                    || testcasePath.trim().isEmpty()) {

                throw new IllegalArgumentException(
                        "testcasePath must not be empty."
                );
            }

            Path sourceDirectory =
                    Paths.get(testcasePath)
                            .toAbsolutePath()
                            .normalize();

            validateEnvironment(sourceDirectory);

            Path coreInfoFile =
                    sourceDirectory.resolve("CoreInfo.json");

            int coreCount =
                    readCoreCount(coreInfoFile);

            logger.accept(
                    "Detected core count from CoreInfo.json: "
                            + coreCount
            );

            Path outputDirectory =
                    sourceDirectory.resolve("output")
                            .normalize();

            Files.createDirectories(outputDirectory);

            Path wcetJson =
                    outputDirectory.resolve("WCET.json");

            /*
             * 删除旧结果，避免本次运行失败后，
             * 错误读取上一次生成的 WCET.json。
             */
            Files.deleteIfExists(wcetJson);

            List<String> command = Arrays.asList(
                    pythonCommand,
                    "-u",
                    runScript.toString(),
                    "-s",
                    sourceDirectory.toString(),
                    "-o",
                    outputDirectory.toString(),
                    "-n",
                    String.valueOf(coreCount)
            );

            ProcessBuilder processBuilder =
                    new ProcessBuilder(command);

            /*
             * 脚本内部使用 dirforgdb 等相对路径，
             * 因此工作目录必须是 LLVMTA 的 testcases 目录。
             */
            processBuilder.directory(
                    llvmtaTestcasesDirectory.toFile()
            );

            /*
             * 合并标准输出与标准错误，
             * 避免两个缓冲区互相阻塞。
             */
            processBuilder.redirectErrorStream(true);

            logger.accept("Starting LLVMTA...");

            logger.accept(
                    "LLVMTA home: " + llvmtaHome
            );

            logger.accept(
                    "Testcase directory: "
                            + sourceDirectory
            );

            logger.accept(
                    "Core count: " + coreCount
            );

            logger.accept(
                    "Output directory: "
                            + outputDirectory
            );

            logger.accept(
                    "Command: "
                            + String.join(" ", command)
            );

            Process process =
                    processBuilder.start();

            try (BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(
                                         process.getInputStream(),
                                         StandardCharsets.UTF_8
                                 )
                         )) {

                String line;

                while ((line = reader.readLine()) != null) {
                    logger.accept(line);
                }
            }

            int exitCode;

            try {
                exitCode = process.waitFor();

            } catch (InterruptedException exception) {
                process.destroy();

                if (process.isAlive()) {
                    process.destroyForcibly();
                }

                Thread.currentThread().interrupt();
                throw exception;
            }

            logger.accept(
                    "LLVMTA exited with code: "
                            + exitCode
            );

            if (exitCode != 0) {
                throw new IllegalStateException(
                        "LLVMTA execution failed. Exit code: "
                                + exitCode
                );
            }

            if (!Files.isRegularFile(wcetJson)) {
                throw new IllegalStateException(
                        "LLVMTA completed, but WCET.json "
                                + "was not generated: "
                                + wcetJson
                );
            }

            if (Files.size(wcetJson) == 0) {
                throw new IllegalStateException(
                        "LLVMTA generated an empty WCET.json: "
                                + wcetJson
                );
            }

            logger.accept(
                    "WCET.json generated successfully: "
                            + wcetJson
            );

            return wcetJson;

        } finally {
            llvmtaLock.unlock();
        }
    }

    /**
     * 从 CoreInfo.json 中读取核心数量。
     *
     * 支持的 CoreInfo.json 格式：
     *
     * [
     *   {
     *     "core": 0,
     *     "tasks": [...]
     *   },
     *   {
     *     "core": 1,
     *     "tasks": [...]
     *   }
     * ]
     *
     * 核心编号必须从 0 开始连续排列。
     */
    private int readCoreCount(
            Path coreInfoFile
    ) throws IOException {

        JsonNode root =
                objectMapper.readTree(coreInfoFile.toFile());

        if (!root.isArray()) {
            throw new IllegalArgumentException(
                    "CoreInfo.json root must be an array: "
                            + coreInfoFile
            );
        }

        if (root.size() == 0) {
            throw new IllegalArgumentException(
                    "CoreInfo.json contains no core information: "
                            + coreInfoFile
            );
        }

        Set<Integer> coreIds =
                new TreeSet<Integer>();

        for (JsonNode coreNode : root) {
            JsonNode coreIdNode =
                    coreNode.get("core");

            if (coreIdNode == null
                    || !coreIdNode.canConvertToInt()) {

                throw new IllegalArgumentException(
                        "Each item in CoreInfo.json must "
                                + "contain an integer field named 'core'. "
                                + "Invalid item: "
                                + coreNode
                );
            }

            int coreId = coreIdNode.asInt();

            if (coreId < 0) {
                throw new IllegalArgumentException(
                        "Core ID must not be negative: "
                                + coreId
                );
            }

            if (!coreIds.add(coreId)) {
                throw new IllegalArgumentException(
                        "Duplicate core ID in CoreInfo.json: "
                                + coreId
                );
            }

            JsonNode tasksNode =
                    coreNode.get("tasks");

            if (tasksNode == null
                    || !tasksNode.isArray()) {

                throw new IllegalArgumentException(
                        "Core "
                                + coreId
                                + " must contain a tasks array."
                );
            }
        }

        /*
         * 检查核心编号是否为：
         * 0, 1, 2, ..., n-1
         */
        int expectedCoreId = 0;

        for (Integer actualCoreId : coreIds) {
            if (actualCoreId != expectedCoreId) {
                throw new IllegalArgumentException(
                        "Core IDs in CoreInfo.json must be "
                                + "continuous and start from 0. "
                                + "Expected core "
                                + expectedCoreId
                                + ", but found core "
                                + actualCoreId
                );
            }

            expectedCoreId++;
        }

        return coreIds.size();
    }

    /**
     * 检查 LLVMTA 环境和 testcase 必需文件。
     */
    private void validateEnvironment(
            Path sourceDirectory
    ) {
        checkDirectory(
                llvmtaHome,
                "LLVMTA home directory"
        );

        checkDirectory(
                llvmtaTestcasesDirectory,
                "LLVMTA testcases directory"
        );

        checkFile(
                runScript,
                "LLVMTA running script"
        );

        checkFile(
                llvmtaHome.resolve("build/bin/llvmta"),
                "LLVMTA executable"
        );

        checkDirectory(
                sourceDirectory,
                "Testcase directory"
        );

        checkFile(
                sourceDirectory.resolve("CoreInfo.json"),
                "CoreInfo.json"
        );

        checkFile(
                sourceDirectory.resolve(
                        "LoopAnnotations.csv"
                ),
                "LoopAnnotations.csv"
        );

        checkFile(
                sourceDirectory.resolve(
                        "LLoopAnnotations.csv"
                ),
                "LLoopAnnotations.csv"
        );
    }

    /**
     * 判断 LLVMTA 是否已正确部署。
     */
    public boolean isAvailable() {
        return Files.isDirectory(llvmtaHome)
                && Files.isDirectory(
                        llvmtaTestcasesDirectory
                )
                && Files.isRegularFile(runScript)
                && Files.isRegularFile(
                        llvmtaHome.resolve(
                                "build/bin/llvmta"
                        )
                );
    }

    public Path getConfiguredHome() {
        return llvmtaHome;
    }

    public Path getConfiguredScript() {
        return runScript;
    }

    public String getPythonCommand() {
        return pythonCommand;
    }

    private void checkDirectory(
            Path directory,
            String description
    ) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException(
                    description
                            + " does not exist: "
                            + directory
            );
        }
    }

    private void checkFile(
            Path file,
            String description
    ) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException(
                    description
                            + " does not exist: "
                            + file
            );
        }
    }
}