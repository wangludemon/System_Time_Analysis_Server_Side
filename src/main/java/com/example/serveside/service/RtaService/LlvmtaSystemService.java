package com.example.serveside.service.RtaService;

import com.example.serveside.request.LlvmtaImportRequest;
import com.example.serveside.response.RtaInfomation.RtaSystemInformation;
import com.example.serveside.service.RtaService.entity.Resource;
import com.example.serveside.service.RtaService.entity.SporadicTask;
import com.example.serveside.service.RtaService.generator.PriorityGenerator;
import com.example.serveside.service.RtaService.systemInfo.CurrentSystemStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 负责运行 LLVMTA，并将 WCET.json 转换为当前 RTA 系统。
 *
 * 当前规则：
 * 1. 核心数量由 CoreInfo.json 决定；
 * 2. LlvmtaRunner 从 CoreInfo.json 自动读取核心数量；
 * 3. LLVMTA 输出 WCET.json；
 * 4. 本服务从 WCET.json 中读取 core_count 和任务信息；
 * 5. 所有导入任务均作为 LO 任务；
 * 6. C_LOW 为 WCET cycles 根据处理器频率换算后的时间；
 * 7. C_HIGH 为 0；
 * 8. deadline 等于 period；
 * 9. 当前没有共享资源信息，因此 resources 为空。
 */
@Service
public class LlvmtaSystemService {

    private final LlvmtaRunner llvmtaRunner;
    private final CurrentSystemStore currentSystemStore;
    private final ObjectMapper objectMapper;

    public LlvmtaSystemService(
            LlvmtaRunner llvmtaRunner,
            CurrentSystemStore currentSystemStore,
            ObjectMapper objectMapper
    ) {
        this.llvmtaRunner = llvmtaRunner;
        this.currentSystemStore = currentSystemStore;
        this.objectMapper = objectMapper;
    }

    /**
     * 运行 LLVMTA，读取结果并保存为当前 RTA 系统。
     */
    public RtaSystemInformation importSystem(
            LlvmtaImportRequest request
    ) throws Exception {

        validateRequest(request);

        /*
         * 核心数量不再由前端传入。
         *
         * LlvmtaRunner 会读取：
         * testcasePath/CoreInfo.json
         *
         * 然后自动确定运行 runf.py 时使用的 -n 参数。
         */
        Path wcetJsonPath = llvmtaRunner.run(
                request.getTestcasePath(),
                System.out::println
        );

        String json = new String(
                Files.readAllBytes(wcetJsonPath),
                StandardCharsets.UTF_8
        );

        return parseAndStore(
                json,
                request.getFrequencyGHz()
        );
    }

    /**
     * 解析 WCET.json，并将任务保存到 CurrentSystemStore。
     */
    private RtaSystemInformation parseAndStore(
            String json,
            double frequencyGHz
    ) throws Exception {

        JsonNode root = objectMapper.readTree(json);

        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException(
                    "WCET.json root must be a JSON object."
            );
        }

        int coreCount = readCoreCount(root);

        ArrayList<ArrayList<SporadicTask>> tasks =
                createEmptyPartitions(coreCount);

        JsonNode taskNodes = root.get("tasks");

        if (taskNodes == null || !taskNodes.isArray()) {
            throw new IllegalArgumentException(
                    "The tasks field in WCET.json must be an array."
            );
        }

        if (taskNodes.size() == 0) {
            throw new IllegalArgumentException(
                    "WCET.json contains no tasks."
            );
        }

        for (JsonNode taskNode : taskNodes) {
            SporadicTask task = parseTask(
                    taskNode,
                    frequencyGHz,
                    coreCount
            );

            tasks.get(task.partition).add(task);
        }

        /*
         * 使用现有的 Deadline-Monotonic 方法分配优先级。
         */
        PriorityGenerator priorityGenerator =
                new PriorityGenerator();

        tasks = priorityGenerator.assignPrioritiesByDM(tasks);

