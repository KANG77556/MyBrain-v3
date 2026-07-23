# 모든파일 1.5.1 강제 종료 수정

원인: previewArea가 기존 ScrollView의 자식인 상태에서 새 ScrollView에 다시 추가되어 IllegalStateException이 발생했습니다.

수정:
- 새 부모에 추가하기 전 기존 부모에서 View 분리
- closeActiveViewers에서 화면 재구성 제거
- 파일 처리 최상위 예외 보호 추가
- 오류 발생 시 앱 종료 대신 오류 카드 표시
