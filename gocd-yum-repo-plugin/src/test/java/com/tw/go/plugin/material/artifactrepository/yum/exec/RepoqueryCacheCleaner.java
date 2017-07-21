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

package com.tw.go.plugin.material.artifactrepository.yum.exec;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

public class RepoqueryCacheCleaner{
    public static void performCleanup() throws IOException {
        File[] cacheFiles = new File("/var/tmp/").listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("go-yum-plugin-");
            }
        });
        for(File cacheFile : cacheFiles){
            FileUtils.forceDelete(cacheFile);
        }
    }

}
