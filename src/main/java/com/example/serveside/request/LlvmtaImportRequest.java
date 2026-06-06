package com.example.serveside.request;

/**
 * LLVMTA 分析请求。
 *
 * 核心数量不再由前端传入，
 * 后端会从 testcase 目录中的 CoreInfo.json 自动获取。
 */
public class LlvmtaImportRequest {

    /**
     * 待分析的 testcase 目录。
     *
     * 该目录应包含：
     * 1. C 源代码；
     * 2. CoreInfo.json；
     * 3. LoopAnnotations.csv；
     * 4. LLoopAnnotations.csv。
     *
     * 示例：
     * /home/user/llvmta/testcases/Benchmarks/test
     */
    private String testcasePath;

    /**
     * 处理器频率，单位 GHz。
     *
     * 用于把 LLVMTA 输出的周期数转换为时间。
     * 例如：1.6 表示 1.6 GHz。
     */
    private Double frequencyGHz;

    public String getTestcasePath() {
        return testcasePath;
    }

    public void setTestcasePath(String testcasePath) {
        this.testcasePath = testcasePath;
    }

    public Double getFrequencyGHz() {
        return frequencyGHz;
    }

    public void setFrequencyGHz(Double frequencyGHz) {
        this.frequencyGHz = frequencyGHz;
    }
}