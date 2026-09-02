package com.ruoyi.acls.command;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 并行命令 —— 同时执行多个子命令
 */
@Slf4j
@RequiredArgsConstructor
public class ParallelCommand implements ICommand {

    private final String commandId;
    private final List<ICommand> children;
    private final long timeoutMillis;

    @Override
    public String getCommandId() {
        return commandId;
    }

    @Override
    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    @Override
    public CommandResult execute(CommandContext context) {
        log.info("⚡ 并行执行 {} 个子命令", children.size());

        // 所有子命令异步提交
        List<CompletableFuture<CommandResult>> futures = children.stream()
            .map(cmd -> CompletableFuture.supplyAsync(() -> cmd.execute(context)))
            .collect(Collectors.toList());

        // 等待所有完成
        CompletableFuture<Void> all = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );

        try {
            all.get(getTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("并行执行部分失败或超时", e);
            // 取消未完成的
            futures.forEach(f -> f.cancel(true));
            return CommandResult.failure("并行执行异常: " + e.getMessage());
        }

        // 汇总结果：任一失败则整体失败
        boolean allSuccess = futures.stream()
            .map(CompletableFuture::join)
            .allMatch(CommandResult::isSuccess);

        if (allSuccess) {
            return CommandResult.success("所有并行命令执行成功");
        } else {
            String errors = futures.stream()
                .map(CompletableFuture::join)
                .filter(r -> !r.isSuccess())
                .map(CommandResult::getErrorMessage)
                .collect(Collectors.joining("; "));
            return CommandResult.failure("部分并行命令失败: " + errors);
        }
    }

    @Override
    public void onInterrupted(CommandContext context) {
        children.forEach(cmd -> cmd.onInterrupted(context));
    }
}
