/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.xd.ec2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class Banner implements ApplicationContextAware {
	@Resource
	private transient ApplicationContext context;
	private static final Logger LOGGER = LoggerFactory.getLogger(Banner.class);

	public void print() {
		BufferedReader stream = null;
		try {
			final InputStream inputStream = context.getResource("banner.txt")
					.getInputStream();
			stream = new BufferedReader(new InputStreamReader(inputStream));
			while (stream.ready()) {
				LOGGER.info(stream.readLine());
			}
		} catch (IOException ioe) {
			// Ignore as this is not essential for the application
		} finally {
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException ioe) {
					// Not essential, just keep going.
				}
			}
		}
	}

	public void setApplicationContext(final ApplicationContext applicationContext)
			throws BeansException {
		this.context = applicationContext;

	}

}
