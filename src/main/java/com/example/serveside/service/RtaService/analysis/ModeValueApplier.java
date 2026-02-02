package com.example.serveside.service.RtaService.analysis;

import com.example.serveside.service.RtaService.entity.Resource;
import com.example.serveside.service.RtaService.entity.SporadicTask;
import com.example.serveside.service.RtaService.systemInfo.SystemMode;

import java.util.ArrayList;

public class ModeValueApplier {

    public static void apply(ArrayList<ArrayList<SporadicTask>> tasks,
                             ArrayList<Resource> resources,
                             SystemMode mode) {

        // 1) 设置资源的 csl（MSRP 用 resource.csl）
        for (Resource r : resources) {
            if (mode == SystemMode.LO) {
                r.csl = r.csl_low;     //  LO 用 low
            } else if (mode == SystemMode.HI) {
                r.csl = r.csl_high;    //  HI 用 high（如果你要 cklo + csl_lo 就改成 low）
            } else {
                // ModeSwitch: csl都用HI
                r.csl = r.csl_high;
            }
        }

        // 2) 设置任务的 WCET 与 pure_resource_execution_time
        for (ArrayList<SporadicTask> part : tasks) {
            for (SporadicTask t : part) {
                // 先清空分析结果项，防止上一次分析残留
                t.spin = 0;
                t.interference = 0;
                t.local = 0;
                t.indirect_spin = 0;
                //t.Ri = 0;

                if (mode == SystemMode.LO) {
                    t.WCET = t.C_LOW;
                    t.pure_resource_execution_time = t.prec_LOW;
                } else if (mode == SystemMode.HI) {
                    t.WCET = t.C_HIGH;
                    t.pure_resource_execution_time = t.prec_HIGH;

                    // 如果你以后想改成 CK_HI，就写：t.pure_resource_execution_time = t.prec_HIGH;
                } else {
                    // ModeSwitch: HI任务， LO任务LO
                    if (t.critical == 0){
                        t.WCET = t.C_LOW;
                        t.pure_resource_execution_time = t.prec_LOW;
                        //t.Ri_LO = t.Ri;
                    }else{
                        t.WCET = t.C_HIGH;
                        t.pure_resource_execution_time = t.prec_HIGH;
                        t.Ri_LO = t.Ri;
                    }
                }
            }
        }
    }
}