package com.bongsoft.maven.plugins.bongfile;

import com.jcraft.jsch.*;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Mojo(name = "upload-changes", defaultPhase = LifecyclePhase.VERIFY)
public class UploadChangesMojo extends AbstractMojo {

    private static final List<String> VALID_EXTENSIONS = Arrays.asList(
            ".java", ".xml", ".jsp", ".jspf", ".js", ".css", ".properties", ".html", ".png", ".gif", ".jpg"
    );
    private static final List<String> SKIP_FILES = Arrays.asList(
            "pom.xml", "context-crypto-test.xml", "globals.properties", "README.md", "LICENSE", ".gitignore"
    );

    private static final String SRC_MAIN_JAVA = "src" + File.separator + "main" + File.separator + "java" + File.separator;
    private static final String SRC_MAIN_WEBAPP = "src" + File.separator + "main" + File.separator + "webapp" + File.separator;

    @Parameter(property = "commits")
    private String commits;

    @Parameter(property = "svnRevisions")
    private String svnRevisions; // 예: "100:105"

    @Parameter(property = "appRootPath", defaultValue = "target/egovframework-all-in-one")
    private String appRootPath;

    @Parameter(property = "remoteHost")
    private String remoteHost;

    @Parameter(property = "remotePort", defaultValue = "22")
    private int remotePort;

    @Parameter(property = "remoteUser", defaultValue = "yourUser")
    private String remoteUser;

    @Parameter(property = "remotePassword", defaultValue = "yourPassword")
    private String remotePassword;

    @Parameter(property = "privateKeyPath")
    private String privateKeyPath;

    @Parameter(property = "passphrase")
    private String passphrase;

    @Parameter(property = "remotePath", defaultValue = "/opt/deploy/")
    private String remotePath;

    @Parameter(property = "uploadFilePath")
    private String uploadFilePath;

    @Parameter(property = "directUpload", defaultValue = "false")
    private boolean directUpload;

    private final Pattern pattern = Pattern.compile("^src[/\\\\]main[/\\\\]resources(?:-[a-zA-Z0-9]+)?([/\\\\].*)?$");

    public void setAppRootPath(String appRootPath) {
        this.appRootPath = appRootPath;
    }

    public void execute() throws MojoExecutionException {

        try {
            if (uploadFilePath != null && !uploadFilePath.isEmpty()) {
                getLog().info("Using uploadFilePath: " + uploadFilePath);
                Path uploadFile = Paths.get(uploadFilePath).toAbsolutePath();
                if (!Files.exists(uploadFile)) {
                    throw new MojoExecutionException("Upload file does not exist: " + uploadFile);
                }
                if (directUpload) {
                    uploadIndividualFiles(Collections.singletonList(uploadFile), null);
                } else {
                    uploadFiles(Collections.singletonList(uploadFile));
                }
            } else {
                getLog().info("Using default appRootPath: " + appRootPath);

                Set<String> changedFiles = getChangedFiles();

                getLog().info("Changed Files: " + changedFiles.size() + " files");
                changedFiles.forEach(getLog()::info);

                List<Path> builtFiles = findBuiltFiles(changedFiles);

                getLog().info("Built Files: " + builtFiles.size() + " files");

                if (directUpload) {
                    Path appRootDir = Paths.get("").toAbsolutePath().resolve(appRootPath);
                    uploadIndividualFiles(builtFiles, appRootDir);
                } else {
                    uploadFiles(Collections.singletonList(compressFiles(builtFiles)));
                }
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to upload changed files", e);
        }
    }

    private Path compressFiles(List<Path> builtFiles) throws Exception {

        Path basePath = Paths.get("").toAbsolutePath();
        Path appRootDir = basePath.resolve(appRootPath);// 예: 압축할 기준 경로 (루트)


        // 압축 파일 경로
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String tarGzFileName = "deploy-" + timestamp + ".tar.gz";
        File tarGzFile = new File(appRootDir.getParent().toFile(), tarGzFileName);

        // 압축 스트림 설정
        try (
                FileOutputStream fos = new FileOutputStream(tarGzFile);
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(bos);
                TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)
        ) {
            taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

            int compressedFileSize = 0;
            for (Path filePath : builtFiles) {
                if (!filePath.startsWith(appRootDir)) {
                    getLog().info("  Skip compress target. outside appRootDir: " + filePath);
                    continue;
                }

                String relativePath = appRootDir.relativize(filePath).toString();
                TarArchiveEntry entry = new TarArchiveEntry(filePath.toFile(), relativePath);
                taos.putArchiveEntry(entry);
                Files.copy(filePath, taos);
                taos.closeArchiveEntry();
                getLog().info("Compressed: " + relativePath);
                compressedFileSize++;
            }


            taos.finish();
            getLog().info("Compressed files("+ compressedFileSize +"): " + tarGzFile.getAbsolutePath());
        }
        return tarGzFile.toPath();
    }

