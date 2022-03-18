/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.robertmetzger;

import com.beust.jcommander.Parameter;

/**
 * Command-lind arguments for the {@link App}.
 */
final class Arguments {
	@Parameter(
			names = {"--repo", "-r"},
			required = true,
			description = "The repo to observe.")
	String repo;

	@Parameter(
			names = {"--user", "-u"},
			required = true,
			description = "The GitHub account name to use for labeling PRs.")
	String username;

	@Parameter(
			names = {"--token", "-t"},
			required = true,
			description = "The GitHub authorization token with write permissions for the repository.")
	String githubToken;

	@Parameter(
			names = {"--jiraUrl", "-j"},
			required = true,
			description = "The JIRA project.")
	String jiraUrl;

	@Parameter(
			names = {"--pollInterval", "-p"},
			required = false,
			description = "The polling interval in seconds.")
	int pollingIntervalInSeconds = 300;

	@Parameter(
			names = {"--validationDuration", "-i"},
			required = false,
			description = "The validation duration for fetched jira labels.")
	int validationDurationInSeconds = 300;

	@Parameter(
			names = {"--cacheDir", "-c"},
			required = true,
			description = "The directory where data is cached."
	)
	String cacheDir;

	@Parameter(
        names = {"--mainCacheSize"},
        required = false)
	int mainCacheMB;

	@Parameter(
			names = {"--help", "-h"},
			help = true,
			hidden = true)
	boolean help = false;
}
