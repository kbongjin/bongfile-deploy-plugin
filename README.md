# bongfile-deploy-plugin

여전히 서버 배포시 별도의 CI/CD 를 이용하지 않고 Filezilla 같은 sftp 로 개별파일을 업로드하는 경우가 많은거 같습니다.
이런 배포는 시간도 오래 걸리고 배포파일을 누락하거나 잘못 배포하는등의 문제를 발생시킬수 있습니다.
이런 환경에 조금이나마 도움이 되고자 좀더 쉽고 간단하게 배포할수 있는 플러그인을 만들었습니다.
이 플러그인은 Git 또는 SVN의 커밋 이력을 기반으로 변경된 파일을 감지하고, SFTP로 배포 서버에 업로드하는 Maven 플러그인입니다.
서버에 있는 기존 파일은 자동으로 백업해주고 배포 내역에 대한 manifest 파일을 생성하여 나중에 롤백도 가능합니다.
참고로 이 플로그인은 Java 기반 Web App 프로젝트를 기준으로 개발되었습니다.

> 자세한 내용은 [GUIDE.md](GUIDE.md)를 참고하세요.

---

## 설치

```xml
<build>
  <plugins>
    <plugin>
      <groupId>com.bongsoft</groupId>
      <artifactId>bongfile-deploy-plugin</artifactId>
      <version>1.4.0</version>
    </plugin>
  </plugins>
</build>
```

---

## 배포 방식

| 방식 | 설명                                                   |
|---|------------------------------------------------------|
| tar.gz / zip 압축 업로드 (기본) | 변경 파일을 tar.gz(기본) 또는 zip(version:1.4.1)으로 묶어 서버에 업로드 |
| 개별 파일 직접 업로드 | `-DdirectUpload=true` — 파일별 백업 후 업로드, 롤백 가능          |
| 파일 직접 지정 | `-DuploadFilePath=경로` — 변경 감지 없이 지정한 파일 업로드          |

---

## 주요 파라미터

| 파라미터 | 설명                                                     |
|---|--------------------------------------------------------|
| `commits` | Git 커밋 범위 (예: `abc123..def456`, 쉼표로 여러 개 지정 가능)        |
| `svnRevisions` | SVN 리비전 범위 (예: `100:105`)                              |
| `remoteHost` | 배포 서버 호스트명 또는 IP                                       |
| `remoteUser` | 접속 계정                                                  |
| `privateKeyPath` | SSH 개인키 경로 (권장)                                        |
| `remotePassword` | 비밀번호 인증 시 사용                                           |
| `remotePath` | 서버의 업로드 대상 경로 (기본: `/opt/deploy/`)                     |
| `directUpload` | `true`이면 개별 파일 직접 업로드                                  |
| `compressFormat` | 압축 방식: `tar.gz`(기본) 또는 `zip` (version:1.4.1)           |
| `skipUpload` | `true`이면 원격 서버 업로드를 건너뛰고 압축 파일만 로컬에 생성 (version:1.4.2) |
| `uploadFilePath` | 변경 감지를 건너뛰고 업로드할 파일 직접 지정                              |

---

## 사용 예시

### Git 커밋 범위 배포

```bash
mvn deploy:upload-changes \
  -Dcommits=abc123..def456 \
  -DremoteHost=a.server.com \
  -DremoteUser=username \
  -DprivateKeyPath=~/.ssh/id_rsa \
  -DremotePath=/opt/tomcat/webapps/myapp/
```

### 개별 파일 직접 업로드 (롤백 지원)

```bash
mvn deploy:upload-changes \
  -Dcommits=abc123..def456 \
  -DremoteHost=a.server.com \
  -DremoteUser=username \
  -DprivateKeyPath=~/.ssh/id_rsa \
  -DremotePath=/opt/tomcat/webapps/myapp/ \
  -DdirectUpload=true
```

### 롤백

```bash
# 최신 배포 자동 롤백
mvn deploy:rollback-changes \
  -DremoteHost=a.server.com \
  -DremoteUser=username \
  -DprivateKeyPath=~/.ssh/id_rsa

# 특정 시점으로 롤백
mvn deploy:rollback-changes \
  -DmanifestFile=deploy/deploy-manifest-20260506-104400.txt \
  -DremoteHost=a.server.com \
  -DremoteUser=username \
  -DprivateKeyPath=~/.ssh/id_rsa
```

---

## 파일 필터링

- **처리 대상**: `.java` `.xml` `.jsp` `.jspf` `.js` `.css` `.properties` `.html` `.png` `.gif` `.jpg`
- **처리 제외**: `pom.xml`, `context-crypto-test.xml`, `globals.properties` 등

### 소스 → 빌드 결과물 경로 변환

| 소스 경로 | 서버 업로드 경로 |
|---|---|
| `src/main/java/.../MyService.java` | `WEB-INF/classes/.../MyService.class` |
| `src/main/resources/config.properties` | `WEB-INF/classes/config.properties` |
| `src/main/webapp/index.jsp` | `index.jsp` |
