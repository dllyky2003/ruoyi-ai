package com.ruoyi.acls.tutor.integration;

import com.ruoyi.acls.tutor.domain.AssessmentState;
import com.ruoyi.acls.tutor.service.AssessmentEngine;
import com.ruoyi.ems.spi.AssessmentQueryService;
import com.ruoyi.ems.spi.dto.BeatSelectionView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * tutor 侧 AssessmentQueryService 实现。
 *
 * <p>从 {@link AssessmentEngine} 的内存状态读取 beat 选择证据，
 * 转换为跨模块 DTO 返回。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TutorAssessmentQueryService implements AssessmentQueryService {

    private final AssessmentEngine assessmentEngine;

    @Override
    public List<BeatSelectionView> getBeatSelections(String sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }

        AssessmentState state = assessmentEngine.getState(sessionId);
        if (state == null) {
            log.debug("[Tutor][AssessmentQuery] session={} 无状态", sessionId);
            return Collections.emptyList();
        }

        List<AssessmentState.BeatSelectionRecord> records = state.getBeatSelections();
        if (records == null || records.isEmpty()) {
            log.debug("[Tutor][AssessmentQuery] session={} 无 beat 证据", sessionId);
            return Collections.emptyList();
        }

        List<BeatSelectionView> result = new ArrayList<>(records.size());
        for (AssessmentState.BeatSelectionRecord r : records) {
            if (r == null) continue;
            BeatSelectionView v = new BeatSelectionView();
            v.setBeatId(r.getBeatId());
            v.setOutcomeRef(r.getOutcomeRef());
            v.setConflictDetected(r.isConflictDetected());
            v.setConflictBeatIds(r.getConflictBeatIds() != null
                ? new ArrayList<>(r.getConflictBeatIds())
                : new ArrayList<>());
            v.setReason(r.getReason());
            v.setRecordedAt(r.getRecordedAt());
            result.add(v);
        }

        log.debug("[Tutor][AssessmentQuery] session={}, beatSelections={}", sessionId, result.size());
        return result;
    }
}