    private Set<String> getChangedFiles() throws IOException, InterruptedException {
        Set<String> files = new HashSet<>();

        if (commits != null && !commits.isEmpty()) {
            for (String token : commits.split(",")) {
                String trimmed = token.trim();
                String cmd;
                if (trimmed.contains("..")) {
                    cmd = String.format("git diff --name-only %s", trimmed);
                } else {
                    cmd = String.format("git show --name-only --pretty=format: %s", trimmed);
                }
                List<String> lines = runAndCollect(cmd);
                lines.stream().filter(l -> !l.isEmpty()).forEach(files::add);
            }
        } else if (svnRevisions != null && !svnRevisions.isEmpty()) {
            String cmd = String.format("svn diff -r %s --summarize", svnRevisions);
            List<String> lines = runAndCollect(cmd);
            for (String line : lines) {
                // Format: "M       path/to/file"
                String[] parts = line.trim().split("\\s+", 2);
                if (parts.length == 2) {
                    files.add(parts[1]);
                }
            }
        } else {
            throw new IllegalArgumentException("You must specify either 'commits' or 'svnRevisions'");
        }

        return files;
    }

    private List<String> runAndCollect(String command) throws IOException, InterruptedException {
        List<String> output = new ArrayList<>();
        Process process = new ProcessBuilder("cmd", "/c", command).redirectErrorStream(true).start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            reader.lines().forEach(output::add);
        }
        process.waitFor();
        return output;
    }

    private List<Path> findBuiltFiles(Set<String> changedFiles) {
        List<Path> result = new ArrayList<>();
        Path baseDir = Paths.get("").toAbsolutePath();
        Path appRootDir = baseDir.resolve(appRootPath);

	    changedFiles.forEach(file -> {
		    if (SKIP_FILES.stream().anyMatch(file::endsWith)) {
			    getLog().info("  Skip " + file);
			    return;
		    }
		    String ext = file.contains(".") ? file.substring(file.lastIndexOf('.')) : "";
		    if (!VALID_EXTENSIONS.contains(ext)) {
                getLog().info("  Skip " + file + " (invalid extension: " + ext + ")");
                return;
            }
		    Path targetPath = resolveBuiltPath(file, appRootDir);
		    if (Files.exists(targetPath)) {
			    result.add(targetPath);
			    if (file.endsWith(".java")) {
				    String baseName = targetPath.getFileName().toString().replace(".class", "");
				    try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetPath.getParent(), baseName + "$*.class")) {
					    stream.forEach(result::add);
				    } catch (IOException e) {
					    getLog().warn("  Failed to scan inner classes: " + e.getMessage());
				    }
			    }
		    } else {
                getLog().warn("  File does not exist: " + targetPath);
            }
	    });
        return result;
    }

    protected Path resolveBuiltPath(String sourcePath, Path appRootDir) {
        sourcePath = sourcePath.replace("/", File.separator).replace("\\", File.separator);
        if (sourcePath.endsWith(".java")) {
            String classPath = sourcePath
                    .replace(SRC_MAIN_JAVA, "")
                    .replace(".java", ".class")
                    .replace("\\", File.separator);
            return appRootDir.resolve("WEB-INF" + File.separator + "classes").resolve(classPath);
        } else if (pattern.matcher(sourcePath).matches()) {
            String resourcePath = sourcePath
                    .replace("\\", "/")
                    .replaceFirst("^src/main/resources(?:-[a-zA-Z0-9]+)?/", "");
            return appRootDir.resolve("WEB-INF" + File.separator + "classes").resolve(resourcePath);
        } else {
            String webappPath = sourcePath
                    .replace(SRC_MAIN_WEBAPP, "")
                    .replace("\\", File.separator);
            return appRootDir.resolve(webappPath);
        }
    }

    private void uploadFiles(List<Path> files) throws Exception {
        Path baseDir = Paths.get("").toAbsolutePath();
        try (SftpClient sftpClient = createSftpClient()) {
            for (Path file : files) {
                String remoteFile = remotePath + file.getFileName().toString();
                sftpClient.uploadFile(file, remoteFile);
                getLog().info("Uploaded: " + baseDir.relativize(file) + " → " + remoteFile);
            }
        }
    }

    private void uploadIndividualFiles(List<Path> files, Path appRootDir) throws Exception {
        String remoteBase = remotePath.endsWith("/") ? remotePath : remotePath + "/";
        try (SftpClient sftpClient = createSftpClient()) {
            for (Path file : files) {
                String relativePath = (appRootDir != null && file.startsWith(appRootDir))
                        ? appRootDir.relativize(file).toString().replace(File.separator, "/")
                        : file.getFileName().toString();

                String remoteFilePath = remoteBase + relativePath;
                String remoteDir = remoteFilePath.substring(0, remoteFilePath.lastIndexOf('/'));

                sftpClient.mkdirs(remoteDir);

                if (sftpClient.exists(remoteFilePath)) {
                    String bakPath = remoteFilePath + ".bak";
                    if (sftpClient.exists(bakPath)) {
                        int i = 1;
                        while (sftpClient.exists(remoteFilePath + ".bak" + i)) {
                            i++;
                        }
                        bakPath = remoteFilePath + ".bak" + i;
                    }
                    sftpClient.rename(remoteFilePath, bakPath);
                    getLog().info("  Backed up: " + remoteFilePath + " → " + bakPath);
                }

                sftpClient.uploadFile(file, remoteFilePath);
                getLog().info("Uploaded: " + relativePath + " → " + remoteFilePath);
            }
        }
    }

    private SftpClient createSftpClient() throws JSchException, MojoExecutionException {
        return new SftpClient.Builder()
                .withRemoteHost(remoteHost)
                .withRemotePort(remotePort)
                .withRemoteUser(remoteUser)
                .withRemotePassword(remotePassword)
                .withPrivateKeyPath(privateKeyPath)
                .withPassphrase(passphrase)
                .build();
    }

}
