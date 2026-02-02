package com.example.serveside.controller;

import com.example.serveside.request.RtaAnalyzeRequest;
import com.example.serveside.request.RtaGenerateRequest;
import com.example.serveside.response.RtaInfomation.RtaAnalysisResult;
import com.example.serveside.response.RtaInfomation.RtaSystemInformation;
import com.example.serveside.response.RtaInfomation.TaskRtaResult;
import com.example.serveside.service.RtaService.AnalysisService;
import com.example.serveside.service.RtaService.RtaSystemService;
import com.example.serveside.service.RtaService.systemInfo.AnalysisMethod;
import com.example.serveside.service.RtaService.systemInfo.CurrentSystemStore;
import com.example.serveside.service.RtaService.systemInfo.SystemMode;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@CrossOrigin
@RequestMapping(path = "/api")
public class RtaSystemController {
    private final RtaSystemService rtaSystemService;
    private final CurrentSystemStore store;
    private final AnalysisService analysisService;

    public RtaSystemController(RtaSystemService rtaSystemService,
                               AnalysisService analysisService,
                               CurrentSystemStore store) {
        this.rtaSystemService = rtaSystemService;
        this.analysisService = analysisService;
        this.store = store;
    }

    /**
     * RTA：参数生成系统并返回系统信息（任务+资源）
     * Qt 前端用于：输入系统配置参数 -> 生成系统 -> 展示任务表/资源表
     * @param req 系统配置参数
     * @return response 生成的任务+资源信息
     */
    @ResponseBody
    @PostMapping(value = "/rta/generateSystem")
    public RtaSystemInformation generateSystem(@RequestBody RtaGenerateRequest req) {

        RtaSystemInformation response = rtaSystemService.generateSystem(req);

        // 缓存当前系统
        store.set(rtaSystemService.getTasks(), rtaSystemService.getResources());

        return response;
    }

     // TODO
//    @PostMapping("/uploadSystem")
//    public RtaSystemInformation uploadSystem(@RequestBody RtaSystemInformation sys) {
//        store.set(sys);
//        return sys;
//    }

    @ResponseBody
    @PostMapping("/rta/analyze")
    public RtaAnalysisResult analyze(@RequestBody RtaAnalyzeRequest req) {
        System.out.println("=== /analyze called ===");
        System.out.println("method=" + req.method + ", systemMode=" + req.systemMode);

        RtaAnalysisResult resp = analysisService.analyze(req.method, req.systemMode, false);

        // ✅ 打印结果：每个任务的Ri和分解项
        System.out.println("========== Analysis Result ==========");
        System.out.println("schedulable=" + resp.schedulable + ", method=" + resp.method + ", mode=" + resp.systemMode);

        if (resp.results != null) {
            for (TaskRtaResult r : resp.results) {
                System.out.println(
                        "T" + r.taskId + " P" + r.partition +
                                " Ri=" + r.Ri +
                                " spin=" + r.spin +
                                " intf=" + r.interference +
                                " indSpin=" + r.indirectSpin +
                                " arrival=" + r.arrivalBlocking +
                                " retry=" + r.retryCost
                );
            }
        }
        System.out.println("=====================================");

        return resp;
    }


}
