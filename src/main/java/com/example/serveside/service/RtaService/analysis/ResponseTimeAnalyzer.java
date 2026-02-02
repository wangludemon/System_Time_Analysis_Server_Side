package com.example.serveside.service.RtaService.analysis;

import com.example.serveside.service.RtaService.entity.Resource;
import com.example.serveside.service.RtaService.entity.SporadicTask;
import com.example.serveside.service.RtaService.systemInfo.AnalysisMethod;
import com.example.serveside.service.RtaService.systemInfo.SystemMode;

import java.util.ArrayList;


public interface ResponseTimeAnalyzer {
    AnalysisMethod method();
    long[][] analyze(ArrayList<ArrayList<SporadicTask>> totalTasks,
                     ArrayList<ArrayList<SporadicTask>> analyzeTasks,
                     ArrayList<ArrayList<SporadicTask>> lowTasks,
                     ArrayList<Resource> resources,
                     SystemMode mode,
                     boolean debugPrint);
}