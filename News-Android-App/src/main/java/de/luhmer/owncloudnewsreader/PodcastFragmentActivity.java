package de.luhmer.owncloudnewsreader;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.session.MediaControllerCompat;
import android.util.Log;
import android.util.TypedValue;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.nextcloud.android.sso.helper.VersionCheckHelper;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import de.luhmer.owncloudnewsreader.ListView.SubscriptionExpandableListAdapter;
import de.luhmer.owncloudnewsreader.database.DatabaseConnectionOrm;
import de.luhmer.owncloudnewsreader.database.model.RssItem;
import de.luhmer.owncloudnewsreader.di.ApiProvider;
import de.luhmer.owncloudnewsreader.events.podcast.CollapsePodcastView;
import de.luhmer.owncloudnewsreader.events.podcast.ExpandPodcastView;
import de.luhmer.owncloudnewsreader.events.podcast.PodcastCompletedEvent;
import de.luhmer.owncloudnewsreader.helper.PostDelayHandler;
import de.luhmer.owncloudnewsreader.helper.ThemeChooser;
import de.luhmer.owncloudnewsreader.interfaces.IPlayPausePodcastClicked;
import de.luhmer.owncloudnewsreader.model.MediaItem;
import de.luhmer.owncloudnewsreader.model.PodcastItem;
import de.luhmer.owncloudnewsreader.notification.NextcloudNotificationManager;
import de.luhmer.owncloudnewsreader.services.PodcastDownloadService;
import de.luhmer.owncloudnewsreader.services.PodcastPlaybackService;
import de.luhmer.owncloudnewsreader.ssl.MemorizingTrustManager;
import de.luhmer.owncloudnewsreader.view.PodcastSlidingUpPanelLayout;
import de.luhmer.owncloudnewsreader.widget.WidgetProvider;

import static de.luhmer.owncloudnewsreader.Constants.MIN_NEXTCLOUD_FILES_APP_VERSION_CODE;

public class PodcastFragmentActivity extends AppCompatActivity implements IPlayPausePodcastClicked {

    private static final String TAG = PodcastFragmentActivity.class.getCanonicalName();

    @Inject protected SharedPreferences mPrefs;
    @Inject protected ApiProvider mApi;
    @Inject protected MemorizingTrustManager mMTM;
    @Inject protected PostDelayHandler mPostDelayHandler;

    private EventBus eventBus;
    private PodcastFragment mPodcastFragment;

    @BindView(R.id.sliding_layout)
    protected PodcastSlidingUpPanelLayout sliding_layout;



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        //Log.v(TAG, "onCreate() called with: savedInstanceState = [" + savedInstanceState + "]");
        ((NewsReaderApplication) getApplication()).getAppComponent().injectActivity(this);

        ThemeChooser.chooseTheme(this);
        super.onCreate(savedInstanceState);
        ThemeChooser.afterOnCreate(this);

        if (mApi.getAPI() instanceof Proxy) { // Single Sign On
            VersionCheckHelper.verifyMinVersion(this, MIN_NEXTCLOUD_FILES_APP_VERSION_CODE);
        }

        mPostDelayHandler.stopRunningPostDelayHandler();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        //Log.v(TAG, "onPostCreate() called with: savedInstanceState = [" + savedInstanceState + "]");
        super.onPostCreate(savedInstanceState);

        eventBus = EventBus.getDefault();

        ButterKnife.bind(this);

