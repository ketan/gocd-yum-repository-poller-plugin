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

import com.tw.go.plugin.common.util.StringUtil;
import com.tw.go.plugin.material.artifactrepository.yum.exec.message.ValidationError;
import com.tw.go.plugin.material.artifactrepository.yum.exec.message.ValidationResultMessage;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class Credentials {

    private final String user;
    private final String password;

    public Credentials(String user, String password) {
        this.user = user;
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public String getUser() {
        return user;
    }

    public String getUserInfo() throws UnsupportedEncodingException {
        return String.format("%s:%s", user, URLEncoder.encode(password, "UTF-8"));
    }

    public void validate(ValidationResultMessage validationResultMessage) {
        if (StringUtil.isBlank(user) && StringUtil.isNotBlank(password))
            validationResultMessage.addError(ValidationError.create(Constants.USERNAME, "Both Username and password are required."));
        if (StringUtil.isNotBlank(user) && StringUtil.isBlank(password))
            validationResultMessage.addError(ValidationError.create(Constants.PASSWORD, "Both Username and password are required."));
    }

    public boolean isComplete() {
        return StringUtil.isNotBlank(user) && StringUtil.isNotBlank(password);
    }

    public boolean isPresent() {
        return StringUtil.isNotBlank(user) || StringUtil.isNotBlank(password);
    }
}
