package com.oldenweb.Archery;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdRequest.Builder;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;
import com.google.android.gms.games.leaderboard.Leaderboards;
import com.google.example.games.basegameutils.BaseGameActivity;

import java.io.IOException;
import java.util.Locale;

public class Main extends BaseGameActivity {
    Handler h = new Handler();
    SharedPreferences sp;
    Editor ed;
    boolean isForeground = true;
    boolean excellent;
    MediaPlayer mp;
    SoundPool sndpool;
    int snd_fly;
    int snd_result;
    int snd_info;
    int snd_board;
    int score;
    int screen_width;
    int screen_height;
    int current_section = R.id.main;
    boolean show_leaderboard;
    float speed_x;
    float speed_y;
    int bow_power;
    Bitmap bitmap_way;
    Paint p;
    Canvas canvas;
    RectF board_area0;
    RectF board_area1;
    RectF board_area2;
    RectF board_area3;
    RectF board_area4;
    RectF board_area5;
    RectF board_area6;
    RectF board_area7;
    RectF board_area8;
    int num_arrows;
    final PointF prev_point = new PointF(0, 0);
    final float gravity = 0.2f; // arrow gravity
    final float rotation = 0.7f; // arrow rotation when fly
    final float speed = 20; // arrow speed

    // AdMob
    AdView adMobBanner;
    InterstitialAd adMobInterstitial;
    AdRequest adRequest;

    TextView tv_location;   //位置坐标

    /* Called when the activity is first created */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // don't automatically sign in
        mHelper.setMaxAutoSignInAttempts(0);

        // fullscreen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // preferences
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        ed = sp.edit();

        // AdMob
        adMob();

        // bg sound
        mp = new MediaPlayer();
        try {
            AssetFileDescriptor descriptor = getAssets().openFd("snd_bg.mp3");
            mp.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
            descriptor.close();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setLooping(true);
            mp.setVolume(0, 0);
            mp.prepare();
            mp.start();
        } catch (Exception e) {
        }

        // if mute
        if (sp.getBoolean("mute", false)) {
            ((Button) findViewById(R.id.btn_sound)).setText(getString(R.string.btn_sound));
        } else {
            mp.setVolume(0.2f, 0.2f);
        }

        // SoundPool
        sndpool = new SoundPool(3, AudioManager.STREAM_MUSIC, 0);
        try {
            snd_fly = sndpool.load(getAssets().openFd("snd_fly.mp3"), 1);
            snd_result = sndpool.load(getAssets().openFd("snd_result.mp3"), 1);
            snd_info = sndpool.load(getAssets().openFd("snd_info.mp3"), 1);
            snd_board = sndpool.load(getAssets().openFd("snd_board.mp3"), 1);
        } catch (IOException e) {
        }

