package com.ruoyi.acls.contract.util;

import com.ruoyi.acls.contract.util.CrossModuleReferenceScanner.VariableBlood;
import com.ruoyi.ems.domain.VariableMeta;
import com.ruoyi.ems.config.loader.VariableMetaLoader;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DanglingRefChecker {

    private final CrossModuleReferenceScanner scanner;
    private final VariableMetaLoader variableMetaLoader;

    public DanglingRefChecker(CrossModuleReferenceScanner scanner,
                              VariableMetaLoader variableMetaLoader) {
        this.scanner = scanner;
        this.variableMetaLoader = variableMetaLoader;
    }

    @Data
    public static class DanglingReport {
        private String caseId;
        private int totalVars;
        private int definedCount;
        private int danglingCount;
        private List<DanglingVar> dangling = new ArrayList<>();
    }

    @Data
    public static class DanglingVar {
        private String varName;
        private int readerCount;
        private List<RefSummary> sampleReaders = new ArrayList<>();
    }

    @Data
    public static class RefSummary {
        private String module;
        private String filePath;
        private String sourceType;
        private String expression;
    }

    public DanglingReport check(String caseId) {
        Map<String, VariableBlood> referenced = scanner.scan(caseId);

        // 真正的定义清单：来自 VariableMetaLoader，已合并 GLOBAL + case
        Set<String> defined = new LinkedHashSet<>();
        Map<String, VariableMeta> metas = variableMetaLoader.getVariablesForCase(caseId);
        if (metas != null) {
            defined.addAll(metas.keySet());
        }

        DanglingReport report = new DanglingReport();
        report.setCaseId(caseId);
        report.setTotalVars(referenced.size());
        report.setDefinedCount(defined.size());

        for (VariableBlood vb : referenced.values()) {
            if (vb.getReaders().isEmpty()) continue;
            if (defined.contains(vb.getVarName())) continue;

            DanglingVar d = new DanglingVar();
            d.setVarName(vb.getVarName());
            d.setReaderCount(vb.getReaders().size());
            d.setSampleReaders(vb.getReaders().stream().limit(5).map(r -> {
                RefSummary s = new RefSummary();
                s.setModule(r.getModule());
                s.setFilePath(r.getFilePath());
                s.setSourceType(r.getSourceType());
                s.setExpression(r.getExpression());
                return s;
            }).collect(Collectors.toList()));
            report.getDangling().add(d);
        }

        report.setDanglingCount(report.getDangling().size());
        return report;
    }
}
