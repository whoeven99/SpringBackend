package com.bogda.service.logic.translate;

import com.bogda.repository.repo.DeleteTasksRepo;
import com.bogda.repository.repo.InitialTaskV2Repo;
import com.bogda.repository.repo.TranslateTaskV2Repo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InitialTranslateCleanupService {
    @Autowired
    private TranslateTaskV2Repo translateTaskV2Repo;
    @Autowired
    private DeleteTasksRepo deleteTasksRepo;
    @Autowired
    private InitialTaskV2Repo initialTaskV2Repo;

    public void physicalDeleteInitialTask(Integer initialTaskId) {
        while (translateTaskV2Repo.deleteByInitialTaskId(initialTaskId) > 0) {
            // batch delete until empty
        }
        while (deleteTasksRepo.deleteByInitialTaskId(initialTaskId) > 0) {
            // batch delete until empty
        }
        initialTaskV2Repo.deleteById(initialTaskId);
    }

    public void physicalDeleteOrphanTranslateTasks() {
        while (translateTaskV2Repo.deleteOrphanBatch() > 0) {
            // batch delete until empty
        }
    }
}
