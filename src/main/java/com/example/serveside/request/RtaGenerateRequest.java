package com.example.serveside.request;

public class RtaGenerateRequest {
    private Integer coreCount;
    private Integer taskNum;
    private Double utilization;
    private Integer periodMin;
    private Integer periodMax;

    private Integer resourceNum;
    private Double rsf;
    private Integer maxAccess;

    private Integer cslMin;
    private Integer cslMax;

    private String allocation; // WF/BF/FF/NF
    private String priority;   // DMPO

    public Integer getCoreCount() { return coreCount; }
    public void setCoreCount(Integer coreCount) { this.coreCount = coreCount; }
    public Integer getTaskNum() { return taskNum; }
    public void setTaskNum(Integer taskNum) { this.taskNum = taskNum; }
    public Double getUtilization() { return utilization; }
    public void setUtilization(Double utilization) { this.utilization = utilization; }
    public Integer getPeriodMin() { return periodMin; }
    public void setPeriodMin(Integer periodMin) { this.periodMin = periodMin; }
    public Integer getPeriodMax() { return periodMax; }
    public void setPeriodMax(Integer periodMax) { this.periodMax = periodMax; }
    public Integer getResourceNum() { return resourceNum; }
    public void setResourceNum(Integer resourceNum) { this.resourceNum = resourceNum; }
    public Double getRsf() { return rsf; }
    public void setRsf(Double rsf) { this.rsf = rsf; }
    public Integer getMaxAccess() { return maxAccess; }
    public void setMaxAccess(Integer maxAccess) { this.maxAccess = maxAccess; }
    public Integer getCslMin() { return cslMin; }
    public void setCslMin(Integer cslMin) { this.cslMin = cslMin; }
    public Integer getCslMax() { return cslMax; }
    public void setCslMax(Integer cslMax) { this.cslMax = cslMax; }
    public String getAllocation() { return allocation; }
    public void setAllocation(String allocation) { this.allocation = allocation; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
}
