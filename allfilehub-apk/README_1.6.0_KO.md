# 모든파일 1.6.0 원본 보기 강화

## 핵심 변경

- PDF 고해상도 페이지 렌더링 및 두 손가락 확대·이동
- JPEG, PNG, GIF, WEBP, HEIF 등 ImageDecoder 기반 원본 색상·방향·애니메이션 표시
- SVG 벡터 원본 렌더링
- HTML 원본 레이아웃 렌더링(스크립트·네트워크 차단)
- DOCX·XLSX·PPTX의 docProps 미리보기 이미지 우선 표시
- HWPX의 Preview/PrvImage 원본 미리보기 우선 표시
- ODT·ODS·ODP의 Thumbnails/thumbnail.png 우선 표시
- EPUB 표지와 포함 사진 표시
- 문서 패키지에 들어 있는 원본 사진 최대 16개 표시
- ZIP 경로 이탈 차단과 용량 제한 유지

## 기술적 한계

DOCX, XLSX, PPTX, HWPX의 글꼴·도형·페이지 배치를 전용 편집기와 100% 동일하게 렌더링하려면 LibreOfficeKit·Collabora 계열의 대형 네이티브 오피스 엔진 또는 상용 SDK가 필요합니다. 이번 버전은 인터넷 업로드 없이 문서 안에 저장된 원본 미리보기와 원본 사진을 우선 표시합니다.
