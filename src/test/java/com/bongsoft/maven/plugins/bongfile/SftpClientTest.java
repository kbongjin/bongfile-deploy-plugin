package com.bongsoft.maven.plugins.bongfile;

import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.*;

/**
 * @author : 권봉진
 * @Project : egoveframework-all-in-one
 * @Package : com.bongsoft.maven.plugins.bongfile
 * @File : JUnit4 Test Class.java.java
 * @Title : {간단한 프로그램의 명칭을 기록}
 * @date : 2025-08-02
 * @descrption : {상세한 프로그램의 용도를 기록}
 */
public class SftpClientTest {

	@Test
	public void uploadFile() {
		try (SftpClient client = new SftpClient.Builder()
				.withRemoteHost("43.201.31.215")
				.withRemotePort(22022)
				.withRemoteUser("centos")
				.withPrivateKeyPath("C:/projects/202505_hallym/im_dev_keyfair_20250722.pem")
				.build()) {

			client.uploadFile(Paths.get("C:/projects/202505_hallym/function_create.sql"), "/home/centos/function_create.sql");
			System.out.println("업로드 완료!");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}