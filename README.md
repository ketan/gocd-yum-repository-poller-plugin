# Yum repository poller plugin for GoCD

The Yum respository poller is a bundled [package material](https://docs.gocd.org/current/extension_points/package_repository_extension.html) plugin capable of polling yum repositories for rpm packages. GoCD server interacts with this plugin via package material plugin interfaces. The plugin makes use of a command similar to the following to poll the server. So it does not depend on the files that yum depends on e.g. files under `/etc/yum.repos.d`

```
repoquery --repofrompath=uuid,$REPO_URL --repoid=uuid -q $PACKAGE_SPEC -qf "%{LOCATION}..."
```

A given instance of polling is considered successful if `repoquery` returns a single package as output.

## Installation

This plugin comes bundled along with the GoCD server, hence a separate installation is not required.

> **Note:** This plugin is available for Go servers running on Linux nodes having `repoquery` installed using the  [`yum-utils`](http://linux.die.net/man/1/yum-utils) package, [Ubuntu](http://manpages.ubuntu.com/manpages/latest/man1/yum-utils.1.html), [CentOS](http://rpmfind.net/linux/rpm2html/search.php?query=yum-utils&system=centos))

## Repository definition

Repo URL must be a valid http, https or file URL. This plugin looks for the presence of **${REPO_URL}/repodata/**[repomd.xml](http://createrepo.baseurl.org/wiki) to ascertain validity. Basic authentication (`https://user:password@yum.example.com/repo`) is supported for http and https repositories.

## Package definition

In case of this plugin, the package definition is completely determined by the package spec. The package spec may be in any of the following formats. Please refer the [repoquery man page](https://linux.die.net/man/1/repoquery) for details.

```
name
name.arch
name-ver
name-ver-rel
name-ver-rel.arch
name-epoch:ver-rel.arch
epoch:name-ver-rel.arch
```

[Shell glob patterns](https://linux.die.net/man/7/glob) may also be used. For example, say we have a component under development getting ready for release of version 1.2.0. We cut a branch for the release and bump up the version on trunk/master to 1.3.0. Thus, a package generated by trunk/master may look like `mycomp-1.3.0-b72349-noarch.rpm` while that generated by branch may look like `mycomp-1.2.0-b72344-noarch.rpm`. Now if we have a deployment pipeline that is only interested in 1.2 series packages, the package spec needs to be `mycomp-1.2.*` rather than just `mycomp`.

## Package Metadata

The following [rpm metadata](http://ftp.rpm.org/max-rpm/s1-rpm-inside-tags.html) is accessed by the plugin

1. BuildTime (required, automatically set by `rpmbuild`) - Used by the plugin to validate if the package is newer than what was last seen by GoCD. GoCD displays this field as Modified On.
2. Packager - GoCD displays this field as Modified By. If not provided, it is shown as anonymous
3. URL - Displayed as a Trackback URL by GoCD. Use this as a means to trace back to the job that published the package (within GoCD or outside) to the yum repository.
4. BuildHost - Displayed by GoCD as Comment: Built on `$BUILDHOST`

## Published Environment Variables

The following information is made available as environment variables for tasks:

```
GO_PACKAGE_< REPO-NAME >_< PACKAGE-NAME >_LABEL
GO_REPO_< REPO-NAME >_< PACKAGE-NAME >_REPO_URL
GO_PACKAGE_< REPO-NAME >_< PACKAGE-NAME >_PACKAGE_SPEC
GO_PACKAGE_< REPO-NAME >_< PACKAGE-NAME >_LOCATION
```
Individual plugins may provide additional info via additional environment variables.

## Downloading RPMs

Let's say we set up a package repository named [ORA](http://public-yum.oracle.com/repo/OracleLinux/OL6/latest/x86_64) and define a package `gcc` with a spec of `gcc-4.*` and set it up as material for a pipeline. To download the package locally on the agent, we could write a task like this:

```
[go] Start to execute task: <exec command="/bin/bash" >
<arg>-c</arg>
<arg>curl -o /tmp/gcc.rpm $GO_PACKAGE_ORA_GCC_LOCATION</arg>
</exec>
```
When the task executes on the agent, the environment variables get subsituted as below:

```
[go] Start to execute task: <exec command="/bin/bash" >
<arg>-c</arg>
<arg>curl -o /tmp/$GO_PACKAGE_ORA_GCC_LABEL.rpm $GO_PACKAGE_ORA_GCC_LOCATION</arg>
</exec>.
...
[go] setting environment variable 'GO_PACKAGE_ORA_GCC_LABEL' to value 'gcc-4.4.7-3.el6.x86_64'
[go] setting environment variable 'GO_REPO_ORA_GCC_REPO_URL' to value 'http://public-yum.oracle.com/repo/OracleLinux/OL6/latest/x86_64'
[go] setting environment variable 'GO_PACKAGE_ORA_GCC_PACKAGE_SPEC' to value 'gcc-4.*'
[go] setting environment variable 'GO_PACKAGE_ORA_GCC_LOCATION' to value 'http://public-yum.oracle.com/repo/OracleLinux/OL6/latest/x86_64/getPackage/gcc-4.4.7-3.el6.x86_64.rpm'
...
```

Or, to simply pass it as an argument to a deploy script on a remote server
```
<exec command="/bin/bash">
  <arg>-c</arg>
  <arg>ssh server "cd /to/dest/dir;deploy.sh $GO_PACKAGE_ORA_GCC_LOCATION"</arg>
</exec>
```

## Installing RPMs

For self contained packages (no external dependencies other than what is already installed on the target node), it is just enough to do:

```shell
rpm -U /path/to/downloaded/pkg.rpm
```

On the other hand, if the package isn't self-contained, we'd run:

```shell
yum install $GO_PACKAGE_ORA_GCC_LABEL
```

This would require that `/etc/yum.repos.d` contain the repository definitions.

## Creating and Publishing RPMs

Although the support for package as material in GoCD isn't concerned with how the packages are created and published, here is a short set of pointers to information on the web.
- [Building an RPM using rpmbuild and SPEC file](https://www.ibm.com/developerworks/library/l-rpm1/#first_rpm)
- [Building using fpm](https://en.wikipedia.org/wiki/List_of_software_package_management_systems#Application-level_package_managers)
- [Tutorial](https://www.howtoforge.com/creating_a_local_yum_repository_centos) to set up a local yum repository using [createrepo](https://linux.die.net/man/8/createrepo). Publishing to a yum repo simply involves uploading/copying over a new package revision at the correct location and running createrepo --update

## Notes

1. This plugin will detect at max one package revision per minute (the default interval at which GoCD materials poll). If multiple versions of a package get published to a repo in the time interval between two polls, GoCD will only register the latest version in that interval.
2. This plugin makes use of buildtime in rpm metadata to determine if a poll has returned a new result. If for some reason (e.g. timezone misconfiguration), the buildtime of pkg-1.1 is less than that of pkg-1.0, then the plugin will not register pkg-1.1 as a newer package.
3. The only way to update an rpm is to change the version or release. [Republishing](https://unix.stackexchange.com/questions/71288/does-yum-use-package-buildtime-to-decide-if-a-package-is-newer) a different file with the same name and different buildtime won't do.
4. Package groups are not supported.
5. The [GoCD command repository](https://github.com/gocd/go-command-repo/tree/master/package/rpm) has a bunch of commands related to rpm packages.

## Building the code base

To build the jar, run `./gradlew clean test assemble`

## Troubleshooting

### Enable Debug Logs

* On Linux:

    Enabling debug level logging can help you troubleshoot an issue with this plugin. To enable debug level logs, edit the file `/etc/default/go-server` (for Linux) to add:

    ```shell
    export GO_SERVER_SYSTEM_PROPERTIES="$GO_SERVER_SYSTEM_PROPERTIES -Dplugin.yum.log.level=debug"
    ```

    If you're running the server via `./server.sh` script:

    ```shell
    $ GO_SERVER_SYSTEM_PROPERTIES="-Dplugin.yum.log.level=debug" ./server.sh
    ```

## License

```plain
Copyright 2017 ThoughtWorks, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```