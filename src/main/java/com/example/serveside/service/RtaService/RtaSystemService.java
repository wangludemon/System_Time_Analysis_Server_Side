package com.example.serveside.service.RtaService;

import com.example.serveside.request.RtaGenerateRequest;
import com.example.serveside.response.RtaInfomation.RtaSystemInformation;
import com.example.serveside.service.RtaService.entity.Resource;
import com.example.serveside.service.RtaService.entity.SporadicTask;
import com.example.serveside.service.RtaService.generator.AllocationGeneator;
import com.example.serveside.service.RtaService.generator.PriorityGenerator;
import com.example.serveside.service.RtaService.generator.SystemGenerator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RtaSystemService {

    public ArrayList<ArrayList<SporadicTask>> getTasks() {
        return tasks;
    }

    public ArrayList<Resource> getResources() {
        return resources;
    }

    ArrayList<ArrayList<SporadicTask>> tasks;
    ArrayList<Resource> resources;

    public RtaSystemInformation generateSystem(RtaGenerateRequest req) {
        validate(req);

        // 1) 构造 SystemGenerator（原工具周期会 *1000）
        boolean isPeriodLogUni = false;
        boolean print = false;

        SystemGenerator generator = new SystemGenerator(
                req.getPeriodMin(),
                req.getPeriodMax(),
                isPeriodLogUni,
                req.getCoreCount(),     // total_partitions
                req.getTaskNum(),       // total_tasks
                req.getRsf(),
                req.getCslMin(),
                req.getCslMax(),
                req.getResourceNum(),
                req.getMaxAccess(),
                req.getUtilization(),
                print
        );

        // 2) 生成 tasks/resources，并生成资源使用
        ArrayList<SporadicTask> tasks = generator.generateTasks(true);
        if (tasks == null) {
            throw new IllegalStateException("Failed to generate tasks (try adjusting utilization/period range).");
        }
        ArrayList<Resource> resources = generator.generateResources();
        generator.generateResourceUsage(tasks, resources);

        // 3) 分配任务到分区（policy: 0 WF, 1 BF, 2 FF, 3 NF）
        int policy = mapAllocationPolicy(req.getAllocation());
        AllocationGeneator allocator = new AllocationGeneator();
        ArrayList<ArrayList<SporadicTask>> tasksByPartition =
                allocator.allocateTasks(tasks, resources, req.getCoreCount(), policy);

        if (tasksByPartition == null) {
            throw new IllegalStateException("Task allocation failed (utilization too high for selected policy).");
        }

        // 4) 分配优先级（DM）
        PriorityGenerator pg = new PriorityGenerator();
        tasksByPartition = pg.assignPrioritiesByDM(tasksByPartition);

        // 5) 轻量统计：资源 -> 任务id集合 / 分区集合（0-based resIndex）
        Map<Integer, Set<Integer>> resToTaskIds = new HashMap<Integer, Set<Integer>>();
        Map<Integer, Set<Integer>> resToPartitions = new HashMap<Integer, Set<Integer>>();

        for (int p = 0; p < tasksByPartition.size(); p++) {
            ArrayList<SporadicTask> partTasks = tasksByPartition.get(p);
            for (int i = 0; i < partTasks.size(); i++) {
                SporadicTask t = partTasks.get(i);
                if (t.resource_required_index == null) continue;

                for (int k = 0; k < t.resource_required_index.size(); k++) {
                    int resIndex = t.resource_required_index.get(k);

                    Set<Integer> taskIdSet = resToTaskIds.get(resIndex);
                    if (taskIdSet == null) {
                        taskIdSet = new HashSet<Integer>();
                        resToTaskIds.put(resIndex, taskIdSet);
                    }
                    taskIdSet.add(t.id);

                    Set<Integer> partSet = resToPartitions.get(resIndex);
                    if (partSet == null) {
                        partSet = new HashSet<Integer>();
                        resToPartitions.put(resIndex, partSet);
                    }
                    partSet.add(t.partition);
                }
            }
        }

        // 为了缓存
        this.tasks = tasksByPartition;
        this.resources = resources;

        // 6) 组装返回 DTO
        RtaSystemInformation out = new RtaSystemInformation();
        out.setCoreCount(req.getCoreCount());

        // tasks: 扁平列表
        List<RtaSystemInformation.RtaTaskInfo> taskInfos = new ArrayList<RtaSystemInformation.RtaTaskInfo>();
        for (int p = 0; p < tasksByPartition.size(); p++) {
            ArrayList<SporadicTask> part = tasksByPartition.get(p);
            for (int i = 0; i < part.size(); i++) {
                SporadicTask t = part.get(i);

                RtaSystemInformation.RtaTaskInfo ti = new RtaSystemInformation.RtaTaskInfo();
                ti.id = t.id;
                ti.partition = t.partition;
                ti.priority = t.priority;
                ti.critical = (t.critical == 0) ? "LO" : "HI";
                ti.period = t.period;
                ti.deadline = t.deadline;
                ti.cLow = t.C_LOW;
                ti.cHigh = t.C_HIGH;
                ti.util = t.util;

                // Java 8：不能用 List.of()/Set.of()
                ti.resourceRequiredIndex = (t.resource_required_index == null)
                        ? new ArrayList<Integer>()
                        : new ArrayList<Integer>(t.resource_required_index);

                ti.accessCount = (t.number_of_access_in_one_release == null)
                        ? new ArrayList<Integer>()
                        : new ArrayList<Integer>(t.number_of_access_in_one_release);

                taskInfos.add(ti);
            }
        }
        out.setTasks(taskInfos);

        // resources: 给 Qt 资源表 + 联动
        List<RtaSystemInformation.RtaResourceInfo> resInfos = new ArrayList<>();

        for (int rIdx = 0; rIdx < resources.size(); rIdx++) {
            Resource r = resources.get(rIdx);

            RtaSystemInformation.RtaResourceInfo ri = new RtaSystemInformation.RtaResourceInfo();
            ri.id = r.id;
            ri.cslLow = r.csl_low;
            ri.cslHigh = r.csl_high;
            ri.isGlobal = r.isGlobal;


            Set<Integer> taskIdSet = new HashSet<>();
            // 判空，防止空指针
            if (r.requested_tasks != null) {
                for (SporadicTask task : r.requested_tasks) {
                    // 这里直接取 task.id，这就是前端需要的 ID (通常是 1-based)
                    // 这样前端收到 [1, 3] 就能对应到 ID 为 1 和 3 的任务
                    taskIdSet.add(task.id);
                }
            }
            ri.requestedTaskIds = new ArrayList<>(taskIdSet);
            Collections.sort(ri.requestedTaskIds);


            Set<Integer> partSet = new HashSet<>();
            if (r.partitions != null) {
                partSet.addAll(r.partitions);
            }
            ri.partitions = new ArrayList<>(partSet);
            Collections.sort(ri.partitions);

            resInfos.add(ri);
        }
        out.setResources(resInfos);

        return out;
    }

    /**
     * Java 8 写法：不用 switch-expression
     * WF=0 BF=1 FF=2 NF=3
     */
    private int mapAllocationPolicy(String allocation) {
        if (allocation == null) return 0;
        String a = allocation.toUpperCase(Locale.ROOT);
        if ("WF".equals(a)) return 0;
        if ("BF".equals(a)) return 1;
        if ("FF".equals(a)) return 2;
        if ("NF".equals(a)) return 3;
        return 0;
    }

    private void validate(RtaGenerateRequest req) {
        if (req.getCoreCount() == null || req.getCoreCount() <= 0)
            throw new IllegalArgumentException("coreCount must be > 0");

        if (req.getTaskNum() == null || req.getTaskNum() <= 0)
            throw new IllegalArgumentException("taskNum must be > 0");

        if (req.getUtilization() == null || req.getUtilization() <= 0)
            throw new IllegalArgumentException("utilization must be > 0");

        if (req.getPeriodMin() == null || req.getPeriodMax() == null
                || req.getPeriodMin() <= 0 || req.getPeriodMax() < req.getPeriodMin())
            throw new IllegalArgumentException("period range invalid");

        if (req.getResourceNum() == null || req.getResourceNum() < 0)
            throw new IllegalArgumentException("resourceNum must be >= 0");

        if (req.getRsf() == null || req.getRsf() < 0 || req.getRsf() > 1)
            throw new IllegalArgumentException("rsf must be in [0,1]");

        if (req.getMaxAccess() == null || req.getMaxAccess() <= 0)
            throw new IllegalArgumentException("maxAccess must be > 0");

        if (req.getCslMin() == null || req.getCslMax() == null
                || req.getCslMin() <= 0 || req.getCslMax() < req.getCslMin())
            throw new IllegalArgumentException("csl range invalid");
    }
}
