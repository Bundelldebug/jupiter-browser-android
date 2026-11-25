# 주피터 (Jupiter Browser Android) — 9.5.0

Chromium WebView 기반 모바일 특화 브라우저입니다. Apple Safari 스타일의 하단 플로팅 리퀴드 글래스 UI와 기능을 구현했습니다.

## 주요 기능
- 하단 플로팅 리퀴드 글래스 주소/검색 바 (스크롤 시 자동 축소)
- Safari 스타일 시작 페이지 (즐겨찾기, 개인정보 보호 리포트, 읽기 목록, 북마크)
- 카드 그리드 멀티 탭 전환기 + 개인정보 보호 브라우징 모드
- `AA` 페이지 도구: 읽기 도구(Reader Mode), 데스크탑 웹사이트 요청, 페이지에서 찾기, 웹 다크 모드, 텍스트 확대/축소, 콘텐츠(추적기) 차단기
- 기본 브라우저(`http`/`https`) 지원 및 GitHub Releases 앱 내 업데이트

## 빌드
```powershell
powershell -ExecutionPolicy Bypass -File build.ps1
```