        // hide navigation bar listener
        findViewById(R.id.all).setOnSystemUiVisibilityChangeListener(new OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                hide_navigation_bar();
            }
        });

        // paint style for points way
        p = new Paint();
        p.setColor(Color.WHITE);

        // disable bow
        findViewById(R.id.bow).setEnabled(false);

        // custom font
        Typeface font = Typeface.createFromAsset(getAssets(), "CooperBlack.otf");
        ((TextView) findViewById(R.id.txt_result)).setTypeface(font);
        ((TextView) findViewById(R.id.txt_high_result)).setTypeface(font);
        ((TextView) findViewById(R.id.txt_score)).setTypeface(font);
        ((TextView) findViewById(R.id.mess)).setTypeface(font);

        // touch listener
        findViewById(R.id.game).setOnTouchListener(new OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (findViewById(R.id.bow).isEnabled()) {

                    ((TextView) findViewById(R.id.tv_location)).setText("X="+String.valueOf(event.getX())+"  Y="+String.valueOf(event.getY()));
//                    Toast.makeText(Main.this,"X="+String.valueOf(event.getX())+"  Y="+String.valueOf(event.getY()),Toast.LENGTH_SHORT).show();
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                        case MotionEvent.ACTION_MOVE:
                            // distance
                            float distance = (float) Math.sqrt(Math.pow(findViewById(R.id.bow).getX()
                                    + findViewById(R.id.bow).getWidth() * 0.5f - event.getX(), 2)
                                    + Math.pow(
                                    findViewById(R.id.bow).getY() + findViewById(R.id.bow).getHeight() * 0.5f - event.getY(),
                                    2));

                            // angle
                            float cos = (findViewById(R.id.bow).getX() + findViewById(R.id.bow).getWidth() * 0.5f - event.getX())
                                    / distance;
                            float sin = (findViewById(R.id.bow).getY() + findViewById(R.id.bow).getHeight() * 0.5f - event.getY())
                                    / distance;
                            if (cos < -1)
                                cos = -1;
                            if (cos > 1)
                                cos = 1;

                            // bow rotation
                            if (findViewById(R.id.bow).getY() + findViewById(R.id.bow).getHeight() * 0.5f - event.getY() > 0)
                                findViewById(R.id.bow).setRotation((float) (Math.acos(cos) * 180f / Math.PI));
                            else
                                findViewById(R.id.bow).setRotation((float) (-Math.acos(cos) * 180f / Math.PI));

                            // bow power
                            bow_power = (int) Math.min(22, Math.round(distance * 22 / (findViewById(R.id.bow).getWidth() * 0.5f)));
                            show_bow();

                            // arrow fly speed
                            speed_x = cos * DpToPx(speed);
                            speed_y = sin * DpToPx(speed);
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            // fire
                            if (bow_power != 0) {
                                num_arrows--;
                                prev_point.set(0, 0);
                                ((TextView) findViewById(R.id.txt_arrows)).setText(getString(R.string.arrows) + " " + num_arrows);
                                findViewById(R.id.bow).setEnabled(false);

                                // canvas for way points
                                bitmap_way = Bitmap.createBitmap(screen_width, screen_height, Bitmap.Config.ARGB_8888);
                                canvas = new Canvas(bitmap_way);

                                // bow animation
                                ((ImageView) findViewById(R.id.bow)).setImageResource(R.drawable.bow0);

                                // sound
                                if (!sp.getBoolean("mute", false) && isForeground)
                                    sndpool.play(snd_fly, 0.5f, 0.5f, 0, 0, 1);

                                h.post(MOVE);
                            }
                            break;
                    }
                }
                return false;
            }
        });

        SCALE();
    }

    // SCALE
    void SCALE() {
        // score
        findViewById(R.id.score).getLayoutParams().width = (int) DpToPx(30);
        findViewById(R.id.score).getLayoutParams().height = (int) DpToPx(20);

        // cloud
        findViewById(R.id.cloud).getLayoutParams().width = (int) DpToPx(300);
        findViewById(R.id.cloud).getLayoutParams().height = (int) DpToPx(139);

        // txt_score
        ((TextView) findViewById(R.id.txt_score)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(24));
        FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) findViewById(R.id.txt_score).getLayoutParams();
        l.setMargins((int) DpToPx(4), 0, 0, 0);
        findViewById(R.id.txt_score).setLayoutParams(l);

        // txt_location
        ((TextView) findViewById(R.id.tv_location)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(16));

        // txt_arrows
        ((TextView) findViewById(R.id.txt_arrows)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(20));
        l = (FrameLayout.LayoutParams) findViewById(R.id.txt_arrows).getLayoutParams();
        l.setMargins((int) DpToPx(5), (int) DpToPx(22), 0, 0);
        findViewById(R.id.txt_arrows).setLayoutParams(l);

        // buttons text
        ((TextView) findViewById(R.id.btn_sign)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(22));
        ((TextView) findViewById(R.id.btn_leaderboard)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(22));
        ((TextView) findViewById(R.id.btn_sound)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(22));
        ((TextView) findViewById(R.id.btn_start)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(22));
        ((TextView) findViewById(R.id.btn_exit)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(22));
        ((TextView) findViewById(R.id.btn_home)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(22));
        ((TextView) findViewById(R.id.btn_start2)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(22));

        // text result
        ((TextView) findViewById(R.id.txt_result)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(64));
        ((TextView) findViewById(R.id.txt_high_result)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(34));

        // text mess
        ((TextView) findViewById(R.id.mess)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(30));

        // arrow
        findViewById(R.id.arrow).getLayoutParams().width = (int) DpToPx(56);
        findViewById(R.id.arrow).getLayoutParams().height = (int) DpToPx(6);

        // board
        findViewById(R.id.board).getLayoutParams().width = (int) DpToPx(30);
        findViewById(R.id.board).getLayoutParams().height = (int) DpToPx(80);

        // bow
        findViewById(R.id.bow).getLayoutParams().width = (int) DpToPx(48);
        findViewById(R.id.bow).getLayoutParams().height = (int) DpToPx(84);
    }

    // START
    void START() {
        show_section(R.id.game);
        score = 0;
        bow_power = 0;
        excellent = true;
        num_arrows = 20; // number of arrows
        findViewById(R.id.bow).setRotation(0);
        findViewById(R.id.bow).setEnabled(false);
        ((TextView) findViewById(R.id.txt_arrows)).setText(getString(R.string.arrows) + " " + num_arrows);
        ((TextView) findViewById(R.id.txt_score)).setText(getString(R.string.score) + " " + score);
        findViewById(R.id.score).setAlpha(0);
        findViewById(R.id.mess).setVisibility(View.GONE);

        // screen size
       /* screen_width = Math.max(findViewById(R.id.all).getWidth(), findViewById(R.id.all).getHeight());
        screen_height = Math.min(findViewById(R.id.all).getWidth(), findViewById(R.id.all).getHeight());*/
//       修改  横竖屏切换 by gkm 2018-04-05
        screen_height = Math.max(findViewById(R.id.all).getWidth(), findViewById(R.id.all).getHeight());
        screen_width = Math.min(findViewById(R.id.all).getWidth(), findViewById(R.id.all).getHeight());

        // ground scale
        findViewById(R.id.ground).getLayoutParams().width = screen_width;
        findViewById(R.id.ground).getLayoutParams().height = (int) (screen_width / 1.33);

        // board start position
        findViewById(R.id.board).setX(screen_width);
        findViewById(R.id.board).setY((screen_height - findViewById(R.id.board).getHeight()) * 0.5f);

        // bow start position
        findViewById(R.id.bow).setX(DpToPx(30));
        findViewById(R.id.bow).setY(screen_height - findViewById(R.id.bow).getHeight() - DpToPx(15));

        // clear fly points
        bitmap_way = Bitmap.createBitmap(screen_width, screen_height, Bitmap.Config.ARGB_8888);
        ((ImageView) findViewById(R.id.way)).setImageBitmap(bitmap_way);

        // cloud
        findViewById(R.id.cloud).setX((float) (Math.random() * screen_width));
        h.post(CLOUD);

        show_bow();
        random_board();
    }

    // show_bow
    void show_bow() {
        // bow drawable
        ((ImageView) findViewById(R.id.bow)).setImageResource(getResources().getIdentifier("bow" + bow_power, "drawable",
                getPackageName()));

        // arrow position
        findViewById(R.id.arrow).setX(
                (findViewById(R.id.bow).getX() + findViewById(R.id.bow).getWidth() * 0.5f - DpToPx(3 + bow_power)));
        findViewById(R.id.arrow)
                .setY((findViewById(R.id.bow).getY() + findViewById(R.id.bow).getHeight() * 0.5f - findViewById(R.id.arrow)
                        .getHeight() * 0.5f));

        // arrow rotation
        findViewById(R.id.arrow).setPivotX(DpToPx(3 + bow_power));
        findViewById(R.id.arrow).setPivotY(findViewById(R.id.arrow).getHeight() * 0.5f);
        findViewById(R.id.arrow).setRotation(findViewById(R.id.bow).getRotation());

        ((ImageView) findViewById(R.id.arrow)).setImageResource(R.drawable.arrow_normal);
        findViewById(R.id.arrow).setAlpha(1);
    }

    // random_board
    void random_board() {
        AnimatorSet anim = new AnimatorSet();
        anim.playTogether(
                ObjectAnimator.ofFloat(findViewById(R.id.board), "x", (int) (screen_width * 0.5f + Math.random()
                        * (screen_width * 0.5f - findViewById(R.id.board).getWidth() - DpToPx(10)))),
                ObjectAnimator.ofFloat(findViewById(R.id.board), "y", (int) (DpToPx(10) + Math.random()
                        * (screen_height - findViewById(R.id.board).getHeight() - DpToPx(20)))));
        anim.setDuration(500);
        anim.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                findViewById(R.id.bow).setEnabled(true);

                // get board hit rectangles
                board_area0 = new RectF(findViewById(R.id.board).getX() + DpToPx(12), findViewById(R.id.board).getY()
                        + DpToPx(4f), findViewById(R.id.board).getX() + DpToPx(15), findViewById(R.id.board).getY() + DpToPx(10));
                board_area1 = new RectF(findViewById(R.id.board).getX() + DpToPx(12), findViewById(R.id.board).getY()
                        + DpToPx(10), findViewById(R.id.board).getX() + DpToPx(15), findViewById(R.id.board).getY() + DpToPx(17));
                board_area2 = new RectF(findViewById(R.id.board).getX() + DpToPx(12), findViewById(R.id.board).getY()
                        + DpToPx(17), findViewById(R.id.board).getX() + DpToPx(15), findViewById(R.id.board).getY() + DpToPx(25));
                board_area3 = new RectF(findViewById(R.id.board).getX() + DpToPx(12), findViewById(R.id.board).getY()
                        + DpToPx(25), findViewById(R.id.board).getX() + DpToPx(15), findViewById(R.id.board).getY() + DpToPx(33));
                board_area4 = new RectF(findViewById(R.id.board).getX() + DpToPx(12), findViewById(R.id.board).getY()
                        + DpToPx(33), findViewById(R.id.board).getX() + DpToPx(15), findViewById(R.id.board).getY() + DpToPx(47));
                board_area5 = new RectF(findViewById(R.id.board).getX() + DpToPx(12), findViewById(R.id.board).getY()
                        + DpToPx(47), findViewById(R.id.board).getX() + DpToPx(15), findViewById(R.id.board).getY() + DpToPx(55));
                board_area6 = new RectF(findViewById(R.id.board).getX() + DpToPx(12), findViewById(R.id.board).getY()
                        + DpToPx(55), findViewById(R.id.board).getX() + DpToPx(15), findViewById(R.id.board).getY() + DpToPx(63));
                board_area7 = new RectF(findViewById(R.id.board).getX() + DpToPx(12), findViewById(R.id.board).getY()
                        + DpToPx(63), findViewById(R.id.board).getX() + DpToPx(15), findViewById(R.id.board).getY() + DpToPx(70));
                board_area8 = new RectF(findViewById(R.id.board).getX() + DpToPx(12), findViewById(R.id.board).getY()
                        + DpToPx(70), findViewById(R.id.board).getX() + DpToPx(15), findViewById(R.id.board).getY() + DpToPx(76));
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }

            @Override
            public void onAnimationStart(Animator animation) {
            }
        });
        anim.start();
    }

    // check_hit
    int check_hit(int i) {
        // arrow position
        findViewById(R.id.arrow).setX((float) (findViewById(R.id.arrow).getX() + (speed_x / i * bow_power / 22f)));
        findViewById(R.id.arrow).setY((float) (findViewById(R.id.arrow).getY() + (speed_y / i * bow_power / 22f)));

        // arrow rotation
        findViewById(R.id.arrow).setPivotX(Math.max(0, findViewById(R.id.arrow).getPivotX() - DpToPx(1 / i)));
        if (findViewById(R.id.bow).getRotation() > -90)
            findViewById(R.id.arrow).setRotation((float) (findViewById(R.id.arrow).getRotation() + rotation / i));
        else if (findViewById(R.id.bow).getRotation() < -90)
            findViewById(R.id.arrow).setRotation((float) (findViewById(R.id.arrow).getRotation() - rotation / i));

        // gravity
        speed_y += DpToPx(gravity / i);

        // arrow fly point
        float[] src = {findViewById(R.id.arrow).getWidth() - DpToPx(1), findViewById(R.id.arrow).getHeight() * 0.5f};
        float[] dst = new float[2];
        findViewById(R.id.arrow).getMatrix().mapPoints(dst, src);

        // draw fly point
        if (Math.abs(Math.abs(prev_point.x) - Math.abs(dst[0])) > DpToPx(30)
                || Math.abs(Math.abs(prev_point.y) - Math.abs(dst[1])) > DpToPx(30)) {
            canvas.drawCircle(dst[0], dst[1], DpToPx(1), p);
            ((ImageView) findViewById(R.id.way)).setImageBitmap(bitmap_way);
            prev_point.set(dst[0], dst[1]);
        }

        // check hit arrow with board
        RectF arrow_rect = new RectF(dst[0], dst[1], dst[0], dst[1]);
        if (arrow_rect.intersect(board_area0) || arrow_rect.intersect(board_area8)) {
            return 1;
        } else if (arrow_rect.intersect(board_area1) || arrow_rect.intersect(board_area7)) {
            return 2;
        } else if (arrow_rect.intersect(board_area2) || arrow_rect.intersect(board_area6)) {
            return 3;
        } else if (arrow_rect.intersect(board_area3) || arrow_rect.intersect(board_area5)) {
            return 4;
        } else if (arrow_rect.intersect(board_area4)) {
            return 5;
        }

        return 0;
    }

    // MOVE
    Runnable MOVE = new Runnable() {
        @Override
        public void run() {
            int hit_board = 0;
            for (int i = 0; i < 10; i++) {
                hit_board = check_hit(10);
                if (hit_board != 0) {
                    break;
                }
            }

            // hit exist
            if (hit_board != 0) {
                // disable arrow
                ((ImageView) findViewById(R.id.arrow)).setImageResource(R.drawable.arrow_disabled);

                // sound
                if (!sp.getBoolean("mute", false) && isForeground)
                    sndpool.play(snd_board, 0.5f, 0.5f, 0, 0, 1);

                // show score
                score += (hit_board * 10);
                ((TextView) findViewById(R.id.txt_score)).setText(getString(R.string.score) + " " + score);
                ((ImageView) findViewById(R.id.score)).setImageResource(getResources().getIdentifier("score" + hit_board,
                        "drawable", getPackageName()));
                findViewById(R.id.score).setAlpha(1);
                findViewById(R.id.score).setX(findViewById(R.id.board).getX() - findViewById(R.id.score).getWidth() * 2);
                findViewById(R.id.score).setY(
                        findViewById(R.id.board).getY() + findViewById(R.id.board).getHeight() * 0.5f
                                - findViewById(R.id.score).getHeight() * 0.5f);

                // not excellent
                if (hit_board != 5)
                    excellent = false;

                // score animation
                AnimatorSet anim_score = new AnimatorSet();
                anim_score.playTogether(
                        ObjectAnimator.ofFloat(findViewById(R.id.score), "y",
                                findViewById(R.id.score).getY() - findViewById(R.id.score).getHeight()),
                        ObjectAnimator.ofFloat(findViewById(R.id.score), "alpha", 0));
                anim_score.setDuration(1000);
                anim_score.start();

                // arrow hide
                ObjectAnimator anim_arrow = ObjectAnimator.ofFloat(findViewById(R.id.arrow), "alpha", 0);
                anim_arrow.setDuration(1000);
                anim_arrow.addListener(new AnimatorListener() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (num_arrows == 0) {
                            if (excellent) {
                                // bonus arrows
                                num_arrows = 5;
                                ((TextView) findViewById(R.id.txt_arrows)).setText(getString(R.string.arrows) + " " + num_arrows);

                                // move board
                                bow_power = 0;
                                show_bow();
                                random_board();
                            } else
                                game_over();
                        } else {
                            // move board
                            bow_power = 0;
                            show_bow();
                            random_board();
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {
                    }

                    @Override
                    public void onAnimationStart(Animator animation) {
                    }
                });
                anim_arrow.start();
                return;
            }

            // arrow off screen
            if (findViewById(R.id.arrow).getX() < -findViewById(R.id.arrow).getWidth()
                    || findViewById(R.id.arrow).getY() < -findViewById(R.id.arrow).getWidth()
                    || findViewById(R.id.arrow).getX() > screen_width || findViewById(R.id.arrow).getY() > screen_height) {
                if (num_arrows == 0) {
                    game_over();
                } else {
                    // enable bow
                    findViewById(R.id.bow).setEnabled(true);
                    bow_power = 0;
                    show_bow();
                }
                return;
            }

            h.postDelayed(MOVE, 10);
        }
    };

    // CLOUD
    Runnable CLOUD = new Runnable() {
        @Override
        public void run() {
            // move cloud
            findViewById(R.id.cloud).setX(findViewById(R.id.cloud).getX() - DpToPx(0.2f));

            // cloud off screen
            if (findViewById(R.id.cloud).getX() < -findViewById(R.id.cloud).getWidth()) {
                ((ImageView) findViewById(R.id.cloud)).setImageResource(getResources().getIdentifier(
                        "cloud" + Math.round(Math.random() * 2), "drawable", getPackageName()));
                findViewById(R.id.cloud).setX(screen_width);
            }

            h.postDelayed(CLOUD, 10);
        }
    };

    // game_over
    void game_over() {
        // show message
        findViewById(R.id.mess).setVisibility(View.VISIBLE);

        // sound
        if (!sp.getBoolean("mute", false) && isForeground)
            sndpool.play(snd_info, 1f, 1f, 0, 0, 1);

        h.postDelayed(STOP, 3000);
    }

    // STOP
    Runnable STOP = new Runnable() {
        @Override
        public void run() {
            // show result
            show_section(R.id.result);
            h.removeCallbacks(CLOUD);

            // save score
            if (score > sp.getInt("score", 0)) {
                ed.putInt("score", score);
                ed.commit();
            }

            // show score
            ((TextView) findViewById(R.id.txt_result)).setText(getString(R.string.score) + " " + score);
            ((TextView) findViewById(R.id.txt_high_result)).setText(getString(R.string.high_score) + " " + sp.getInt("score", 0));

            // save score to leaderboard
            if (getApiClient().isConnected()) {
                Games.Leaderboards.submitScore(getApiClient(), getString(R.string.leaderboard), sp.getInt("score", 0));
            }

            // sound
            if (!sp.getBoolean("mute", false) && isForeground)
                sndpool.play(snd_result, 0.8f, 0.8f, 0, 0, 1);

            // AdMob Interstitial
            if (adMobInterstitial != null)
                if (adMobInterstitial.isLoaded())
                    adMobInterstitial.show(); // show
                else if (!adMobInterstitial.isLoading() && ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo() != null)
                    adMobInterstitial.loadAd(adRequest); // load
        }
    };

    // onClick
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_start:
            case R.id.btn_start2:
                START();
                break;
            case R.id.btn_home:
                show_section(R.id.main);
                break;
            case R.id.btn_exit:
                finish();
                break;
            case R.id.btn_sound:
                if (sp.getBoolean("mute", false)) {
                    ed.putBoolean("mute", false);
                    mp.setVolume(0.2f, 0.2f);
                    ((Button) findViewById(R.id.btn_sound)).setText(getString(R.string.btn_mute));
                } else {
                    ed.putBoolean("mute", true);
                    mp.setVolume(0, 0);
                    ((Button) findViewById(R.id.btn_sound)).setText(getString(R.string.btn_sound));
                }
                ed.commit();
                break;
            case R.id.btn_leaderboard:
                // show leaderboard
                show_leaderboard = true;
                if (getApiClient().isConnected())
                    onSignInSucceeded();
                else
                    beginUserInitiatedSignIn();
                break;
            case R.id.btn_sign:
                // Google sign in/out
                if (getApiClient().isConnected()) {
                    signOut();
                    onSignInFailed();
                } else
                    beginUserInitiatedSignIn();
                break;
        }
    }

    @Override
    public void onBackPressed() {
        switch (current_section) {
            case R.id.main:
                super.onBackPressed();
                break;
            case R.id.result:
                show_section(R.id.main);
                break;
            case R.id.game:
                show_section(R.id.main);
                h.removeCallbacks(MOVE);
                h.removeCallbacks(STOP);
                h.removeCallbacks(CLOUD);
                break;
        }
    }

    // show_section
    void show_section(int section) {
        current_section = section;
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.game).setVisibility(View.GONE);
        findViewById(R.id.result).setVisibility(View.GONE);
        findViewById(current_section).setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacks(MOVE);
        h.removeCallbacks(STOP);
        h.removeCallbacks(CLOUD);
        mp.release();
        sndpool.release();

        // destroy AdMob
        if (adMobBanner != null) {
            adMobBanner.setAdListener(null);
            adMobBanner.destroyDrawingCache();
            adMobBanner.destroy();
            adMobBanner = null;
        }
        if (adMobInterstitial != null) {
            adMobInterstitial.setAdListener(null);
            adMobInterstitial = null;
        }
			adRequest = null;

        super.onDestroy();
    }

    @Override
    protected void onPause() {
        isForeground = false;
        mp.setVolume(0, 0);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isForeground = true;

        if (!sp.getBoolean("mute", false) && isForeground)
            mp.setVolume(0.2f, 0.2f);
    }

    // DpToPx
    float DpToPx(float dp) {
        return (dp * Math.max(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels) / 540f);
    }

    // hide_navigation_bar
    @TargetApi(Build.VERSION_CODES.KITKAT)
    void hide_navigation_bar() {
        // fullscreen mode
        if (android.os.Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hide_navigation_bar();
        }
    }

    @Override
    public void onSignInSucceeded() {
        ((Button) findViewById(R.id.btn_sign)).setText(getString(R.string.btn_sign_out));

        // save score to leaderboard
        if (show_leaderboard) {
            Games.Leaderboards.submitScore(getApiClient(), getString(R.string.leaderboard), sp.getInt("score", 0));

            // show leaderboard
            startActivityForResult(Games.Leaderboards.getLeaderboardIntent(getApiClient(), getString(R.string.leaderboard)), 9999);
        }

        // get score from leaderboard
        Games.Leaderboards.loadCurrentPlayerLeaderboardScore(getApiClient(), getString(R.string.leaderboard),
                LeaderboardVariant.TIME_SPAN_ALL_TIME, LeaderboardVariant.COLLECTION_PUBLIC).setResultCallback(
                new ResultCallback<Leaderboards.LoadPlayerScoreResult>() {
                    @Override
                    public void onResult(final Leaderboards.LoadPlayerScoreResult scoreResult) {
                        if (scoreResult != null && scoreResult.getStatus().getStatusCode() == GamesStatusCodes.STATUS_OK
                                && scoreResult.getScore() != null) {
                            // save score localy
                            if ((int) scoreResult.getScore().getRawScore() > sp.getInt("score", 0)) {
                                ed.putInt("score", (int) scoreResult.getScore().getRawScore());
                                ed.commit();
                            }
                        }
                    }
                });

        show_leaderboard = false;
    }

    @Override
    public void onSignInFailed() {
        ((Button) findViewById(R.id.btn_sign)).setText(getString(R.string.btn_sign_in));
        show_leaderboard = false;
    }

    // adMob
    void adMob() {
        if (getResources().getBoolean(R.bool.show_admob)) {
            // make AdMob request
            Builder builder = new AdRequest.Builder();
            if (getResources().getBoolean(R.bool.admob_test))
                builder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR).addTestDevice(
                        MD5(Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID)));
            adRequest = builder.build();

            // AdMob Interstitial
            adMobInterstitial = new InterstitialAd(Main.this);
            adMobInterstitial.setAdUnitId(getString(R.string.adMob_interstitial));
            adMobInterstitial.setAdListener(new AdListener() {
                public void onAdClosed() {
                    if (((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo() != null)
                        adMobInterstitial.loadAd(adRequest);
                }
            });

            if (((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo() != null) {
                // AdMob Banner
				adMobBanner = new AdView(Main.this);
                adMobBanner.setAdUnitId(getString(R.string.adMob_banner));
                adMobBanner.setAdSize(AdSize.SMART_BANNER);
                ((ViewGroup) findViewById(R.id.admob)).addView(adMobBanner);
                
				// load
				adMobBanner.loadAd(adRequest);
				adMobInterstitial.loadAd(adRequest);
            }
        }
    }

    // MD5
    String MD5(String str) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(str.getBytes());
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < array.length; ++i)
                sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1, 3));
            return sb.toString().toUpperCase(Locale.ENGLISH);
        } catch (java.security.NoSuchAlgorithmException e) {
        }
        return null;
    }
}