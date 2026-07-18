package com.jjx.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "erp.log.clean")
public class LogCleanProperties {
    private Integer operLogRetentionDays = 90;
    private Integer loginLogRetentionDays = 180;
    private Integer errorLogRetentionDays = 30;
    private Boolean enabled = true;
    private String archivePath = "/var/log/erp/archive";
    private Boolean archiveBeforeDelete = true;
    private Integer batchSize = 1000;
}
