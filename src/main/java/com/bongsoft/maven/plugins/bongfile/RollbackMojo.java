package com.bongsoft.maven.plugins.bongfile;

import com.jcraft.jsch.JSchException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

@Mojo(name = "rollback-changes")
public class RollbackMojo extends AbstractMojo {

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

    @Parameter(property = "manifestFile")
    private String manifestFile;

    public void execute() throws MojoExecutionException {
        try {
            Path manifest = resolveManifest();
            getLog().info("Using manifest: " + manifest.toAbsolutePath());

            List<String[]> entries = readManifest(manifest);

            try (SftpClient sftpClient = createSftpClient()) {
                for (String[] entry : entries) {
                    String remoteFilePath = entry[0];
                    String bakPath = entry[1];

                    if (bakPath.isEmpty()) {
                        if (sftpClient.exists(remoteFilePath)) {
                            sftpClient.remove(remoteFilePath);
                            getLog().info("Removed (was new): " + remoteFilePath);
                        }
                    } else {
                        if (!sftpClient.exists(bakPath)) {
                            getLog().warn("Backup not found, skipping: " + bakPath);
                            continue;
                        }
                        if (sftpClient.exists(remoteFilePath)) {
                            sftpClient.remove(remoteFilePath);
                        }
                        sftpClient.rename(bakPath, remoteFilePath);
                        getLog().info("Restored: " + bakPath + " → " + remoteFilePath);
                    }
                }
            }

            getLog().info("Rollback complete. (" + entries.size() + " files)");

            String manifestName = manifest.getFileName().toString();
            String rolledBackName = manifestName.replace(".txt", "-ROLLED-BACK.txt");
            Files.move(manifest, manifest.resolveSibling(rolledBackName));
            getLog().info("Manifest renamed: " + manifestName + " → " + rolledBackName);

        } catch (Exception e) {
            throw new MojoExecutionException("Rollback failed", e);
        }
    }

    private Path resolveManifest() throws MojoExecutionException {
        if (manifestFile != null && !manifestFile.isEmpty()) {
            Path p = Paths.get(manifestFile);
            if (!Files.exists(p)) {
                throw new MojoExecutionException("Manifest file not found: " + p.toAbsolutePath());
            }
            return p;
        }

        Path targetDir = Paths.get("deploy");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDir, "deploy-manifest-*.txt")) {
            Path latest = null;
            for (Path p : stream) {
                if (latest == null || p.getFileName().toString().compareTo(latest.getFileName().toString()) > 0) {
                    latest = p;
                }
            }
            if (latest == null) {
                throw new MojoExecutionException("No deploy manifest found in deploy/. Run with -DdirectUpload=true first.");
            }
            return latest;
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to scan deploy/ for manifests", e);
        }
    }

    private List<String[]> readManifest(Path manifest) throws IOException {
        List<String[]> entries = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(manifest)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                String[] parts = line.split("\\|", 2);
                entries.add(new String[]{parts[0], parts.length > 1 ? parts[1] : ""});
            }
        }
        return entries;
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
