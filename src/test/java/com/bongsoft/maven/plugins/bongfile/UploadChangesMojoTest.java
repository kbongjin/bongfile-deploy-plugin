package com.bongsoft.maven.plugins.bongfile;

import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

/**
 * @author : 권봉진
 * @Project : egoveframework-all-in-one
 * @Package : com.bongsoft.maven.plugins.bongfile
 * @File : JUnit4 Test Class.java.java
 * @Title : {간단한 프로그램의 명칭을 기록}
 * @date : 2025-08-05
 * @descrption : {상세한 프로그램의 용도를 기록}
 */
public class UploadChangesMojoTest {

	UploadChangesMojo uploadChangesMojo = new UploadChangesMojo();

	@Before
	public void setUp() throws Exception {
		//uploadChangesMojo.setAppRootPath("target/egovframework-all-in-one");
	}

	@Test
	public void resolveBuiltPath() {
		//given
		Path baseDir = Paths.get("").toAbsolutePath();
		Path appRootDir = baseDir.resolve("target/egovframework-all-in-one");

		//when
		Path path = uploadChangesMojo.resolveBuiltPath("src\\main\\resources\\egovframework\\egovProps\\globals.properties", appRootDir);
		Path path2 = uploadChangesMojo.resolveBuiltPath("src\\main\\resources-dev\\egovframework\\egovProps\\globals.properties", appRootDir);
		Path path3 = uploadChangesMojo.resolveBuiltPath("src\\main\\webapp\\WEB-INF\\config\\egovframework\\springmvc\\egov-com-servlet.xml", appRootDir);
		Path path4 = uploadChangesMojo.resolveBuiltPath("src\\main\\java\\com\\intermorph\\cmmn\\service\\IMCmmnSystemPropertyService.java", appRootDir);

		//then
		System.out.println("path = " + path);
		assertTrue(path.endsWith("target\\egovframework-all-in-one\\WEB-INF\\classes\\egovframework\\egovProps\\globals.properties"));
		assertTrue(path2.endsWith("target\\egovframework-all-in-one\\WEB-INF\\classes\\egovframework\\egovProps\\globals.properties"));
		assertTrue(path3.endsWith("target\\egovframework-all-in-one\\WEB-INF\\config\\egovframework\\springmvc\\egov-com-servlet.xml"));
		assertTrue(path4.endsWith("target\\egovframework-all-in-one\\WEB-INF\\classes\\com\\intermorph\\cmmn\\service\\IMCmmnSystemPropertyService.class"));
	}


}