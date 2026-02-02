package com.example.serveside.response.RtaInfomation;

/**
 * DTO：返回每个任务的 Ri 和组成项 (response的一部分)
 */
public class TaskRtaResult {
    public int taskId;
    public int partition;
    public long deadline;
    public String critical;
    public long Ri;

    public long WCET;
    public long pure_resource_execution_time;
    public long spin;
    public long interference;
    public long indirectSpin;

    public long arrivalBlocking; // MSRP没有就0
    public long retryCost;       // MSRP没有就0
}

