package com.ruoyi.acls.contract;

import com.ruoyi.ems.spi.CaseConfigReloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CaseConfigReloadServiceImpl implements CaseConfigReloadService {

    private final CaseConfigLoader caseConfigLoader;

    @Override
    public void reloadCase(String caseId) throws Exception {
        caseConfigLoader.reloadCase(caseId);
    }

    @Override
    public void reloadAll() throws Exception {
        caseConfigLoader.reloadAll();
    }
}
