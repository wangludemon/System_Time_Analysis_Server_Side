package com.example.serveside.service.RtaService;

import com.example.serveside.response.RtaInfomation.RtaAnalysisResult;
import com.example.serveside.response.RtaInfomation.TaskRtaResult;
import com.example.serveside.service.RtaService.analysis.ModeValueApplier;
import com.example.serveside.service.RtaService.analysis.ResponseTimeAnalyzer;
import com.example.serveside.service.RtaService.entity.Resource;
import com.example.serveside.service.RtaService.entity.SporadicTask;
import com.example.serveside.service.RtaService.systemInfo.AnalysisMethod;
import com.example.serveside.service.RtaService.systemInfo.CurrentSystemStore;
import com.example.serveside.service.RtaService.systemInfo.SystemMode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalysisService {

    private final CurrentSystemStore store;
    private final Map<AnalysisMethod, ResponseTimeAnalyzer> analyzerMap = new HashMap<>();

    public ArrayList<ArrayList<SporadicTask>> analyzeTasks;   // 真正拿去算Ri的 tasks

    // 仅 ModeSwitch 用
    public ArrayList<ArrayList<SporadicTask>> lowTasks;       // LO参数版（给 modeswitch 用）

    // 用于前端展示的任务列表（LO=全部，HI/ModeSwitch=HI）
    public ArrayList<ArrayList<SporadicTask>> displayTasks;

    public AnalysisService(CurrentSystemStore store, List<ResponseTimeAnalyzer> analyzers) {
        this.store = store;
        for (ResponseTimeAnalyzer a : analyzers) {
            analyzerMap.put(a.method(), a);
        }
    }

    public RtaAnalysisResult analyze(AnalysisMethod method, SystemMode mode, boolean debugPrint) {

        ArrayList<ArrayList<SporadicTask>> tasks = store.getTasksOrThrow();
        ArrayList<Resource> resources = store.getResourcesOrThrow();

        ResponseTimeAnalyzer analyzer = analyzerMap.get(method);
        if (analyzer == null) {
            throw new IllegalArgumentException("Unsupported analysis method: " + method);
        }



        if (mode == SystemMode.LO){
            analyzeTasks = tasks;
            lowTasks = null;
        }
        else if (mode == SystemMode.HI){
            analyzeTasks = new ArrayList<>();
            for (ArrayList<SporadicTask> sporadicTasks : tasks) {
                ArrayList<SporadicTask> temp = new ArrayList<>();
                for (SporadicTask sporadicTask : sporadicTasks) {
                    if (sporadicTask.critical == 1)
                        temp.add(sporadicTask);
                }
                analyzeTasks.add(temp);
            }
        }else{
            analyzeTasks = new ArrayList<>();
            lowTasks = new ArrayList<>();

            for (ArrayList<SporadicTask> task : tasks) {
                ArrayList<SporadicTask> high = new ArrayList<>();
                ArrayList<SporadicTask> low = new ArrayList<>();
                for (SporadicTask sporadicTask : task) {
                    sporadicTask.PWLP_S = 0;
                    if (sporadicTask.critical == 0) {
                        low.add(sporadicTask);
                    } else {
                        high.add(sporadicTask);
                    }
                }
                analyzeTasks.add(high);
                lowTasks.add(low);
            }
        }


        // 1) 运行分析（会更新 task.spin/task.Ri/...）
        long[][] RiMatrix = analyzer.analyze(tasks, analyzeTasks, lowTasks, resources, mode, debugPrint);
        if (RiMatrix == null){
            RtaAnalysisResult resp = new RtaAnalysisResult();
            resp.method = method.name();
            resp.systemMode = mode.name();
            resp.schedulable = false;
            resp.reason = "LO mode is already unschedulable";
            resp.results = new ArrayList<>();
            return resp;
        }

        // 2) 打包输出 (tasks 替换为 analyzeTasks)
        List<TaskRtaResult> out = new ArrayList<>();
        for (int p = 0; p < analyzeTasks.size(); p++) {
            for (int j = 0; j < analyzeTasks.get(p).size(); j++) {
                SporadicTask t = analyzeTasks.get(p).get(j);

                TaskRtaResult r = new TaskRtaResult();
                r.taskId = t.id;
                r.partition = t.partition;
                r.deadline = t.deadline;
                r.WCET = t.WCET;
                r.pure_resource_execution_time = t.pure_resource_execution_time;

                if (t.critical==0)
                    r.critical = "LO";
                else
                    r.critical = "HI";

                // 以 task.Ri 为准（你 MSRPOriginal 已经写了）
                r.Ri = t.Ri;

                r.spin = t.spin;
                r.interference = t.interference;

                r.indirectSpin = t.indirect_spin;
                r.arrivalBlocking = t.local;

                // MSRP 没有的项置 0
                r.retryCost = t.PWLP_S;

                out.add(r);
            }
        }

        RtaAnalysisResult resp = new RtaAnalysisResult();
        resp.method = method.name();
        resp.systemMode = mode.name();
        resp.reason = "";

        // schedulable：最简单就看是否有任何 Ri > deadline
        resp.schedulable = out.stream().allMatch(x -> {
            SporadicTask t = findTask(analyzeTasks, x.taskId);
            return t != null && x.Ri <= t.deadline;
        });

        resp.results = out;
        return resp;
    }

    private SporadicTask findTask(ArrayList<ArrayList<SporadicTask>> tasks, int id) {
        for (ArrayList<SporadicTask> part : tasks) {
            for (SporadicTask t : part) {
                if (t.id == id) return t;
            }
        }
        return null;
    }

}
