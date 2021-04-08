/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.gldemo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoListener;
import java.util.UUID;

/**
 * Activity that demonstrates playback of video to an {@link android.opengl.GLSurfaceView} with
 * postprocessing of the video content using GL.
 */
public final class MainActivity extends Activity {

  private static final String TAG = "MainActivity";

  private static final String DEFAULT_MEDIA_URI =
      "https://cdn.display.io/ctvbins/asset/video/360_640_30.mp4";

  private static final String HORIZONTAL_MEDIA_URI =
      "https://cdn.display.io/ctvbins/asset/video/640_360.mp4";

//      "https://storage.googleapis.com/exoplayer-test-media-1/mkv/android-screens-lavf-56.36.100-aac-avc-main-1280x720.mkv";


  private final String BASE_VIDEO_FILE = "base_video_layer.mp4";
  private final String OVERLAY_VIDEO_FILE = "overlay_video_layer.mp4";
  private final String OVERLAY_VIDEO_FILE_ALFA = "overlay_video_layer_alfa.mov";


  private static final String ACTION_VIEW = "com.google.android.exoplayer.gldemo.action.VIEW";
  private static final String EXTENSION_EXTRA = "extension";
  private static final String DRM_SCHEME_EXTRA = "drm_scheme";
  private static final String DRM_LICENSE_URL_EXTRA = "drm_license_url";

  @Nullable private PlayerView playerView;
  @Nullable private VideoProcessingGLSurfaceView videoProcessingGLSurfaceView;

  @Nullable private SimpleExoPlayer player;
  private SimpleExoPlayer overlayExoPlayer;
  private boolean isOverlayReady = false;
  private FrameLayout adContainer;
  @Nullable private PlayerView adVideoView;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main_activity);
    playerView = findViewById(R.id.player_view);

    Context context = getApplicationContext();
    boolean requestSecureSurface = getIntent().hasExtra(DRM_SCHEME_EXTRA);
    if (requestSecureSurface && !GlUtil.isProtectedContentExtensionSupported(context)) {
      Toast.makeText(
              context, R.string.error_protected_content_extension_not_supported, Toast.LENGTH_LONG)
          .show();
    }

    VideoProcessingGLSurfaceView videoProcessingGLSurfaceView =
        new VideoProcessingGLSurfaceView(
            context, requestSecureSurface, new BitmapOverlayVideoProcessor(context));
    FrameLayout contentFrame = findViewById(R.id.exo_content_frame);
    contentFrame.addView(videoProcessingGLSurfaceView);
    this.videoProcessingGLSurfaceView = videoProcessingGLSurfaceView;

    addAd();
  }

  private void addAd() {
    Uri uri = Uri.parse(getVideoPath(OVERLAY_VIDEO_FILE));
      DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this,
          Util.getUserAgent(this, "CustomApplication"));
      MediaSource videoSource =
          new ProgressiveMediaSource.Factory(dataSourceFactory)
              .createMediaSource(MediaItem.fromUri(uri));
      LoopingMediaSource loopingMediaSource = new LoopingMediaSource(videoSource, 300);

      overlayExoPlayer = new SimpleExoPlayer.Builder(this).build();


      adContainer = (FrameLayout) LayoutInflater.from(this).inflate(R.layout.ad_layout, null);
      adContainer.setVisibility(View.INVISIBLE);
      PlayerView adVideoView = adContainer.findViewById(R.id.ad_view);
      adVideoView.setAlpha(0.6f);
      adVideoView.setPlayer(overlayExoPlayer);
      adVideoView.setUseController(false);


      overlayExoPlayer.addListener(new Player.EventListener() {

        @Override
        public void onPlaybackStateChanged(int state) {
          switch (state) {
            case Player.STATE_READY:
//              overlayExoPlayer.setPlayWhenReady(true);
              isOverlayReady = true;
              break;
            case Player.STATE_BUFFERING:
              break;
            case Player.STATE_IDLE:
              overlayExoPlayer.retry();
              break;
          }
        }
      });
      overlayExoPlayer.setMediaSource(loopingMediaSource);
      overlayExoPlayer.prepare();



    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
