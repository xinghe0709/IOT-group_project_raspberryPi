package org.xinghe.AIThermalGuardIoT.infrastructure.file;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StorageInitializer implements ApplicationRunner {

    private final FileStorageService fileStorageService;
    @Override
    public void run(ApplicationArguments args) throws Exception {
        fileStorageService.ensureBucketExists();
    }
}
