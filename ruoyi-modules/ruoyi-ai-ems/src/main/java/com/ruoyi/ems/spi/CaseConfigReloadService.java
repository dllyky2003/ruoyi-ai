package com.ruoyi.ems.spi;

public interface CaseConfigReloadService {
    void reloadCase(String caseId) throws Exception;
    void reloadAll() throws Exception;
}
