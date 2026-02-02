package com.example.serveside.response.RtaInfomation;

import java.util.List;

/**
 * DTO: 响应
 */
public class RtaAnalysisResult {
    public String method;
    public String systemMode;
    public boolean schedulable;
    public List<TaskRtaResult> results;

    public String reason;
}