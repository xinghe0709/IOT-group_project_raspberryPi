package org.xinghe.AIThermalGuardIoT.infrastructure.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StorageInitializer implements ApplicationRunner {

    private final FileStorageService fileStorageService;
    @Override
    public void run(ApplicationArguments args) {
        try {
            fileStorageService.ensureBucketExists();
        } catch (Exception e) {
            log.warn("S3 storage is not available, file upload will be disabled: {}", e.getMessage());
        }
    }
}
