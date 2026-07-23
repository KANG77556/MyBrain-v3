from pathlib import Path

path = Path('allfilehub-apk/app/src/main/java/com/seongho/allfilehub/MainActivity.java')
source = path.read_text(encoding='utf-8')

old = '''    /** 파일을 분석하고 가능한 내부 실행기를 자동 선택합니다. */
    private void processFile(Uri uri) {
        closeActiveViewers();
        currentUri = uri;
'''
new = '''    /** 파일을 분석하고 가능한 내부 실행기를 자동 선택합니다. */
    private void processFile(Uri uri) {
        try {
            processFileInternal(uri);
        } catch (OutOfMemoryError error) {
            showOpenError("파일이 너무 커서 메모리가 부족합니다. 더 작은 파일로 다시 시도하세요.");
        } catch (Throwable error) {
            showOpenError(safeMessage(error));
        }
    }

    /** 실제 파일 분류와 뷰어 실행을 담당합니다. */
    private void processFileInternal(Uri uri) {
        if (uri == null) throw new IllegalArgumentException("파일 주소가 없습니다.");
        closeActiveViewers();
        showScrollPreview();
        currentUri = uri;
'''
if old not in source:
    raise SystemExit('processFile 패턴을 찾지 못했습니다.')
source = source.replace(old, new, 1)

source = source.replace('''        showScrollPreview();
        previewArea.removeAllViews();

        if (isImage''', '''        previewArea.removeAllViews();

        if (isImage''', 1)

old = '''        apkPreviewFile = null;
        showScrollPreview();
    }
'''
new = '''        apkPreviewFile = null;
    }
'''
if old not in source:
    raise SystemExit('closeActiveViewers 패턴을 찾지 못했습니다.')
source = source.replace(old, new, 1)

old = '''    private void showScrollPreview() {
        previewFrame.removeAllViews();
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(previewArea, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        previewFrame.addView(scrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }
'''
new = '''    private void showScrollPreview() {
        if (previewFrame == null || previewArea == null || isFinishing()) return;

        // 새 부모에 붙이기 전에 기존 부모에서 반드시 분리합니다.
        android.view.ViewParent parent = previewArea.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(previewArea);
        }

        previewFrame.removeAllViews();
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(previewArea, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        previewFrame.addView(scrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }
'''
if old not in source:
    raise SystemExit('showScrollPreview 패턴을 찾지 못했습니다.')
source = source.replace(old, new, 1)

old = '''    private void showDirectPreview() {
        previewFrame.removeAllViews();
    }
'''
new = '''    private void showDirectPreview() {
        if (previewFrame == null || isFinishing()) return;
        android.view.ViewParent parent = previewArea == null ? null : previewArea.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(previewArea);
        }
        previewFrame.removeAllViews();
    }
'''
if old not in source:
    raise SystemExit('showDirectPreview 패턴을 찾지 못했습니다.')
source = source.replace(old, new, 1)

marker = '''    /** 사용자가 명시적으로 요청한 경우에만 외부 호환 앱으로 전달합니다. */
    private void openExternally(Uri uri, String mime) {
'''
addition = '''    /** 파일 열기 오류를 앱 종료 대신 화면에 표시합니다. */
    private void showOpenError(String message) {
        try {
            showScrollPreview();
            if (previewArea != null) {
                previewArea.removeAllViews();
                addInfoCard("파일 열기 오류",
                        TextUtils.isEmpty(message) ? "파일을 내부에서 열지 못했습니다." : message);
                if (currentUri != null) {
                    addPreviewButton("외부 앱으로 열기", () -> openExternally(currentUri, currentMime));
                }
            }
            if (statusView != null) statusView.append("\\n오류가 발생했지만 앱은 안전하게 유지되었습니다.");
        } catch (Throwable ignored) {
            Toast.makeText(this, "파일을 열지 못했습니다.", Toast.LENGTH_LONG).show();
        }
    }

''' + marker
if marker not in source:
    raise SystemExit('openExternally 패턴을 찾지 못했습니다.')
source = source.replace(marker, addition, 1)
source = source.replace('문서·PDF·미디어·ZIP·APK 정보를 앱 안에서 직접 실행',
                        '문서·PDF·미디어·ZIP·APK 내부 실행 · 안정화 1.5.1', 1)

path.write_text(source, encoding='utf-8')
print('강제 종료 수정 완료:', len(source))
