package com.example.mojo;

import com.jcraft.jsch.*;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

@Mojo(name = "upload-changes", defaultPhase = LifecyclePhase.VERIFY)
public class UploadChangesMojo extends AbstractMojo {

    @Parameter(property = "commits", required = true)
    private String commits;

    @Parameter(property = "remoteHost", defaultValue = "a.server.com")
    private String remoteHost;

    @Parameter(property = "remotePort", defaultValue = "22")
    private int remotePort;

    @Parameter(property = "remoteUser", defaultValue = "yourUser")
    private String remoteUser;

    @Parameter(property = "remotePassword", defaultValue = "yourPassword")
    private String remotePassword;

    @Parameter(property = "remotePath", defaultValue = "/opt/tomcat/webapps/your-context/")
    private String remotePath;

    private static final List<String> VALID_EXTENSIONS = Arrays.asList(".class", ".xml", ".jsp", ".js", ".css");

    public void execute() throws MojoExecutionException {
        try {
            Set<String> changedFiles = getChangedFiles(Arrays.asList(commits.split(",")));

            getLog().info("Changed Files:");
            changedFiles.forEach(getLog()::info);

            runCommand("mvn clean install -DskipTests");

            List<Path> builtFiles = findBuiltFiles(changedFiles);
            uploadFiles(builtFiles);

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to upload changed files", e);
        }
    }

    private Set<String> getChangedFiles(List<String> commitList) throws IOException, InterruptedException {
        Set<String> files = new HashSet<>();
        for (int i = 0; i < commitList.size() - 1; i++) {
            String cmd = String.format("git diff --name-only %s %s", commitList.get(i), commitList.get(i + 1));
            Process process = new ProcessBuilder("bash", "-c", cmd).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                reader.lines().forEach(files::add);
            }
            process.waitFor();
        }
        return files;
    }

    private void runCommand(String command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("bash", "-c", command).inheritIO().start();
        process.waitFor();
    }

    private List<Path> findBuiltFiles(Set<String> changedFiles) {
        List<Path> result = new ArrayList<>();
        Path baseDir = Paths.get("").toAbsolutePath();
        Path targetDir = baseDir.resolve("target");

        for (String file : changedFiles) {
            String ext = file.contains(".") ? file.substring(file.lastIndexOf('.')) : "";
            if (!VALID_EXTENSIONS.contains(ext)) continue;

            Path targetPath = resolveBuiltPath(file, ext, targetDir, baseDir);
            if (targetPath != null && Files.exists(targetPath)) {
                result.add(targetPath);
            }
        }
        return result;
    }

    private Path resolveBuiltPath(String sourcePath, String ext, Path targetDir, Path baseDir) {
        if (ext.equals(".class")) {
            String classPath = sourcePath
                    .replace("src/main/java/", "")
                    .replace(".java", ".class")
                    .replace("/", File.separator);
            return targetDir.resolve("classes").resolve(classPath);
        } else {
            return baseDir.resolve(sourcePath);
        }
    }

    private void uploadFiles(List<Path> files) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(remoteUser, remoteHost, remotePort);
        session.setPassword(remotePassword);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        Channel channel = session.openChannel("sftp");
        channel.connect();
        ChannelSftp sftp = (ChannelSftp) channel;

        for (Path file : files) {
            String remoteFile = remotePath + file.getFileName().toString();
            getLog().info("Uploading: " + file + " → " + remoteFile);
            sftp.put(file.toAbsolutePath().toString(), remoteFile);
        }

        sftp.exit();
        session.disconnect();
    }
}
