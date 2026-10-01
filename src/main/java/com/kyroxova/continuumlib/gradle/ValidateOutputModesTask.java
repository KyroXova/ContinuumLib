package com.kyroxova.continuumlib.gradle;

import com.kyroxova.continuumlib.api.config.OutputTargets;
import org.gradle.api.*;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;
import java.io.IOException;

@DisableCachingByDefault(because = "Validates requested modes without producing an artifact")
public abstract class ValidateOutputModesTask extends DefaultTask {
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getConfigFile();
    @TaskAction public void validateModes() throws IOException {
        var outputs = OutputTargets.read(getConfigFile().get().getAsFile().toPath());
        if (outputs.universal()) throw new GradleException("Universal output is not supported yet: loader-specific runtime bootstrapping remains unimplemented");
    }
}
