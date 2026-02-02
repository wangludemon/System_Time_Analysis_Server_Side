package com.example.serveside.response.RtaInfomation;

import java.util.List;

public class RtaSystemInformation {
    private Integer coreCount;
    private List<RtaTaskInfo> tasks;
    private List<RtaResourceInfo> resources;

    public Integer getCoreCount() { return coreCount; }
    public void setCoreCount(Integer coreCount) { this.coreCount = coreCount; }
    public List<RtaTaskInfo> getTasks() { return tasks; }
    public void setTasks(List<RtaTaskInfo> tasks) { this.tasks = tasks; }
    public List<RtaResourceInfo> getResources() { return resources; }
    public void setResources(List<RtaResourceInfo> resources) { this.resources = resources; }

    public static class RtaTaskInfo {
        public Integer id;
        public Integer partition;
        public Integer priority;
        public String critical; // LO/HI
        public Long period;
        public Long deadline;
        public Long cLow;
        public Long cHigh;
        public Double util;
        public List<Integer> resourceRequiredIndex; // 0-based
        public List<Integer> accessCount;
    }

    public static class RtaResourceInfo {
        public Integer id;   // 1-based
        public Long cslLow;
        public Long cslHigh;
        public Boolean isGlobal;
        public List<Integer> partitions;
        public List<Integer> requestedTaskIds;
    }
}
