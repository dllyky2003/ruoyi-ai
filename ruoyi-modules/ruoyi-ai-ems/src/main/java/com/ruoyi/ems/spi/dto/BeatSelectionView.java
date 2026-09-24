package com.ruoyi.ems.spi.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * beat 选择证据视图（SPI 跨模块传输）。
 *
 * <p>字段与 tutor 侧 AssessmentState.BeatSelectionRecord 一一对应，
 * 屏蔽内部实现。</p>
 */
@Data
public class BeatSelectionView {

    /** 命中的 beat id；未命中为 null */
    private String beatId;

    /** 引用的 case_outcome_branch id；未命中为 null */
    private String outcomeRef;

    /** 是否检测到互斥冲突 */
    private boolean conflictDetected;

    /** 冲突涉及的其他 beat id */
    private List<String> conflictBeatIds = new ArrayList<>();

    /** 选择原因：ok / no beats matched / conflict 等 */
    private String reason;

    /** 记录时间 */
    private LocalDateTime recordedAt;
}
