package com.example.serveside.service.RtaService.systemInfo;

import com.example.serveside.response.RtaInfomation.RtaSystemInformation;
import com.example.serveside.service.RtaService.entity.Resource;
import com.example.serveside.service.RtaService.entity.SporadicTask;

import java.util.ArrayList;

public interface CurrentSystemStore {
    void set(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources);
    ArrayList<ArrayList<SporadicTask>> getTasksOrThrow();
    ArrayList<Resource> getResourcesOrThrow();
    void clear();
    boolean has();
}