//    lp.gravity = Gravity.END | Gravity.BOTTOM;
    adVideoView.setLayoutParams(lp);

    FrameLayout overlayFrame = playerView.findViewById(R.id.exo_overlay);
    overlayFrame.addView(adContainer);
    adVideoView.setTranslationY(100);
    adVideoView.setTranslationX(100);
  }

  @Override
  public void onStart() {
    super.onStart();
    if (Util.SDK_INT > 23) {
      initializePlayer();
      if (playerView != null) {
        playerView.onResume();
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (Util.SDK_INT <= 23 || player == null) {
      initializePlayer();
      if (playerView != null) {
        playerView.onResume();
      }
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (Util.SDK_INT <= 23) {
      if (playerView != null) {
        playerView.onPause();
      }
      releasePlayer();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    if (Util.SDK_INT > 23) {
      if (playerView != null) {
        playerView.onPause();
      }
      releasePlayer();
    }
    if (adVideoView != null) {
      adVideoView.onPause();
      adVideoView.setPlayer(null);
      adVideoView.getPlayer().release();
    }
  }

  private void initializePlayer() {
    Intent intent = getIntent();
    String action = intent.getAction();
    Uri uri =
        ACTION_VIEW.equals(action)
            ? Assertions.checkNotNull(intent.getData())
            : Uri.parse(getVideoPath(BASE_VIDEO_FILE));
    DrmSessionManager drmSessionManager;
    if (Util.SDK_INT >= 18 && intent.hasExtra(DRM_SCHEME_EXTRA)) {
      String drmScheme = Assertions.checkNotNull(intent.getStringExtra(DRM_SCHEME_EXTRA));
      String drmLicenseUrl = Assertions.checkNotNull(intent.getStringExtra(DRM_LICENSE_URL_EXTRA));
      UUID drmSchemeUuid = Assertions.checkNotNull(Util.getDrmUuid(drmScheme));
      HttpDataSource.Factory licenseDataSourceFactory = new DefaultHttpDataSourceFactory();
      HttpMediaDrmCallback drmCallback =
          new HttpMediaDrmCallback(drmLicenseUrl, licenseDataSourceFactory);
      drmSessionManager =
          new DefaultDrmSessionManager.Builder()
              .setUuidAndExoMediaDrmProvider(drmSchemeUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
              .build(drmCallback);
    } else {
      drmSessionManager = DrmSessionManager.DRM_UNSUPPORTED;
    }

    DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this);
    MediaSource mediaSource;
    @C.ContentType int type = Util.inferContentType(uri, intent.getStringExtra(EXTENSION_EXTRA));
    if (type == C.TYPE_DASH) {
      mediaSource =
          new DashMediaSource.Factory(dataSourceFactory)
              .setDrmSessionManager(drmSessionManager)
              .createMediaSource(MediaItem.fromUri(uri));
    } else if (type == C.TYPE_OTHER) {
      mediaSource =
          new ProgressiveMediaSource.Factory(dataSourceFactory)
              .setDrmSessionManager(drmSessionManager)
              .createMediaSource(MediaItem.fromUri(uri));
    } else {
      throw new IllegalStateException();
    }

    SimpleExoPlayer player = new SimpleExoPlayer.Builder(getApplicationContext()).build();
    player.addListener(new Player.EventListener() {
      @Override
      public void onPlaybackStateChanged(int state) {
//        if (state == Player.STATE_READY) {
//          adVideoView.setVisibility(View.VISIBLE);
//        } else {
//          adVideoView.setVisibility(View.INVISIBLE);
//        }
      }
    });
    player.setRepeatMode(Player.REPEAT_MODE_ALL);
    player.setMediaSource(mediaSource);
    player.prepare();
    player.play();
    VideoProcessingGLSurfaceView videoProcessingGLSurfaceView =
        Assertions.checkNotNull(this.videoProcessingGLSurfaceView);
    videoProcessingGLSurfaceView.setVideoComponent(
        Assertions.checkNotNull(player.getVideoComponent()));
    Assertions.checkNotNull(playerView).setPlayer(player);
    player.addAnalyticsListener(new EventLogger(/* trackSelector= */ null));
    this.player = player;
    trackPosition(player);
  }

  private void releasePlayer() {
    Assertions.checkNotNull(playerView).setPlayer(null);
    if (player != null) {
      player.release();
      Assertions.checkNotNull(videoProcessingGLSurfaceView).setVideoComponent(null);
      player = null;
    }
  }

  private String getVideoPath(String fileName) {
    return "file:/android_asset/" + fileName;
//    return "android.resource://" + this.getPackageName() + "/raw/" + fileName;
  }

  private void trackPosition(SimpleExoPlayer player) {
    final Handler handler = new Handler();
    Runnable trackOnce = new Runnable() {
      @Override
      public void run() {
        long currentPosition = player.getCurrentPosition();

        if (currentPosition < 60000) {
          handler.postDelayed(this, 5);
        } else {
          adContainer.setVisibility(View.VISIBLE);
          overlayExoPlayer.setPlayWhenReady(true);
        }
      }
    };

    handler.postDelayed(trackOnce, 5);
  }
}
