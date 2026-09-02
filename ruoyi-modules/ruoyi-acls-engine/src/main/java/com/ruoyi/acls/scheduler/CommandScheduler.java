package com.ruoyi.acls.scheduler;

import com.ruoyi.acls.engine.CommandResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 命令调度器（骨架实现，后续逐步填充）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandScheduler {

    public CommandResult schedule(/* 参数后续完善 */) {
        // 骨架返回成功
        return CommandResult.success("调度器已接收（骨架）");
    }
}
