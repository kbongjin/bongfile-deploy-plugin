# bongfile-deploy-plugin 사용 가이드

Git 또는 SVN의 커밋 이력을 기반으로 변경된 파일을 감지하고, SFTP로 배포 서버에 업로드하는 Maven 플러그인입니다.

---

## 목차

1. [설치](#1-설치)
2. [배포 방식 개요](#2-배포-방식-개요)
3. [파라미터 전체 목록](#3-파라미터-전체-목록)
4. [배포 방식 1 — tar.gz 압축 업로드 (기본)](#4-배포-방식-1--targz-압축-업로드-기본)
5. [배포 방식 2 — 개별 파일 직접 업로드](#5-배포-방식-2--개별-파일-직접-업로드)
6. [롤백](#6-롤백)
7. [인증 방식](#7-인증-방식)
8. [파일 필터링 규칙](#8-파일-필터링-규칙)

---

## 1. 설치

### 로컬 설치 (테스트용)

```bash
mvn clean install
```

### 내부 Nexus 배포

```bash
mvn deploy
```

### 대상 프로젝트 pom.xml 설정

```xml
<build>
  <plugins>
    <plugin>
      <groupId>com.bongsoft</groupId>
      <artifactId>bongfile-deploy-plugin</artifactId>
      <version>1.3-SNAPSHOT</version>
    </plugin>
  </plugins>
</build>
```

---

## 2. 배포 방식 개요

| 방식 | 파라미터 | 설명 |
|---|---|---|
| tar.gz 압축 업로드 | `directUpload` 생략 또는 `false` | 변경 파일을 tar.gz으로 묶어 서버에 업로드 |
| 개별 파일 직접 업로드 | `directUpload=true` | 파일별로 백업 후 직접 업로드, 롤백 가능 |
| 파일 직접 지정 | `uploadFilePath=경로` | 변경 감지 없이 지정한 파일을 업로드 |

---

## 3. 파라미터 전체 목록

### 변경 파일 감지

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `commits` | — | Git 커밋 범위. 단일 커밋 또는 `..` 범위, 쉼표로 여러 개 지정 가능 |
| `svnRevisions` | — | SVN 리비전 범위 (예: `100:105`) |
| `appRootPath` | `target/egovframework-all-in-one` | 빌드 결과물 루트 경로 |
| `uploadFilePath` | — | 변경 감지를 건너뛰고 업로드할 파일 직접 지정 |

> `commits` 또는 `svnRevisions` 중 하나는 반드시 지정해야 합니다. (`uploadFilePath` 사용 시 제외)

### SFTP 접속

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `remoteHost` | — | 배포 서버 호스트명 또는 IP |
| `remotePort` | `22` | SSH 포트 |
| `remoteUser` | — | 접속 계정 |
| `remotePassword` | — | 비밀번호 (privateKeyPath와 택일) |
| `privateKeyPath` | — | SSH 개인키 경로 (remotePassword와 택일) |
| `passphrase` | — | 개인키 암호 (키에 암호가 설정된 경우) |
| `remotePath` | `/opt/deploy/` | 서버의 업로드 대상 경로 |

### 업로드 방식

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `directUpload` | `false` | `true`이면 개별 파일 직접 업로드 (백업 및 롤백 지원) |

### 롤백

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `manifestFile` | — | 롤백할 매니페스트 파일 경로. 생략 시 `deploy/` 에서 최신 파일 자동 선택 |

---

## 4. 배포 방식 1 — tar.gz 압축 업로드 (기본)

변경된 파일을 `deploy-{타임스탬프}.tar.gz` 으로 압축하여 서버에 업로드합니다.

### Git 커밋 범위 지정

```bash
# 두 커밋 사이의 변경 파일
mvn deploy:upload-changes \
  -Dcommits=abc123..def456 \
  -DremoteHost=a.server.com \
  -DremoteUser=username \
  -DprivateKeyPath=~/.ssh/id_rsa \
  -DremotePath=/opt/tomcat/webapps/myapp/
```

```bash
# 단일 커밋의 변경 파일
mvn deploy:upload-changes \
  -Dcommits=abc123 \
  -DremoteHost=a.server.com \
  -DremoteUser=username \
  -DprivateKeyPath=~/.ssh/id_rsa \
  -DremotePath=/opt/tomcat/webapps/myapp/
```

```bash
# 여러 커밋 조합 (쉼표 구분)
mvn deploy:upload-changes \
  -Dcommits=abc123..def456,ghi789 \
  -DremoteHost=a.server.com \
  -DremoteUser=username \
  -DprivateKeyPath=~/.ssh/id_rsa \
  -DremotePath=/opt/tomcat/webapps/myapp/
```

### SVN 리비전 범위 지정

```bash
mvn deploy:upload-changes \
  -DsvnRevisions=100:105 \
  -DremoteHost=a.server.com \
  -DremoteUser=username \
  -DprivateKeyPath=~/.ssh/id_rsa \
  -DremotePath=/opt/tomcat/webapps/myapp/
```

### 파일 직접 지정

변경 감지 없이 tar.gz 파일을 직접 지정하여 업로드합니다.

```bash
mvn deploy:upload-changes \
  -DuploadFilePath=/path/to/deploy-20260506.tar.gz \
  -DremoteHost=a.server.com \
  -DremoteUser=username \
  -DprivateKeyPath=~/.ssh/id_rsa \
  -DremotePath=/opt/tomcat/webapps/myapp/
```

---

## 5. 배포 방식 2 — 개별 파일 직접 업로드

`-DdirectUpload=true` 를 추가하면 tar.gz 압축 없이 파일을 하나씩 서버에 직접 업로드합니다.

- 업로드 전 서버의 기존 파일을 `.bak` 으로 백업
- 동일한 `.bak` 이 이미 있으면 `.bak1`, `.bak2`, `.bak3` ... 순서로 생성
- 배포 이력을 `deploy/deploy-manifest-{타임스탬프}.txt` 에 기록 (롤백에 사용)

```bash
mvn deploy:upload-changes \
  -Dcommits=abc123..def456 \
  -DremoteHost=a.server.com \
  -DremoteUser=username \
  -DprivateKeyPath=~/.ssh/id_rsa \
  -DremotePath=/opt/tomcat/webapps/myapp/ \
  -DdirectUpload=true
```

### 배포 후 서버 파일 상태 예시

```
MyService.class      ← 새로 배포된 파일
MyService.class.bak  ← 배포 전 원본 (자동 백업)
```

### 배포 매니페스트

배포가 완료되면 프로젝트의 `deploy/` 디렉토리에 매니페스트 파일이 생성됩니다.

```
deploy/
  deploy-manifest-20260506-104400.txt
  deploy-manifest-20260506-152300.txt
```

매니페스트 내용 예시:
```
# bongfile-deploy manifest - 20260506-104400
# remote=username@a.server.com:22/opt/tomcat/webapps/myapp/
/opt/tomcat/webapps/myapp/WEB-INF/classes/com/example/MyService.class|...MyService.class.bak
/opt/tomcat/webapps/myapp/WEB-INF/classes/com/example/MyDao.class|
```

> `deploy/` 디렉토리는 `mvn clean` 으로 삭제되지 않습니다.

---

## 6. 롤백

`directUpload=true` 로 배포한 경우에만 롤백이 가능합니다.

### 최신 배포 자동 롤백

`deploy/` 디렉토리에서 가장 최신 매니페스트를 자동으로 선택합니다.

```bash
mvn deploy:rollback-changes \
  -DremoteHost=a.server.com \
  -DremoteUser=username \
  -DprivateKeyPath=~/.ssh/id_rsa
```

### 특정 시점으로 롤백

```bash
mvn deploy:rollback-changes \
  -DmanifestFile=deploy/deploy-manifest-20260506-104400.txt \
  -DremoteHost=a.server.com \
  -DremoteUser=username \
  -DprivateKeyPath=~/.ssh/id_rsa
```

### 롤백 동작 규칙

| 상황 | 동작 |
|---|---|
| 배포 전 파일이 존재했음 (`.bak` 있음) | 현재 파일 삭제 → `.bak` 을 원본으로 복원 |
| 배포 전 파일이 없었음 (신규 파일) | 현재 파일 삭제 |
| `.bak` 파일이 서버에서 없어진 경우 | 해당 파일 건너뜀 + 경고 로그 출력 |

### 롤백 후 매니페스트 상태

롤백이 완료되면 해당 매니페스트 파일명에 `-ROLLED-BACK` 이 추가됩니다.

```
deploy/
  deploy-manifest-20260506-104400-ROLLED-BACK.txt  ← 롤백 완료
  deploy-manifest-20260506-152300.txt              ← 미롤백
```

롤백된 매니페스트는 자동 탐색에서 제외되므로, 최신 롤백 명령은 항상 미롤백 배포 중 가장 최신 것을 선택합니다.

---

## 7. 인증 방식

### SSH 개인키 (권장)

```bash
-DprivateKeyPath=~/.ssh/id_rsa
```

개인키에 암호가 설정된 경우:

```bash
-DprivateKeyPath=~/.ssh/id_rsa -Dpassphrase=키암호
```

### 비밀번호

```bash
-DremotePassword=서버비밀번호
```

---

## 8. 파일 필터링 규칙

### 처리 대상 확장자

`.java` `.xml` `.jsp` `.jspf` `.js` `.css` `.properties` `.html` `.png` `.gif` `.jpg`

### 처리 제외 파일

`pom.xml` `context-crypto-test.xml` `globals.properties` `README.md` `LICENSE` `.gitignore`

### 소스 → 빌드 결과물 경로 변환

| 소스 경로 | 서버 업로드 경로 |
|---|---|
| `src/main/java/com/example/MyService.java` | `WEB-INF/classes/com/example/MyService.class` |
| `src/main/resources/config.properties` | `WEB-INF/classes/config.properties` |
| `src/main/webapp/index.jsp` | `index.jsp` |

> `$InnerClass.class` 같은 내부 클래스 파일도 자동으로 포함됩니다.
