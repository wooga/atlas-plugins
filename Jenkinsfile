#!groovy
/*
 * Copyright 2018-2021 Wooga GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@Library('github.com/wooga/atlas-jenkins-pipeline@1.x') _

withCredentials([usernamePassword(credentialsId: 'github_integration', passwordVariable: 'githubPassword', usernameVariable: 'githubUser'),
                 usernamePassword(credentialsId: 'github_integration_2', passwordVariable: 'githubPassword2', usernameVariable: 'githubUser2'),
                 usernamePassword(credentialsId: 'github_integration_3', passwordVariable: 'githubPassword3', usernameVariable: 'githubUser3'),
                 string(credentialsId: 'atlas_plugins_coveralls_token', variable: 'coveralls_token'),
                 string(credentialsId: 'atlas_plugins_sonar_token', variable: 'sonar_token')]) {
    def testEnvironment = [
        'osx':
        [
                "ATLAS_GITHUB_INTEGRATION_USER=${githubUser}",
                "ATLAS_GITHUB_INTEGRATION_PASSWORD=${githubPassword}"
        ],
        'windows':
        [
                "ATLAS_GITHUB_INTEGRATION_USER=${githubUser2}",
                "ATLAS_GITHUB_INTEGRATION_PASSWORD=${githubPassword2}"
        ],
        'linux':
        [
                "ATLAS_GITHUB_INTEGRATION_USER=${githubUser3}",
                "ATLAS_GITHUB_INTEGRATION_PASSWORD=${githubPassword3}"
        ]
    ]


    buildGradlePlugin plaforms: ['osx','windows','linux'], coverallsToken: coveralls_token, sonarToken: sonar_token, testEnvironment: testEnvironment
}
