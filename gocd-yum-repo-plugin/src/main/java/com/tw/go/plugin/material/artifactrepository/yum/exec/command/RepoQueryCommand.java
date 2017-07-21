/*
 * Copyright 2017 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tw.go.plugin.material.artifactrepository.yum.exec.command;

import com.thoughtworks.go.plugin.api.logging.Logger;
import com.tw.go.plugin.common.util.ListUtil;
import com.tw.go.plugin.material.artifactrepository.yum.exec.Constants;
import com.tw.go.plugin.material.artifactrepository.yum.exec.YumEnvironmentMap;
import com.tw.go.plugin.material.artifactrepository.yum.exec.message.PackageRevisionMessage;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static java.lang.Long.parseLong;
import static java.lang.String.format;

public class RepoQueryCommand {
    static final String DELIMITER = "<=>";
    private final ProcessRunner processRunner;
    private static Logger LOGGER = Logger.getLoggerFor(RepoQueryCommand.class);
    private final com.tw.go.plugin.material.artifactrepository.yum.exec.command.RepoQueryParams params;

    public RepoQueryCommand(RepoQueryParams params) {
        this(new ProcessRunner(), params);
    }

    //for tests
    public RepoQueryCommand(ProcessRunner processRunner, RepoQueryParams params) {
        this.processRunner = processRunner;
        this.params = params;
    }

    public PackageRevisionMessage execute() {
        YumEnvironmentMap yumEnvironmentMap = new YumEnvironmentMap(params.getRepoId());
        String[] command = {"repoquery",
                "--repofrompath=" + params.getRepoFromId(),
                "--repoid=" + params.getRepoId(),
                "-q",
                params.getPackageSpec(),
                "--qf",
                "%{RELATIVEPATH}" + DELIMITER + "%{NAME}" + DELIMITER + "%{VERSION}" + DELIMITER + "%{RELEASE}" +
                        DELIMITER + "%{ARCH}" + DELIMITER + "%{BUILDTIME}" + DELIMITER + "%{PACKAGER}" + DELIMITER + "%{LOCATION}" +
                        DELIMITER + "%{URL}" + DELIMITER + "%{BUILDHOST}"};

        ProcessOutput processOutput = null;
        synchronized (params.getRepoId().intern()) {
            processOutput = processRunner.execute(command, yumEnvironmentMap.buildYumEnvironmentMap());
        }
        if (isSuccessful(processOutput)) {
            return parseOutput(processOutput);
        }
        String message = format("Error while querying repository with path '%s' and package spec '%s'. %s", params.getRepoUrl(), params.getPackageSpec(), processOutput.getStdErrorAsString());
        LOGGER.info(message);
        throw new RuntimeException(message);
    }

    private boolean isSuccessful(ProcessOutput processOutput) {
        return processOutput != null && processOutput.isZeroReturnCode() && processOutput.hasOutput() && !processOutput.hasErrors();
    }

    private PackageRevisionMessage parseOutput(ProcessOutput processOutput) {
        if (processOutput.getStdOut().size() > 1) {
            List<String> results = new ArrayList<String>();
            List<String> stdOut = processOutput.getStdOut();
            for (int i = 0, stdOutSize = stdOut.size(); i < stdOutSize; i++) {
                String output = stdOut.get(i);
                String[] parts = output.split(DELIMITER);
                String fileName = parts[0].substring(parts[0].lastIndexOf('/') + 1);
                results.add(fileName);
            }
            String message = String.format("Given Package Spec (%s) resolves to more than one file on the repository: %s", params.getPackageSpec(), ListUtil.join(results));
            LOGGER.info(message);
            throw new MultiplePackageException(message);
        }

        String output = processOutput.getStdOut().get(0);
        String[] parts = output.split(DELIMITER);
        String name = parts[1];
        String version = parts[2];
        String release = parts[3];
        String arch = parts[4];
        String buildTime = parts[5];
        String packager = packageTagValue(parts[6]);
        String location = packageTagValue(parts[7]);
        String trackbackUrl = packageTagValue(parts[8]);
        String buildHost = packageTagValue(parts[9]);
        String packageName = format("%s-%s-%s.%s", name, version, release, arch);
        //converting from epoch time
        long timeInMillis = parseLong(buildTime) * 1000;
        PackageRevisionMessage packageRevision = new PackageRevisionMessage(packageName, new Date(timeInMillis), packager, buildHost != null ? "Built on " + buildHost : null, trackbackUrl);
        try {
            packageRevision.addData(Constants.PACKAGE_LOCATION, location);
        } catch (RuntimeException e) {
            LOGGER.warn("Could not add data key. Reason : " + e.getMessage());
        }
        return packageRevision;

    }

    private String packageTagValue(String input) {
        return "NONE".equalsIgnoreCase(input) ? null : input;
    }
}
