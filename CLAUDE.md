# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 절대 규칙

1. **커밋 전 테스트 필수** — 어떤 변경이든 커밋 전에 반드시 테스트를 실행하여 전체 테스트를 통과시킨다.
2. **TDD 방식으로 개발** — 구현 코드 작성 전에 반드시 테스트를 먼저 작성한다 (Red → Green → Refactor 사이클).

## Language

이 저장소에서는 **항상 한국어로 생각하고 답변**한다.

## 프로젝트 구조

| 디렉토리 | 설명 | 기술 스택 |
|----------|------|-----------|
| `ticket-server/` | REST API 서버 | Spring Boot 3.5.11, Java 17 |
| `ticket-client/` | 클라이언트 웹 화면 (미생성) | Next.js, Tailwind CSS |

각 모듈의 상세 내용은 해당 디렉토리의 `CLAUDE.md`를 참고한다.
