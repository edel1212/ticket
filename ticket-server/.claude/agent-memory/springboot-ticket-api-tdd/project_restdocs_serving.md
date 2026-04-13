---
name: REST Docs bootRun 서빙 전략
description: bootRun 실행 시 /docs/index.html 접근을 위한 build.gradle 구성 방식 — git 오염 없이 처리
type: project
---

`src/main/resources/static/docs/`에 직접 복사하면 git 오염 문제가 생긴다.

**해결 방식:** `copyDocsForRun` Copy 태스크로 `build/resources/main/static/docs/`에만 복사하고, `bootRun`이 이 태스크에 의존하게 한다.

```groovy
tasks.named('asciidoctor') {
    inputs.dir snippetsDir
    dependsOn test
    attributes 'snippets': snippetsDir  // adoc에서 {snippets} 변수 사용 가능
}

tasks.register('copyDocsForRun', Copy) {
    dependsOn asciidoctor
    from("${asciidoctor.outputDir}")
    into "${buildDir}/resources/main/static/docs"
}

tasks.named('bootRun') {
    dependsOn copyDocsForRun
}

bootJar {
    dependsOn asciidoctor
    from("${asciidoctor.outputDir}") {
        into 'static/docs'
    }
}
```

**주의:** `processResources`에 `dependsOn asciidoctor`를 걸면 `processResources -> asciidoctor -> test -> classes -> processResources` 순환 의존성이 발생한다.

**Why:** bootRun은 JAR를 만들지 않고 classpath에서 직접 실행하므로 bootJar의 `from()` 복사가 적용되지 않는다.

**How to apply:** bootRun 기반 로컬 개발 환경에서 REST Docs HTML을 서빙할 때 항상 이 패턴을 사용한다. adoc 인덱스 파일은 `src/docs/asciidoc/index.adoc`에 위치해야 asciidoctor가 NO-SOURCE로 끝나지 않는다.
