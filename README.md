atlas-plugins
=============

![Wooga Internal](https://img.shields.io/badge/wooga-internal-lightgray.svg?style=flat-square)
[![Gradle Plugin ID](https://img.shields.io/badge/gradle-net.wooga.github-brightgreen.svg?style=flat-square)](https://plugins.gradle.org/plugin/net.wooga.plugins)
[![Build Status](https://img.shields.io/travis/wooga/atlas-plugins/master.svg?style=flat-square)](https://travis-ci.org/wooga/atlas-plugins)
[![Coveralls Status](https://img.shields.io/coveralls/wooga/atlas-plugins/master.svg?style=flat-square)](https://coveralls.io/github/wooga/atlas-plugins?branch=master)
[![Apache 2.0](https://img.shields.io/badge/license-Apache%202-blue.svg?style=flat-square)](https://raw.githubusercontent.com/wooga/atlas-plugins/master/LICENSE)
[![GitHub tag](https://img.shields.io/github/tag/wooga/atlas-plugins.svg?style=flat-square)]()
[![GitHub release](https://img.shields.io/github/release/wooga/atlas-plugins.svg?style=flat-square)]()

Plugin to establish conventions for a atlas gradle plugins. This plugin is used to help setup our other plugins.

Usage
=====

**build.gradle**

```groovy
plugins {
  id "net.wooga.unity" version "1.0.0"
}
```

It applies the following plugins:

* 'groovy'
* 'idea'
* 'publish'
* 'plugin-publish'
* 'nebular.release'
* 'jacoco'
* 'com.github.kt3k.coveralls'
* 'net.wooga.github'
* 'maven-publish'

### Testing/Development

The resulting project structure is prepared as a normal groovy gradle plugin. Additional to the normal `test` task the plugin also adds a `integrationTest` task with code coverage and reporting. As test framework `Spock` will be added to the classpath. The jocoo plugin is used for code coverage. The integration test results are merged with the unit test results. All our plugins push the coverage report to coveralls. The coveralls plugin is connected to jacoco and only needs the `COVERALLS_TOKEN` in the environment. The plugin will also generate an idea project with the integration tests configured as seperate module.

### Publish/Release

The project uses [Nebular Release](https://github.com/nebula-plugins/nebula-release-plugin) for the release and publish cycle. The tasks `final` and `candidate` will publish the plugin to the gradle plugins repository with the help of the `plugin-publish` plugin and create a formal gitub release with the `net.wooga.github` plugin. The `snapshot` task will only publish to the local maven repository.


Documentation
=============

- [API docs](https://wooga.github.io/atlas-plugins/docs/api/)
- [Release Notes](RELEASE_NOTES.md)

Development
===========

[Code of Conduct](docs/Code-of-conduct.md)

LICENSE
=======

Copyright 2017 Wooga GmbH

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
