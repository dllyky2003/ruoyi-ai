package com.ruoyi.ems.spi;



import com.ruoyi.ems.spi.dto.BeatSelectionView;

import java.util.List;

/**
 * 评分证据查询 SPI。
 *
 * <p>由 tutor 侧实现；ems 与外部通过 {@code ObjectProvider} 调用。
 * tutor 未部署时由 No-Op 兜底，返回空列表。</p>
 */
public interface AssessmentQueryService {

    /**
     * 查询指定会话的 beat 选择证据链。
     *
     * @param sessionId 会话 id
     * @return 命中/冲突记录列表；未启用或无记录返回空列表（非 null）
     */
    List<BeatSelectionView> getBeatSelections(String sessionId);
}
