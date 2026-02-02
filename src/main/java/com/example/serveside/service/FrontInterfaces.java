package com.example.serveside.service;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.example.serveside.response.ResourceInformation;
import com.example.serveside.response.WorstCaseInformation;
import com.example.serveside.service.mrsp.MrsPWorstCase;
import com.example.serveside.service.msrp.MSRPWorstCase;
import com.example.serveside.service.pwlp.PWLPWorstCase;
import com.example.serveside.service.CommonUse.generatorTools.SimpleSystemGenerator;
import com.example.serveside.service.CommonUse.BasicPCB;
import com.example.serveside.service.CommonUse.BasicResource;
import com.example.serveside.response.SchedulableInformation;
import com.example.serveside.response.TaskGanttInformation;
import com.example.serveside.request.ConfigurationInformation;
import com.example.serveside.service.mrsp.MrsPMixedCriticalSystem;
import com.example.serveside.service.msrp.MSRPMixedCriticalSystem;
import com.example.serveside.service.pwlp.PWLPMixedCriticalSystem;
import com.google.gson.Gson;

/**
 * {@code FrontInterfaces} 主要起以下作用
 * <p>
 *     <ol>
 *         <li>使用前端传递的系统环境配置参数随机生成一组任务、资源、任务发布时间等信息，并模拟任务在不同资源共享协议下的执行情况，并将执行结果返回。</li>
 *         <li>保存模拟器生成的任务、资源、任务发布时间等信息，以便后续模拟运行任务在特定资源共享协议下的执行情况做准备。</li>
 *         <li>模拟指定任务在不同资源共享协议下的最差运行情况。</li>
 *     </ol>
 * </p>
 * */
public class FrontInterfaces
{
    /**
     * 随机生成的任务的信息。
     * */
    public static ArrayList<BasicPCB> totalTasks;

    /**
     * 随机生成的资源的信息。
     * */
    public static ArrayList<BasicResource> totalResources;

    /**
     * 任务发布的时间点。
     * */
    public static TreeMap<Integer, ArrayList<Integer>> taskReleaseTimes;

    /**
    * MrsP 协议下某一任务的较差运行情况下的任务的时间点发布
    * */
    public static TreeMap<Integer, ArrayList<Integer>> MrsPTaskReleaseTimes;

    /**
     * MSRP 协议下某一个任务的较差运行情况下的任务的时间点发布
     * */
    public static TreeMap<Integer, ArrayList<Integer>> MSRPTaskReleaseTimes;
    /**
     * PWLP 协议下某一个任务的较差运行情况下的任务的时间点发布
     * */
    public static TreeMap<Integer, ArrayList<Integer>> PWLPTaskReleaseTimes;

