package com.acls.tutor.test;



import com.ruoyi.acls.tutor.manifest.AttemptManifest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AttemptManifestTest {

    @Test
    void 未冻结或缺字段时isComplete为false() {
        assertFalse(AttemptManifest.builder().build().isComplete());

        AttemptManifest m = AttemptManifest.builder()
            .manifestId("M-1")
            .caseId("stable_narrow")
            .caseVersion("0.1.0")
            .planId("LP_STABLE_NARROW_001")
            .planVersion("0.1.0")
            .schemaVersion("0.1.0")
            .fileHashes(Map.of("clinical_rules.yaml", "abc"))
            .frozen(false)
            .build();
        assertFalse(m.isComplete(), "frozen=false 应为 false");
    }

    @Test
    void 字段齐全且冻结返回true() {
        AttemptManifest m = AttemptManifest.builder()
            .manifestId("M-1")
            .caseId("stable_narrow")
            .caseVersion("0.1.0")
            .planId("LP_STABLE_NARROW_001")
            .planVersion("0.1.0")
            .schemaVersion("0.1.0")
            .fileHashes(Map.of("clinical_rules.yaml", "abc"))
            .frozen(true)
            .build();
        assertTrue(m.isComplete());
    }
}