        /*
         * 当前 LLVMTA 输出中没有共享资源信息。
         */
        ArrayList<Resource> resources =
                new ArrayList<Resource>();

        /*
         * 只有 LLVMTA 成功运行、JSON 成功解析后，
         * 才覆盖当前系统。
         */
        currentSystemStore.set(tasks, resources);

        return buildResponse(tasks);
    }

    /**
     * 从 WCET.json 的 system.core_count 读取核心数量。
     */
    private int readCoreCount(JsonNode root) {
        JsonNode systemNode = root.get("system");

        if (systemNode == null || !systemNode.isObject()) {
            throw new IllegalArgumentException(
                    "WCET.json must contain a system object."
            );
        }

        JsonNode coreCountNode =
                systemNode.get("core_count");

        if (coreCountNode == null
                || !coreCountNode.canConvertToInt()) {

            throw new IllegalArgumentException(
                    "WCET.json must contain an integer "
                            + "system.core_count field."
            );
        }

        int coreCount = coreCountNode.asInt();

        if (coreCount <= 0) {
            throw new IllegalArgumentException(
                    "system.core_count must be greater than zero, "
                            + "actual value: "
                            + coreCount
            );
        }

        return coreCount;
    }

    /**
     * 将 WCET.json 中的单个任务转换为 SporadicTask。
     */
    private SporadicTask parseTask(
            JsonNode taskNode,
            double frequencyGHz,
            int coreCount
    ) {
        if (taskNode == null || !taskNode.isObject()) {
            throw new IllegalArgumentException(
                    "Each item in tasks must be a JSON object."
            );
        }

        int id = readRequiredInteger(
                taskNode,
                "id",
                "task ID"
        );

        int partition = readRequiredInteger(
                taskNode,
                "partition",
                "partition of task " + id
        );

        long period = readRequiredLong(
                taskNode,
                "period",
                "period of task " + id
        );

        long wcetCycles = readRequiredLong(
                taskNode,
                "WCET",
                "WCET of task " + id
        );

        if (partition < 0 || partition >= coreCount) {
            throw new IllegalArgumentException(
                    "Invalid partition "
                            + partition
                            + " for task "
                            + id
                            + ". Valid partition range is 0 to "
                            + (coreCount - 1)
                            + "."
            );
        }

        if (period <= 0) {
            throw new IllegalArgumentException(
                    "Period must be greater than zero for task "
                            + id
                            + ", actual value: "
                            + period
            );
        }

        if (wcetCycles <= 0) {
            throw new IllegalArgumentException(
                    "LLVMTA returned a non-positive WCET "
                            + "for task "
                            + id
                            + ": "
                            + wcetCycles
            );
        }

        /*
         * LLVMTA 输出的 WCET 单位为 CPU cycles。
         *
         * 1 GHz = 1000 cycles / microsecond。
         *
         * WCET(us) =
         *     WCET(cycles) / (frequencyGHz * 1000)
         *
         * 为保证安全性，结果向上取整。
         *
         * 示例：
         * 217449 / (1.6 * 1000)
         * = 135.905625 us
         * 向上取整为 136 us。
         */
        long wcetTime = (long) Math.ceil(
                wcetCycles
                        / (frequencyGHz * 1000.0)
        );

        if (wcetTime <= 0) {
            throw new IllegalArgumentException(
                    "Converted WCET must be greater than zero "
                            + "for task "
                            + id
                            + "."
            );
        }

        double utilization =
                (double) wcetTime / (double) period;

        System.out.println(
                "Imported task "
                        + id
                        + ": partition="
                        + partition
                        + ", WCET cycles="
                        + wcetCycles
                        + ", WCET time="
                        + wcetTime
                        + " us"
                        + ", period="
                        + period
        );

        /*
         * 构造 LO 任务：
         *
         * priority = -1：
         *     后续通过 DM 方法分配。
         *
         * period：
         *     从 WCET.json 中读取。
         *
         * C_LOW：
         *     换算后的 WCET 时间。
         *
         * critical = 0：
         *     表示 LO 任务。
         *
         * 当前 SporadicTask 构造函数会设置：
         * deadline = period；
         * C_HIGH = 0。
         */
        return new SporadicTask(
                -1,
                period,
                wcetTime,
                partition,
                id,
                utilization,
                0,
                2.0
        );
    }

    /**
     * 创建与核心数量对应的空任务分区。
     */
    private ArrayList<ArrayList<SporadicTask>>
    createEmptyPartitions(int coreCount) {

        ArrayList<ArrayList<SporadicTask>> tasks =
                new ArrayList<ArrayList<SporadicTask>>();

        for (int core = 0; core < coreCount; core++) {
            tasks.add(new ArrayList<SporadicTask>());
        }

        return tasks;
    }

    /**
     * 构造前端使用的任务系统响应。
     *
     * 不增加 function、wcetCycles 等新展示字段，
     * 继续沿用原有前端任务表结构。
     */
    private RtaSystemInformation buildResponse(
            ArrayList<ArrayList<SporadicTask>> tasks
    ) {
        RtaSystemInformation response =
                new RtaSystemInformation();

        response.setCoreCount(tasks.size());

        List<RtaSystemInformation.RtaTaskInfo> taskInfos =
                new ArrayList<RtaSystemInformation.RtaTaskInfo>();

        for (ArrayList<SporadicTask> partitionTasks : tasks) {
            for (SporadicTask task : partitionTasks) {

                RtaSystemInformation.RtaTaskInfo taskInfo =
                        new RtaSystemInformation.RtaTaskInfo();

                taskInfo.id = task.id;
                taskInfo.partition = task.partition;
                taskInfo.priority = task.priority;
                taskInfo.critical = "LO";

                taskInfo.period = task.period;
                taskInfo.deadline = task.deadline;

                taskInfo.cLow = task.C_LOW;
                taskInfo.cHigh = task.C_HIGH;
                taskInfo.util = task.util;

                taskInfo.resourceRequiredIndex =
                        new ArrayList<Integer>();

                taskInfo.accessCount =
                        new ArrayList<Integer>();

                taskInfos.add(taskInfo);
            }
        }

        response.setTasks(taskInfos);

        response.setResources(
                new ArrayList<
                        RtaSystemInformation.RtaResourceInfo
                        >()
        );

        return response;
    }

    /**
     * 检查前端请求参数。
     */
    private void validateRequest(
            LlvmtaImportRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Request must not be null."
            );
        }

        if (request.getTestcasePath() == null
                || request.getTestcasePath()
                .trim()
                .isEmpty()) {

            throw new IllegalArgumentException(
                    "testcasePath must not be empty."
            );
        }

        if (request.getFrequencyGHz() == null
                || !Double.isFinite(
                request.getFrequencyGHz()
        )
                || request.getFrequencyGHz() <= 0) {

            throw new IllegalArgumentException(
                    "frequencyGHz must be greater than zero."
            );
        }
    }

    /**
     * 读取必需的整数属性。
     */
    private int readRequiredInteger(
            JsonNode node,
            String fieldName,
            String description
    ) {
        JsonNode valueNode = node.get(fieldName);

        if (valueNode == null
                || !valueNode.canConvertToInt()) {

            throw new IllegalArgumentException(
                    "Missing or invalid "
                            + description
                            + ". JSON field: "
                            + fieldName
                            + ", task JSON: "
                            + node
            );
        }

        return valueNode.asInt();
    }

    /**
     * 读取必需的长整数属性。
     */
    private long readRequiredLong(
            JsonNode node,
            String fieldName,
            String description
    ) {
        JsonNode valueNode = node.get(fieldName);

        if (valueNode == null
                || !valueNode.isIntegralNumber()) {

            throw new IllegalArgumentException(
                    "Missing or invalid "
                            + description
                            + ". JSON field: "
                            + fieldName
                            + ", task JSON: "
                            + node
            );
        }

        return valueNode.asLong();
    }
}

