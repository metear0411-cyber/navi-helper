package kr.navihelper;

import android.service.wallpaper.WallpaperService;

/**
 * 실제로 쓰는 배경화면이 아니다. FYT(com.syu.ms)는 시동을 끄고 잠들 때(休眠)
 * 보호 목록·홈 런처·키보드·배경화면 서비스를 가진 앱을 뺀 모든 앱을 강제 종료하고,
 * 안드로이드는 강제 종료된 앱의 접근성 서비스를 설정에서 지운다. 그러면 시동을
 * 다시 켜도 자동 주행이 죽은 채로 남는다. 이 서비스를 갖고 있으면 FYT가 이 앱을
 * '배경화면 앱'으로 보고 죽이지 않는다. meta-data가 없어서 배경화면 고르기 목록엔
 * 나오지 않는다.
 */
public class KeepAliveWallpaper extends WallpaperService {
    @Override public Engine onCreateEngine() { return new Engine(); }
}