    /**
     * {@code SchedulableResult}使用前端传递的系统环境配置参数随机生成一组任务、资源、任务发布时间等信息，并模拟任务在不同资源共享协议下的执行情况，并将执行结果返回。
     *
     * @param requestInformation 系统环境配置参数
     */
    public static SchedulableInformation SchedulableResult(ConfigurationInformation requestInformation)
    {
        // 初始化一个模拟器并生成任务、资源以及任务使用资源的情况
        SimpleSystemGenerator systemGenerator = new SimpleSystemGenerator(requestInformation);
        totalTasks = systemGenerator.generateTasks();
        // 按照 Task Id 从小到大进行排序
        totalTasks.sort((t1, t2) -> Integer.compare(t1.staticTaskId, t2.staticTaskId));
        totalResources = systemGenerator.generateResources();
        systemGenerator.generateResourceUsage(totalTasks, totalResources);
        taskReleaseTimes = systemGenerator.generateTaskReleaseTime(totalTasks);

        String generatedJson = generateSystemJson(
                totalTasks,
                totalResources,
                requestInformation.getTotalCPUNum()
        );

        // 调用文件写入函数
        writeJsonFile(generatedJson, "system_configuration.json");

//        totalTasks = systemGenerator.testGenerateTask();
//        // 按照 Task Id 从小到大进行排序
//        totalTasks.sort((t1, t2) -> Integer.compare(t1.staticTaskId, t2.staticTaskId));
//        totalResources = systemGenerator.testGenerateResources();
//        systemGenerator.testGenerateResourceUsage(totalTasks, totalResources);
//        taskReleaseTimes = systemGenerator.testGenerateTaskReleaseTime(totalTasks);

        // 初始化 MSRP 协议的配置
        MSRPMixedCriticalSystem.MSRPInitialize(totalTasks, totalResources, SimpleSystemGenerator.total_partitions, taskReleaseTimes, requestInformation.getIsStartUpSwitch(), requestInformation.getCriticalitySwitchTime());
        // 初始化 MrsP 协议的配置
        MrsPMixedCriticalSystem.MrsPInitialize(totalTasks, totalResources, SimpleSystemGenerator.total_partitions, taskReleaseTimes, requestInformation.getIsStartUpSwitch(), requestInformation.getCriticalitySwitchTime());
        // 初始化 PWLP 协议的设置
        PWLPMixedCriticalSystem.PWLPInitialize(totalTasks, totalResources, SimpleSystemGenerator.total_partitions, taskReleaseTimes, requestInformation.getIsStartUpSwitch(), requestInformation.getCriticalitySwitchTime());

        // 运行 MSRP 协议
        MSRPMixedCriticalSystem.SystemExecute();
        // 运行 MrsP 协议
        MrsPMixedCriticalSystem.SystemExecute();
        // 运行 PWLP
        PWLPMixedCriticalSystem.SystemExecute();

        // task gantt chart information: 任务甘特图信息：
        List<TaskGanttInformation> taskGanttInformations = new ArrayList<>();
        for (com.example.serveside.response.TaskInformation releaseTaskInformation : MrsPMixedCriticalSystem.releaseTaskInformations)
        {
            com.example.serveside.response.GanttInformation ganttInformation = new com.example.serveside.response.GanttInformation(new ArrayList<>(), new ArrayList<>(), 30);

            int baseRunningCPUCore = -1;
            for (BasicPCB staticTask : totalTasks)
                if (releaseTaskInformation.getStaticPid() == staticTask.staticTaskId)
                {
                    baseRunningCPUCore = staticTask.baseRunningCpuCore;
                    break;
                }

            taskGanttInformations.add(new com.example.serveside.response.TaskGanttInformation(releaseTaskInformation.getStaticPid(), releaseTaskInformation.getDynamicPid(), baseRunningCPUCore, ganttInformation));
        }

        // cpu gantt chart information cpu 甘特图信息 :
        List<com.example.serveside.response.GanttInformation> cpuGanttInformations = new ArrayList<>();
        // 后面这个 TOTAL_CPU_CORE_NUM 可以用前端传递过来的信息进行更正
        for (int i = 0; i < MrsPMixedCriticalSystem.TOTAL_CPU_CORE_NUM; ++i)
        {
            cpuGanttInformations.add(new com.example.serveside.response.GanttInformation(new ArrayList<>(), new ArrayList<>(), 30));
        }

        // 生成一下 ResourceInformation
        ArrayList<ResourceInformation> resourceInformations = new ArrayList<>();
        List<List<Integer>> accessTasks = new ArrayList<>();
        for (int i = 0; i < totalResources.size(); ++i) {
            resourceInformations.add(new ResourceInformation(totalResources.get(i)));
            accessTasks.add(new ArrayList<>());
        }
        for (BasicPCB task : totalTasks) {
            for (int resourceId : task.accessResourceIndex) {
                if (!accessTasks.get(resourceId).contains(task.staticTaskId)) {
                    accessTasks.get(resourceId).add(task.staticTaskId);
                }
            }
        }
        for (int i = 0; i < resourceInformations.size(); ++i) {
            resourceInformations.get(i).setAccessTasks(accessTasks.get(i));
        }

        return new SchedulableInformation(MSRPMixedCriticalSystem.isSchedulable,
                MrsPMixedCriticalSystem.isSchedulable,
                PWLPMixedCriticalSystem.isSchedulable,
                totalTasks, resourceInformations,
                taskGanttInformations, cpuGanttInformations);
    }

