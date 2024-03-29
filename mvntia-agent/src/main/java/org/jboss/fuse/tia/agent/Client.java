/*
 * Copyright 2020 the original author or authors.
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
package org.jboss.fuse.tia.agent;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface Client {

    Set<String> disabledTests(String project, String digest);

    void addReport(String project, String test, Collection<String> classes);

    void writeReport(String project, String digest);

    void log(String project, String level, String message);

}