        updatePodcastView();
    }

    @Override
    protected void onStart() {
        super.onStart();

        mMTM.bindDisplayActivity(this);
    }

    @Override
    protected void onStop() {
        mMTM.unbindDisplayActivity(this);

        super.onStop();
    }


    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        mPostDelayHandler.delayOnExitTimer();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (hasWindowFocus) {
            int currentOrientation = getResources().getConfiguration().orientation;
            if (currentOrientation != lastOrientation) {
                sliding_layout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
                lastOrientation = currentOrientation;
            }
        }

        super.onWindowFocusChanged(hasWindowFocus);
    }


    int lastOrientation = -1;

    @Override
    protected void onResume() {
        eventBus.register(this);

        super.onResume();
    }

    @Override
    protected void onPause() {
        eventBus.unregister(this);

        /*
        isVideoViewVisible = false;
        videoViewInitialized = false;

        eventBus.post(new RegisterVideoOutput(null, null));

        rlVideoPodcastSurfaceWrapper.setVisibility(View.GONE);
        rlVideoPodcastSurfaceWrapper.removeAllViews();
        */

        WidgetProvider.UpdateWidget(this);

        if (NextcloudNotificationManager.isUnreadRssCountNotificationVisible(this)) {
            DatabaseConnectionOrm dbConn = new DatabaseConnectionOrm(this);
            int count = Integer.parseInt(dbConn.getUnreadItemsCountForSpecificFolder(SubscriptionExpandableListAdapter.SPECIAL_FOLDERS.ALL_UNREAD_ITEMS));
            NextcloudNotificationManager.showUnreadRssItemsNotification(this, count);

            if (count == 0) {
                NextcloudNotificationManager.removeRssItemsNotification(this);
            }
        }


        super.onPause();
    }


    /*
    public static boolean isMyServiceRunning(Class<?> serviceClass, Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    */



    /*
    private void buildTransportControls() {
        // Grab the view for the play/pause button

        int pbState = MediaControllerCompat.getMediaController(PodcastFragmentActivity.this).getPlaybackState().getState();
        if (pbState == PlaybackStateCompat.STATE_PLAYING) {
            MediaControllerCompat.getMediaController(PodcastFragmentActivity.this).getTransportControls().pause();
        } else {
            MediaControllerCompat.getMediaController(PodcastFragmentActivity.this).getTransportControls().play();
        }
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(PodcastFragmentActivity.this);

        // Display the initial state
        MediaMetadataCompat metadata = mediaController.getMetadata();
        PlaybackStateCompat pbState = mediaController.getPlaybackState();

        // Register a Callback to stay in sync
        mediaController.registerCallback(controllerCallback);
    }
    */

    public PodcastSlidingUpPanelLayout getSlidingLayout() {
        return sliding_layout;
    }

    public boolean handlePodcastBackPressed() {
        if(mPodcastFragment != null && sliding_layout.getPanelState().equals(SlidingUpPanelLayout.PanelState.EXPANDED)) {
            sliding_layout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
            return true;
        }
        return false;
    }

    protected void updatePodcastView() {
        if(mPodcastFragment == null) {
            mPodcastFragment = PodcastFragment.newInstance();
        }
        /*
        if(mPodcastFragment != null) {
            getSupportFragmentManager().beginTransaction().remove(mPodcastFragment).commitAllowingStateLoss();
        }
        */

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.podcast_frame, mPodcastFragment)
                .commitAllowingStateLoss();

        collapsePodcastView();
    }


    @Subscribe
    public void onEvent(CollapsePodcastView event) {
        Log.v(TAG, "onEvent(CollapsePodcastView) called with: event = [" + event + "]");
        collapsePodcastView();
    }

    @Subscribe
    public void onEvent(ExpandPodcastView event) {
        Log.v(TAG, "onEvent(ExpandPodcastView) called with: event = [" + event + "]");
        expandPodcastView();
    }


    private void collapsePodcastView() {
        sliding_layout.setPanelHeight(0);
    }

    private void expandPodcastView() {
        sliding_layout.setPanelHeight((int) dipToPx(68));
    }

    @Subscribe
    public void onEvent(PodcastCompletedEvent podcastCompletedEvent) {
        collapsePodcastView();
        sliding_layout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
        //currentlyPlaying = false;
    }

    public static int pxToDp(int px) {
        return (int) (px / Resources.getSystem().getDisplayMetrics().density);
    }

    private float dipToPx(float dip) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, getResources().getDisplayMetrics());
    }

    @VisibleForTesting
    public void openMediaItem(final MediaItem mediaItem) {
        Intent intent = new Intent(this, PodcastPlaybackService.class);
        intent.putExtra(PodcastPlaybackService.MEDIA_ITEM, mediaItem);
        startService(intent);

        /*
        if(!mMediaBrowser.isConnected()) {
            mMediaBrowser.connect();
        }
        */


        //bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

    }

    @Override
    public void openPodcast(final RssItem rssItem) {
        final PodcastItem podcastItem = DatabaseConnectionOrm.ParsePodcastItemFromRssItem(this, rssItem);

        File file = new File(PodcastDownloadService.getUrlToPodcastFile(this, podcastItem.link, false));
        if(file.exists()) {
            podcastItem.link = file.getAbsolutePath();
            openMediaItem(podcastItem);
        } else if(!podcastItem.offlineCached) {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(this)
                    .setNegativeButton("Abort", null)
                    .setTitle("Podcast");

            if("youtube".equals(podcastItem.mimeType)) {
                alertDialog.setPositiveButton("Open Youtube", (dialogInterface, i) -> openYoutube(podcastItem));
            } else {
                alertDialog.setNeutralButton("Download", (dialogInterface, i) -> {
                    PodcastDownloadService.startPodcastDownload(PodcastFragmentActivity.this, podcastItem);
                    Toast.makeText(PodcastFragmentActivity.this, "Starting download of podcast. Please wait..", Toast.LENGTH_SHORT).show();
                });
                alertDialog.setPositiveButton("Stream", (dialogInterface, i) -> openMediaItem(podcastItem));
                alertDialog.setMessage("Choose if you want to download or stream the selected podcast");
            }

            alertDialog.show();
        }
    }


    @Override
    public void pausePodcast() {
        MediaControllerCompat.getMediaController(PodcastFragmentActivity.this).getTransportControls().pause();
    }


    private void openYoutube(PodcastItem podcastItem) {
        Log.e(TAG, podcastItem.link);
        String youtubeVideoID = getVideoIdFromYoutubeUrl(podcastItem.link);
        if(youtubeVideoID == null) {
            Toast.makeText(this, "Failed to extract youtube video id for url: " + podcastItem.link + ". Please report this issue.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent appIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + youtubeVideoID));
        Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.youtube.com/watch?v=" + podcastItem.link));
        try {
            startActivity(appIntent);
        } catch (ActivityNotFoundException ex) {
            startActivity(webIntent);
        }
    }

    public String getVideoIdFromYoutubeUrl(String url){
        String videoId = null;
        String regex = "http(?:s)?:\\/\\/(?:m.)?(?:www\\.)?youtu(?:\\.be\\/|be\\.com\\/(?:watch\\?(?:feature=youtu.be\\&)?v=|v\\/|embed\\/|user\\/(?:[\\w#]+\\/)+))([^&#?\\n]+)";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(url);
        if(matcher.find()){
            videoId = matcher.group(1);
        }
        return videoId;
    }
}
