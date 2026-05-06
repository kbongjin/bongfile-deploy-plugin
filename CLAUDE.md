# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Maven plugin (`com.bongsoft:bongfile-deploy-plugin`) that automates deployment of changed files via SFTP. It detects changed files from Git commits or SVN revisions, maps source files to their compiled/built output locations, compresses them into a tar.gz archive, and uploads the archive to a remote server via SFTP.

## Build Commands

```bash
# Build the plugin
mvn clean package

# Install locally for testing
mvn clean install

# Run tests
mvn test

# Run a single test
mvn test -Dtest=UploadChangesMojoTest

# Deploy to internal Nexus
mvn deploy
```

## Plugin Usage

```bash
mvn deploy:upload-changes \
  -DsvnRevisions=100:105 \
  -DremoteHost=a.server.com \
  -DremoteUser=username \
  -DprivateKeyPath=~/.ssh/id_rsa \
  -DremotePath=/opt/tomcat/webapps/myapp/
```

Key parameters: `commits` (Git commit range, comma-separated), `svnRevisions` (SVN revision range like "100:105"), `appRootPath` (default: `target/egovframework-all-in-one`), `uploadFilePath` (direct file path, bypasses auto-detection), and SFTP connection params (`remoteHost`, `remoteUser`, `privateKeyPath`, `remotePath`).

## Architecture

Two source files in `src/main/java/com/bongsoft/maven/plugins/bongfile/`:

- **UploadChangesMojo** - The Maven Mojo (goal: `upload-changes`, phase: VERIFY). Orchestrates the full pipeline:
  1. `getChangedFiles()` - Runs `git diff --name-only` or `svn diff --summarize` via subprocess
  2. `findBuiltFiles()` - Maps source paths to compiled output paths, filtering by `VALID_EXTENSIONS` and `SKIP_FILES`
  3. `resolveBuiltPath()` - Path resolution logic: `.java` → `WEB-INF/classes/*.class`, `src/main/resources(-profile)/` → `WEB-INF/classes/`, `src/main/webapp/` → root
  4. `compressFiles()` - Creates timestamped `deploy-*.tar.gz` using Commons Compress
  5. `uploadFiles()` - Sends archive via SftpClient
  - If `uploadFilePath` is set, skips steps 1-4 and uploads the specified file directly

- **SftpClient** - JSch-based SFTP client with Builder pattern. Supports password and SSH private key authentication. Implements AutoCloseable.

## Key Conventions

- Targets egovframework-based web app structure (WEB-INF/classes layout)
- Subprocesses run via `cmd /c` (Windows-specific)
- File filtering lists (`VALID_EXTENSIONS`, `SKIP_FILES`) are hardcoded in UploadChangesMojo
- Deployed to internal Nexus at `http://43.201.31.215:7091/`
- Java 8+ compatible, Maven 3.9.6, JUnit 4 for tests