    /**
     * {@code MrsPWorstCaseExecution} 生成指定任务{@code sufferTaskId} 在 MrsP 资源共享协议下的最差运行情况。
     *
     * @param sufferTaskId 指定任务的静态标识符
     * @param isStartUpSwitch 是否开启关键级切换
     * @param criticalitySwitchTime 关键级切换发生的时间点
     * */
    public static WorstCaseInformation MrsPWorstCaseExecution(Integer sufferTaskId, Boolean isStartUpSwitch, Integer criticalitySwitchTime)
    {
        // 先调用 SimpleSystemGenerator 生成：在 staticTaskId 最坏情况下所有任务的发布时间
        MrsPTaskReleaseTimes = new MrsPWorstCase().MrsPGeneratedWorstCaseReleaseTime(totalTasks, totalResources, sufferTaskId, SimpleSystemGenerator.TOTAL_CPU_CORE_NUM);
        // 初始化一下协议的运行配置
        MrsPMixedCriticalSystem.MrsPInitialize(totalTasks, totalResources, SimpleSystemGenerator.total_partitions, MrsPTaskReleaseTimes, isStartUpSwitch, criticalitySwitchTime);
        // 运行协议
        MrsPMixedCriticalSystem.SystemExecute();
        // 返回调度信息
        return new WorstCaseInformation(MrsPMixedCriticalSystem.isSchedulable, MrsPMixedCriticalSystem.PackageTotalInformation());
    }

    /**
     * {@code MSRPWorstCaseExecution} 生成指定任务{@code sufferTaskId} 在 MSRP 资源共享协议下的最差运行情况。
     *
     * @param sufferTaskId 指定任务的静态标识符
     * @param isStartUpSwitch 是否开启关键级切换
     * @param criticalitySwitchTime 关键级切换发生的时间点
     * */
    public static WorstCaseInformation MSRPWorstCaseExecution(Integer sufferTaskId, Boolean isStartUpSwitch, Integer criticalitySwitchTime) {
        // 先调用 SimpleSystemGenerator 生成：在 staticTaskId 最坏情况下所有任务的发布时间
        MSRPTaskReleaseTimes = new MSRPWorstCase().MSRPGeneratedWorstCaseReleaseTime(totalTasks, totalResources, sufferTaskId, SimpleSystemGenerator.TOTAL_CPU_CORE_NUM);
        // 初始化一下协议的运行配置
        MSRPMixedCriticalSystem.MSRPInitialize(totalTasks, totalResources, SimpleSystemGenerator.total_partitions, MSRPTaskReleaseTimes, isStartUpSwitch, criticalitySwitchTime);
        // 运行协议
        MSRPMixedCriticalSystem.SystemExecute();
        // 返回调度信息
        return new WorstCaseInformation(MSRPMixedCriticalSystem.isSchedulable, MSRPMixedCriticalSystem.PackageTotalInformation());
    }

    /**
     * {@code PWLPWorstCaseExecution} 生成指定任务{@code sufferTaskId} 在 PWLP 资源共享协议下的最差运行情况。
     *
     * @param sufferTaskId 指定任务的静态标识符
     * @param isStartUpSwitch 是否开启关键级切换
     * @param criticalitySwitchTime 关键级切换发生的时间点
     * */
    public static WorstCaseInformation PWLPWorstCaseExecution(Integer sufferTaskId, Boolean isStartUpSwitch, Integer criticalitySwitchTime) {
        // 先调用 SimpleSystemGenerator 生成：在 staticTaskId 最坏情况下所有任务的发布时间
        PWLPTaskReleaseTimes = new PWLPWorstCase().PWLPGeneratedWorstCaseReleaseTime(totalTasks, totalResources, sufferTaskId, SimpleSystemGenerator.TOTAL_CPU_CORE_NUM);
        // 初始化一下协议的运行配置
        PWLPMixedCriticalSystem.PWLPInitialize(totalTasks, totalResources, SimpleSystemGenerator.total_partitions, PWLPTaskReleaseTimes, isStartUpSwitch, criticalitySwitchTime);
        // 运行协议
        PWLPMixedCriticalSystem.SystemExecute();
        // 返回调度信息
        return new WorstCaseInformation(PWLPMixedCriticalSystem.isSchedulable, PWLPMixedCriticalSystem.PackageTotalInformation());
    }


