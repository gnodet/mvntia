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
package org.jboss.fuse.tia.maven;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jboss.fuse.tia.agent.AgentOptions;

@Mojo(name = "prepare-agent", defaultPhase = LifecyclePhase.INITIALIZE,
        requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class PrepareAgent extends AbstractMojo {

    /**
     * Name of the MvnTia Agent artifact.
     */
    static final String AGENT_ARTIFACT_NAME = "org.jboss.fuse.mvntia:mvntia-agent";
    /**
     * Name of the property used in maven-surefire-plugin.
     */
    static final String SUREFIRE_ARG_LINE = "argLine";

    static final Map<String, Server> servers = new HashMap<>();

    /**
     * Maven project.
     */
    @Parameter(property = "project", readonly = true)
    MavenProject project;

    /**
     * Flag used to suppress execution.
     */
    @Parameter(property = "mvntia.skip", defaultValue = "false")
    boolean skip;

    /**
     * Flag used to suppress execution.
     */
    @Parameter(property = "mvntia.debug", defaultValue = "false")
    boolean debug;

    /**
     * Property name to set
     */
    @Parameter(defaultValue = SUREFIRE_ARG_LINE)
    String propertyName;

    @Parameter(property = "mvntia.force")
    boolean force;

    /**
     * Map of plugin artifacts.
     */
    @Parameter(property = "plugin.artifactMap", required = true, readonly = true)
    Map<String, Artifact> pluginArtifactMap;

    @Parameter(defaultValue = "${project.groupId}:*")
    Collection<String> artifacts;

    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping mvntia execution because property mvntia.skip is set.");
        } else {
            executeMojo();
        }
    }

    protected void executeMojo() throws MojoExecutionException, MojoFailureException {
        try {
            String executionDir;
            File parent = new File(".").getAbsoluteFile();
            boolean isGit = new File(parent, ".git").exists();
            while (parent.getParentFile() != null && !isGit) {
                parent = parent.getParentFile();
                isGit = new File(parent, ".git").exists();
            }
            if (isGit) {
                executionDir = parent.getAbsolutePath();
            } else {
                throw new RuntimeException("It is not a Git repository");
            }

            Server server = servers.get(executionDir);
            if (server == null) {
                getLog().info("Creating mvntia server for git repository " + executionDir);
                server = new Server(new GitClient(executionDir, getLog()));
                servers.put(executionDir, server);
            }

            if (force) {
                getLog().warn("The mvntia.force option is set, ignoring existing TIA data");
            }

            String artifactsSet = project.getArtifacts().stream()
                    .map(Artifact::toString)
                    .collect(Collectors.joining(" "));
            String digest = digest(artifactsSet);

            Collection<ArtifactId> artifactIds = ArtifactId.toIds(artifacts);
            Set<String> reactorDeps = project.getArtifacts().stream()
                    .filter(a -> ArtifactId.matches(artifactIds, a))
                    .map(a -> a.getFile().toString())
                    .collect(Collectors.toSet());

            String id = project.getGroupId() + ":" + project.getArtifactId();
            final String name = propertyName;
            final Properties projectProperties = project.getProperties();
            final String oldValue = projectProperties.getProperty(name);
            final String newValue = new AgentOptions()
                    .digest(digest)
                    .force(force)
                    .port(server.getPort())
                    .project(id)
                    .reactorDeps(String.join(";", reactorDeps))
                    .prependVMArguments(oldValue, getAgentJarFile(), debug);
            getLog().info("Preparing surefire to run with mvntia");
            getLog().debug(name + " set to " + newValue);
            projectProperties.setProperty(name, newValue);
        } catch (Exception e) {
            throw new MojoFailureException("Error setting up agent", e);
        }
    }

    private String digest(String str) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        String digest = bytesToHex(md.digest(str.getBytes(StandardCharsets.UTF_8)));
        return digest;
    }

    File getAgentJarFile() {
        final Artifact mvntiaAgentArtifact = pluginArtifactMap.get(AGENT_ARTIFACT_NAME);
        return mvntiaAgentArtifact.getFile();
    }

    public static String bytesToHex(byte[] b) {
        char[] hexDigit = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        StringBuilder buf = new StringBuilder();
        for (byte value : b) {
            buf.append(hexDigit[(value >> 4) & 0x0f]);
            buf.append(hexDigit[value & 0x0f]);
        }
        return buf.toString();
    }
}
