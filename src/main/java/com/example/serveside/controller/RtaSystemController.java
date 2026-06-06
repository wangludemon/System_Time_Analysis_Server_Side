package com.example.serveside.controller;

import com.example.serveside.request.LlvmtaImportRequest;
import com.example.serveside.request.RtaAnalyzeRequest;
import com.example.serveside.request.RtaGenerateRequest;
import com.example.serveside.response.RtaInfomation.RtaAnalysisResult;
import com.example.serveside.response.RtaInfomation.RtaSystemInformation;
import com.example.serveside.response.RtaInfomation.TaskRtaResult;
import com.example.serveside.service.RtaService.AnalysisService;
import com.example.serveside.service.RtaService.LlvmtaRunner;
import com.example.serveside.service.RtaService.LlvmtaSystemService;
import com.example.serveside.service.RtaService.RtaSystemService;
import com.example.serveside.service.RtaService.systemInfo.CurrentSystemStore;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
@CrossOrigin
@RequestMapping(path = "/api")
public class RtaSystemController {

    private final RtaSystemService rtaSystemService;
    private final CurrentSystemStore store;
    private final AnalysisService analysisService;
    private final LlvmtaSystemService llvmtaSystemService;
    private final LlvmtaRunner llvmtaRunner;

    public RtaSystemController(
            RtaSystemService rtaSystemService,
            AnalysisService analysisService,
            CurrentSystemStore store,
            LlvmtaSystemService llvmtaSystemService,
            LlvmtaRunner llvmtaRunner
    ) {
        this.rtaSystemService = rtaSystemService;
        this.analysisService = analysisService;
        this.store = store;
        this.llvmtaSystemService = llvmtaSystemService;
        this.llvmtaRunner = llvmtaRunner;
    }

    /**
     * 根据前端输入参数随机生成任务系统。
     *
     * 该接口中的核心数量仍然由随机系统生成参数指定，
     * 与 LLVMTA 的 CoreInfo.json 无关。
     */
    @ResponseBody
    @PostMapping("/rta/generateSystem")
    public RtaSystemInformation generateSystem(
            @RequestBody RtaGenerateRequest request
    ) {
        RtaSystemInformation response =
                rtaSystemService.generateSystem(request);

        // 保存为当前待分析系统。
        store.set(
                rtaSystemService.getTasks(),
                rtaSystemService.getResources()
        );

        return response;
    }

    /**
     * 检查 RTA 后端和 LLVMTA 是否可用。
     *
     * 请求：
     * GET /api/rta/health
     */
    @ResponseBody
    @GetMapping("/rta/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();

        result.put("status", "UP");
        result.put("service", "RTA");

        result.put(
                "llvmtaAvailable",
                llvmtaRunner.isAvailable()
        );

        result.put(
                "llvmtaHome",
                llvmtaRunner
                        .getConfiguredHome()
                        .toString()
        );

        result.put(
                "llvmtaScript",
                llvmtaRunner
                        .getConfiguredScript()
                        .toString()
        );

        result.put(
                "pythonCommand",
                llvmtaRunner.getPythonCommand()
        );

        return result;
    }

    /**
     * 调用 LLVMTA 分析指定 testcase，
     * 并将 WCET.json 转换为当前 RTA 系统。
     *
     * 核心数量不由前端传入。
     * LlvmtaRunner 会读取 testcase 目录中的 CoreInfo.json，
     * 自动确定核心数量并传给 LLVMTA。
     *
     * 请求：
     * POST /api/rta/llvmta/import
     */
    @ResponseBody
    @PostMapping("/rta/llvmta/import")
    public RtaSystemInformation importLlvmtaSystem(
            @RequestBody LlvmtaImportRequest request
    ) throws Exception {

        System.out.println(
                "========== LLVMTA Import Request =========="
        );

        System.out.println(
                "testcasePath = "
                        + request.getTestcasePath()
        );

        System.out.println(
                "frequencyGHz = "
                        + request.getFrequencyGHz()
        );

        /*
         * 不再打印 request.getCoreCount()。
         * 核心数量由 CoreInfo.json 决定。
         */
        System.out.println(
                "llvmtaHome = "
                        + llvmtaRunner.getConfiguredHome()
        );

        System.out.println(
                "llvmtaScript = "
                        + llvmtaRunner.getConfiguredScript()
        );

        RtaSystemInformation response =
                llvmtaSystemService.importSystem(request);

        System.out.println(
                "LLVMTA system imported successfully."
        );

        System.out.println(
                "Imported core count = "
                        + response.getCoreCount()
        );

        if (response.getTasks() != null) {
            System.out.println(
                    "Imported task count = "
                            + response.getTasks().size()
            );
        }

        System.out.println(
                "==========================================="
        );

        return response;
    }

    /**
     * 对当前随机生成或 LLVMTA 导入的系统
     * 执行响应时间分析。
     *
     * 请求：
     * POST /api/rta/analyze
     */
    @ResponseBody
    @PostMapping("/rta/analyze")
    public RtaAnalysisResult analyze(
            @RequestBody RtaAnalyzeRequest request
    ) {
        System.out.println(
                "========== RTA Analysis Request =========="
        );

        System.out.println(
                "method = "
                        + request.method
                        + ", systemMode = "
                        + request.systemMode
        );

        RtaAnalysisResult response =
                analysisService.analyze(
                        request.method,
                        request.systemMode,
                        false
                );

        System.out.println(
                "========== Analysis Result =========="
        );

        System.out.println(
                "schedulable = "
                        + response.schedulable
                        + ", method = "
                        + response.method
                        + ", mode = "
                        + response.systemMode
        );

        if (response.results != null) {
            for (TaskRtaResult result : response.results) {
                System.out.println(
                        "T" + result.taskId
                                + " P" + result.partition
                                + " WCET=" + result.WCET
                                + " Ri=" + result.Ri
                                + " spin=" + result.spin
                                + " intf=" + result.interference
                                + " indSpin="
                                + result.indirectSpin
                                + " arrival="
                                + result.arrivalBlocking
                                + " retry="
                                + result.retryCost
                );
            }
        }

        System.out.println(
                "====================================="
        );

        return response;
    }
}

