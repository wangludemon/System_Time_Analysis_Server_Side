package com.example.serveside.service.RtaService.systemInfo;

import com.example.serveside.response.RtaInfomation.RtaSystemInformation;
import com.example.serveside.service.RtaService.entity.Resource;
import com.example.serveside.service.RtaService.entity.SporadicTask;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

//  如果你的服务端未来要支持多人同时用：
//        现在的“全局单例 currentSystem”会互相覆盖。
//        解决方式：把 store 从 AtomicReference<RtaSystemInformation> 改成 ConcurrentHashMap<String, RtaSystemInformation>，key 用：
//
//        sessionId / token / userId / 或者前端生成的 UUID。
@Component
public class InMemoryCurrentSystemStore implements CurrentSystemStore {
    private static class Snapshot {
        ArrayList<ArrayList<SporadicTask>> tasks;
        ArrayList<Resource> resources;
        Snapshot(ArrayList<ArrayList<SporadicTask>> t, ArrayList<Resource> r){ this.tasks=t; this.resources=r; }
    }

    private final AtomicReference<Snapshot> ref = new AtomicReference<>();

    @Override
    public void set(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources) {
        ref.set(new Snapshot(tasks, resources));
    }

    @Override
    public ArrayList<ArrayList<SporadicTask>> getTasksOrThrow() {
        Snapshot s = ref.get();
        if (s == null) throw new IllegalStateException("No current system. Generate or upload first.");
        return s.tasks;
    }

    @Override
    public ArrayList<Resource> getResourcesOrThrow() {
        Snapshot s = ref.get();
        if (s == null) throw new IllegalStateException("No current system. Generate or upload first.");
        return s.resources;
    }

    @Override public void clear() { ref.set(null); }
    @Override public boolean has() { return ref.get() != null; }
}