package com.ruoyi.ems.spi;

import com.ruoyi.ems.spi.dto.BeatSelectionView;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

/**
 * AssessmentQueryService 的 No-Op 兜底实现。
 *
 * <p>tutor 未部署时使用。</p>
 */
@Slf4j
public class NoOpAssessmentQueryService implements AssessmentQueryService {

    @Override
    public List<BeatSelectionView> getBeatSelections(String sessionId) {
        if (log.isDebugEnabled()) {
            log.debug("[ACLS SPI] AssessmentQueryService No-Op: session={}", sessionId);
        }
        return Collections.emptyList();
    }
}
