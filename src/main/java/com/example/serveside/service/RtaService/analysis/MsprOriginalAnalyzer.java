package com.example.serveside.service.RtaService.analysis;

import com.example.serveside.service.RtaService.analysis.allAnalysis.MSRPOriginal;
import com.example.serveside.service.RtaService.analysis.allAnalysis.MSRPOriginalForModeSwitch;
import com.example.serveside.service.RtaService.entity.Resource;
import com.example.serveside.service.RtaService.entity.SporadicTask;
import com.example.serveside.service.RtaService.systemInfo.AnalysisMethod;
import com.example.serveside.service.RtaService.systemInfo.SystemMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class MsprOriginalAnalyzer implements ResponseTimeAnalyzer {

    @Override
    public AnalysisMethod method() {
        return AnalysisMethod.MSRP;
    }

    @Override
    public long[][] analyze(ArrayList<ArrayList<SporadicTask>> totalTasks,
                            ArrayList<ArrayList<SporadicTask>> analyzeTasks,
                            ArrayList<ArrayList<SporadicTask>> lowTasks,
                            ArrayList<Resource> resources,
                            SystemMode mode,
                            boolean debugPrint) {



        // 调现有算法
        if (mode == SystemMode.LO || mode ==SystemMode.HI){
            // 先把 mode 对应的 WCET / CK / CSL 写进去
            ModeValueApplier.apply(totalTasks, resources, mode);
            return new MSRPOriginal().getResponseTime(analyzeTasks, resources, debugPrint);
        }

        else{
            ModeValueApplier.apply(totalTasks, resources, SystemMode.LO);
            // 需要先获取Ri_LO, 才能调用mode switch的分析
            long[][] ri = new MSRPOriginal().getResponseTime(totalTasks, resources, debugPrint);
            // 如果发现不能调度，不需要继续计算mode switch
            if (!isSchedulable(totalTasks, ri)){
                return null;
            }
            ModeValueApplier.apply(totalTasks, resources, mode);
            return new MSRPOriginalForModeSwitch().getResponseTime(analyzeTasks, resources, lowTasks, debugPrint);   // 要传入 hitask 和 lotask分开传入
        }

    }

    private boolean isSchedulable(ArrayList<ArrayList<SporadicTask>> tasks, long[][] ri) {
        if (tasks == null || ri == null) return false;
        for (int p = 0; p < tasks.size(); p++) {
            ArrayList<SporadicTask> part = tasks.get(p);
            if (part == null) continue;

            for (int i = 0; i < part.size(); i++) {
                SporadicTask t = part.get(i);
                long r = (p < ri.length && ri[p] != null && i < ri[p].length) ? ri[p][i] : Long.MAX_VALUE;

                // 这里用 deadline 判断
                if (t != null && r > t.deadline) return false;
            }
        }
        return true;
    }
}