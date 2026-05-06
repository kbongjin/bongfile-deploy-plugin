package com.bongsoft.maven.plugins.bongfile;

import com.jcraft.jsch.*;
import org.apache.maven.plugin.MojoExecutionException;

import java.nio.file.Path;

/**
 * @author : 권봉진
 * @Project : egoveframework-all-in-one
 * @Package : com.bongsoft.maven.plugins.bongfile
 * @File : null.java
 * @Title : {간단한 프로그램의 명칭을 기록}
 * @date : 2025-08-02
 * @descrption : {상세한 프로그램의 용도를 기록}
 */
public class SftpClient implements AutoCloseable {
	private final Session session;
	private ChannelSftp sftpChannel;

	private SftpClient(Session session) {
		this.session = session;
	}

	public void uploadFile(Path localFile, String remoteFilePath) throws SftpException {
		sftpChannel.put(localFile.toAbsolutePath().toString(), remoteFilePath);
	}

	public boolean exists(String remoteFilePath) {
		try {
			sftpChannel.lstat(remoteFilePath);
			return true;
		} catch (SftpException e) {
			return false;
		}
	}

	public void rename(String from, String to) throws SftpException {
		sftpChannel.rename(from, to);
	}

	public void mkdirs(String remoteDir) throws SftpException {
		if (exists(remoteDir)) return;
		int lastSlash = remoteDir.lastIndexOf('/');
		if (lastSlash > 0) {
			mkdirs(remoteDir.substring(0, lastSlash));
		}
		try {
			sftpChannel.mkdir(remoteDir);
		} catch (SftpException e) {
			if (!exists(remoteDir)) throw e;
		}
	}

	public void connect() throws JSchException {
		session.connect();
		Channel channel = session.openChannel("sftp");
		channel.connect();
		sftpChannel = (ChannelSftp) channel;
	}

	@Override
	public void close() {
		if (sftpChannel != null) {
			sftpChannel.exit();
		}
		if (session != null) {
			session.disconnect();
		}
	}

	public static class Builder {
		private String remoteHost;
		private int remotePort;
		private String remoteUser;
		private String remotePassword;
		private String privateKeyPath;
		private String passphrase;

		public Builder withRemoteHost(String remoteHost) {
			this.remoteHost = remoteHost;
			return this;
		}

		public Builder withRemotePort(int remotePort) {
			this.remotePort = remotePort;
			return this;
		}

		public Builder withRemoteUser(String remoteUser) {
			this.remoteUser = remoteUser;
			return this;
		}

		public Builder withRemotePassword(String remotePassword) {
			this.remotePassword = remotePassword;
			return this;
		}

		public Builder withPrivateKeyPath(String privateKeyPath) {
			this.privateKeyPath = privateKeyPath;
			return this;
		}

		public Builder withPassphrase(String passphrase) {
			this.passphrase = passphrase;
			return this;
		}

		public SftpClient build() throws JSchException, MojoExecutionException {
			JSch jsch = new JSch();

			if (privateKeyPath != null && !privateKeyPath.isEmpty()) {
				if (passphrase != null && !passphrase.isEmpty()) {
					jsch.addIdentity(privateKeyPath, passphrase);
				} else {
					jsch.addIdentity(privateKeyPath);
				}
			}

			Session session = jsch.getSession(remoteUser, remoteHost, remotePort);

			if (privateKeyPath == null || privateKeyPath.isEmpty()) {
				if (remotePassword == null || remotePassword.isEmpty()) {
					throw new MojoExecutionException("remotePassword 또는 privateKeyPath를 설정 해야 합니다.");
				}
				session.setPassword(remotePassword);
			}

			session.setConfig("StrictHostKeyChecking", "no");

			SftpClient client = new SftpClient(session);
			client.connect();
			return client;
		}
	}

}
