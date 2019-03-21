/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.xd.cloud;

/**
 * Exception for when a service failed to start after a specified period of time.
 * 
 * @author Glenn Renfro
 */
public class DeployTimeoutException extends RuntimeException {


	public DeployTimeoutException() {
	}

	public DeployTimeoutException(String message) {
		super(message);
	}

	public DeployTimeoutException(Throwable cause) {
		super(cause);
	}

	public DeployTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}

	public DeployTimeoutException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