    public static String generateSystemJson(
            ArrayList<BasicPCB> totalTasks,
            ArrayList<BasicResource> totalResources,
            int coreCount) {

        // 1. Sort tasks by ID as requested in the example.
        // 假设 'staticTaskId' 是用于排序的字段
        //totalTasks.sort(Comparator.comparingInt(t -> t.staticTaskId));

        // 2. Create the overarching structure for the JSON object
        Map<String, Object> systemData = new HashMap<>();

        // 2.1. System information
        Map<String, Integer> systemInfo = new HashMap<>();
        systemInfo.put("core_count", coreCount);
        systemData.put("system", systemInfo);

        // 2.2. Tasks array
        List<Map<String, Object>> tasksList = new ArrayList<>();
        for (BasicPCB task : totalTasks) {
            Map<String, Object> taskMap = new HashMap<>();

            // Map task fields directly
            taskMap.put("id", task.staticTaskId);
            taskMap.put("priority", task.basePriority);
            taskMap.put("WCET_HI", task.WCCT_high);
            taskMap.put("WCET_LO", task.WCCT_low);
            taskMap.put("deadline", task.deadline);
            taskMap.put("period", task.period);
            taskMap.put("partition", task.baseRunningCpuCore);
            taskMap.put("critical", task.criticality);

            // Map resource requests
            // *** 关键修改：requestsList 被初始化为空，并且删除了遍历请求的代码 ***
            List<Map<String, Integer>> requestsList = new ArrayList<>();

        /* if (task.resource_requests != null) {
            for (BasicPCB.ResourceRequest request : task.resource_requests) {
                Map<String, Integer> requestMap = new HashMap<>();
                requestMap.put("resource_id", request.resource_id);
                requestMap.put("request_number", request.request_number);
                requestsList.add(requestMap);
            }
        }
        */

            taskMap.put("resource_requests", requestsList); // tasksList 中的 requestsList 是空的
            tasksList.add(taskMap);
        }
        systemData.put("tasks", tasksList);

        // 2.3. Resources array
        // *** 关键修改：resourcesList 被初始化为空，并且删除了遍历资源的代码 ***
        List<Map<String, Integer>> resourcesList = new ArrayList<>();
        // 删除了遍历 totalResources 的代码

        systemData.put("resources", resourcesList); // 此时 resourcesList 是一个空列表

        // 3. Serialize the data structure to a JSON string
        Gson gson = new Gson();
        return gson.toJson(systemData);
    }
    public static boolean writeJsonFile(String jsonString, String fileName) {
        // 1. 获取项目的根目录路径
        // System.getProperty("user.dir") 返回程序当前运行的工作目录，
        // 在大多数 IDE 或标准 Java 运行环境中，这通常是项目的根目录。
        String projectRootPath = System.getProperty("user.dir");

        // 2. 构造完整的文件路径
        // 使用 Path 和 Paths 可以安全地构建跨平台的文件路径。
        Path filePath = Paths.get(projectRootPath, fileName);

        // 打印路径信息，方便调试
        System.out.println("Target file path: " + filePath.toAbsolutePath().toString());

        try (FileWriter fileWriter = new FileWriter(filePath.toFile())) {

            // 3. 写入 JSON 字符串
            fileWriter.write(jsonString);

            // 4. 确保所有数据被立即写入磁盘
            fileWriter.flush();

            System.out.println("JSON file generated successfully at: " + filePath.toAbsolutePath().toString());
            return true;
        } catch (IOException e) {
            System.err.println("Error writing JSON file: " + e.getMessage());
            // 如果目录不存在，可能需要先创建目录，但这里假设项目根目录是存在的
            return false;
        }
    }
}
