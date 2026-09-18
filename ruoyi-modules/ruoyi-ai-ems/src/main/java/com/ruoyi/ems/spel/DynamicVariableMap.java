package com.ruoyi.ems.spel;

import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import lombok.RequiredArgsConstructor;

import java.util.*;

@RequiredArgsConstructor
public class DynamicVariableMap implements Map<String, Object> {
    private final String sessionId;
    private final EmsContextManager manager;
    private final String prefix;

    @Override
    public Object get(Object key) {
        CaseContext ctx = manager.getReadOnlySnapshot(sessionId);
        if (ctx == null) return null;
        String varName = prefix + "." + key;
        Object value = ctx.getVar(varName, Object.class);
        if (value != null) return value;
        // 变量未初始化：回退到 resetDefault / default_value
        return manager.getVariableDefault(ctx.getCaseType(), varName);
    }

    @Override
    public Object put(String key, Object value) {
        throw new UnsupportedOperationException(
            "DynamicVariableMap 是只读视图，禁止通过 SpEL 写入变量: " + prefix + "." + key);
    }

    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException(
            "DynamicVariableMap 是只读视图，禁止通过 SpEL 删除变量: " + prefix + "." + key);
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        throw new UnsupportedOperationException("DynamicVariableMap 是只读视图，禁止批量写入");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("DynamicVariableMap 是只读视图，禁止清空");
    }

    // 只读查询接口
    @Override public int size() { return 0; }
    @Override public boolean isEmpty() { return false; }
    @Override public boolean containsKey(Object key) { return get(key) != null; }
    @Override public boolean containsValue(Object value) { return false; }
    @Override public Set<String> keySet() { return Set.of(); }
    @Override public Collection<Object> values() { return List.of(); }
    @Override public Set<Entry<String, Object>> entrySet() { return Set.of(); }
}
