package me.cl.lingxi.module.main;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;

import me.cl.library.base.BaseFragment;
import me.cl.library.photo.PhotoBrowser;
import me.cl.library.util.ToolbarUtil;
import me.cl.lingxi.R;
import me.cl.lingxi.common.glide.GlideApp;
import me.cl.lingxi.common.okhttp.OkUtil;
import me.cl.lingxi.common.okhttp.ResultCallback;
import me.cl.lingxi.common.util.GsonUtil;
import me.cl.lingxi.common.util.NetworkUtil;
import me.cl.lingxi.common.util.SPUtil;
import me.cl.lingxi.databinding.HomeFragmentBinding;
import me.cl.lingxi.module.search.SearchActivity;
import okhttp3.Call;

public class HomeFragment extends BaseFragment {

    private HomeFragmentBinding mBinding;

    private static final String TYPE = "type";

    private String mType;
    private String mImageUrl;
    private boolean openTuPics;

    public HomeFragment() {

    }

    public static HomeFragment newInstance(String newsType) {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        args.putString(TYPE, newsType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mType = getArguments().getString(TYPE);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        openTuPics = SPUtil.build().getBoolean("open_tu_pics");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mBinding = HomeFragmentBinding.inflate(inflater, container, false);
        init();
        return mBinding.getRoot();
    }

    private void init() {
        ToolbarUtil.init(mBinding.includeTb.toolbar, getActivity())
                .setTitle(R.string.title_bar_home)
                .setTitleCenter()
                .setMenu(R.menu.search_menu, new Toolbar.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case R.id.action_search:
                                gotoSearch();
                                break;
                        }
                        return false;
                    }
                })
                .build();

        initView();
        initData();
    }

    private void initView() {
         mBinding.swipeRefreshLayout.setOnRefreshListener(() -> {
             if (NetworkUtil.isWifiConnected( mBinding.swipeRefreshLayout.getContext())) {
                 if (openTuPics) {
                     getTuPicsData();
                 } else {
                     getDefaultData();
                 }
             } else {
                 setError();
             }
         });
        mBinding.wordInfo.setOnLongClickListener(v -> {
            ClipboardManager clipboardManager = (ClipboardManager) mBinding.wordInfo.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clipData = ClipData.newPlainText(null, mBinding.wordInfo.getText().toString().trim());
            if (clipboardManager != null) {
                clipboardManager.setPrimaryClip(clipData);
                showToast("已复制");
            }
            return false;
        });
        mBinding.randomImage.setOnClickListener(v -> {
            ArrayList<String> strings = new ArrayList<>();
            strings.add(mImageUrl);
            PhotoBrowser.builder()
                    .setPhotos(strings)
                    .start(requireActivity());
        });
    }

    /**
     * 初始化数据
     */
    private void initData() {
         mBinding.swipeRefreshLayout.setRefreshing(true);
        openTuPics = SPUtil.build().getBoolean("open_tu_pics");
        if (NetworkUtil.isWifiConnected(requireContext())) {
            if (openTuPics) {
                getTuPicsData();
            } else {
                getDefaultData();
            }
        } else {
            loadCache();
        }
    }

    private void getTuPicsData() {
        // Tujian
        // old API https://api.dpic.dev/ | new API https://v2.api.dailypics.cn/
        // 图片访问 s1.images.dailypics.cn | s2.images.dailypics.cn
        OkUtil.get()
                .url("https://v2.api.dailypics.cn/random?op=mobile")
                .execute(new ResultCallback<ArrayList<RandomPicture>>() {
                    @Override
                    public void onSuccess(ArrayList<RandomPicture> response) {
                        if (response != null && !response.isEmpty()) {
                            setTuPicsDate(response.get(0));
                        } else {
                            setError();
                        }
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                        setError();
                    }
                });
    }

    private void loadCache() {
        if (openTuPics) {
            String json = SPUtil.build().getString(RandomPicture.class.getName());
            if (!TextUtils.isEmpty(json)) {
                setTuPicsDate(GsonUtil.toObject(json, RandomPicture.class));
            } else {
                getTuPicsData();
            }
        } else {
            String json = SPUtil.build().getString(HiToKoTo.class.getName());
            if (!TextUtils.isEmpty(json)) {
                setDefaultData(GsonUtil.toObject(json, HiToKoTo.class));
            } else {
                getDefaultData();
            }
        }
    }

    private void setTuPicsDate(RandomPicture picture) {
        setRefreshFalse();
        // 文
        mBinding.wordInfo.setVisibility(View.INVISIBLE);
        mBinding.wordAuthor.setVisibility(View.INVISIBLE);
        mBinding.wordSource.setVisibility(View.INVISIBLE);
        String text = picture.getP_content();
        if (!TextUtils.isEmpty(text)) {
            mBinding.wordInfo.setVisibility(View.VISIBLE);
            mBinding.wordInfo.setText(text);
        }
        String author = picture.getP_title();
        if (!TextUtils.isEmpty(author)) {
            mBinding.wordAuthor.setVisibility(View.VISIBLE);
            mBinding.wordAuthor.setText(author);
        }
        SPUtil.build().putString(RandomPicture.class.getName(), GsonUtil.toJson(picture));
        // 图
        mBinding.imageSource.setVisibility(View.VISIBLE);
        String pLink = picture.getNativePath();
        if (TextUtils.isEmpty(pLink)) {
            mBinding.randomImage.setEnabled(false);
        } else {
            mBinding.randomImage.setEnabled(true);
            pLink = "https://s1.images.dailypics.cn" + pLink;
            // 2560 * 1440
            int width = picture.getWidth();
            if (width > 1440) {
                mImageUrl = pLink + "!w1080";
            } else {
                mImageUrl = pLink;
            }
            GlideApp.with(this)
                    .load(mImageUrl)
//                    .centerCrop
                    .into(mBinding.randomImage);
        }
    }

    private void getDefaultData() {
        // 一言
        OkUtil.get()
                .url("https://v1.hitokoto.cn/")
                .setLoadDelay()
                .execute(new ResultCallback<HiToKoTo>() {
                    @Override
                    public void onSuccess(HiToKoTo response) {
                        setDefaultData(response);
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                        setError();
                    }
                });

        // 一图
        OkUtil.get()
                .url("http://img.xjh.me/random_img.php?return=json")
                .execute(new ResultCallback<RandomImage>() {
                    @Override
                    public void onSuccess(RandomImage response) {
                        loadImage(response);
                    }

                    @Override
                    public void onError(Call call, Exception e) {

                    }
                });
    }

    private void setDefaultData(HiToKoTo hitokoto) {
        setRefreshFalse();
        mBinding.wordInfo.setVisibility(View.INVISIBLE);
        mBinding.wordAuthor.setVisibility(View.INVISIBLE);
        mBinding.wordSource.setVisibility(View.INVISIBLE);
        mBinding.imageSource.setVisibility(View.GONE);
        if (hitokoto != null) {
            String text = hitokoto.getHitokoto();
            if (!TextUtils.isEmpty(text)) {
                mBinding.wordInfo.setVisibility(View.VISIBLE);
                mBinding.wordInfo.setText(text);
            }
            String author = hitokoto.getCreator();
            if (!TextUtils.isEmpty(author)) {
                mBinding.wordAuthor.setVisibility(View.VISIBLE);
                mBinding.wordAuthor.setText(author);
            }
            String source = hitokoto.getFrom();
            if (!TextUtils.isEmpty(source)) {
                mBinding.wordSource.setVisibility(View.VISIBLE);
                mBinding.wordSource.setText(source);
            }
            SPUtil.build().putString(HiToKoTo.class.getName(), GsonUtil.toJson(hitokoto));
        }
    }

    private void loadImage(RandomImage randomImage) {
        if (randomImage != null) {
            String img = randomImage.getImg();
            if (img.startsWith("//")) {
                img = "http:" + img;
            }
            if (TextUtils.isEmpty(img)) {
                mBinding.randomImage.setEnabled(false);
            } else {
                mBinding.randomImage.setEnabled(true);
                mImageUrl = img;
                GlideApp.with(this)
                        .load(img)
                        .centerInside()
                        .into(mBinding.randomImage);
            }
        }
    }

    /**
     * 设置异常提示
     */
    private void setError() {
        setRefreshFalse();
        showToast("在未知的边缘试探╮(╯▽╰)╭");
    }

    /**
     * 结束刷新
     */
    private void setRefreshFalse() {
        boolean refreshing =  mBinding.swipeRefreshLayout.isRefreshing();
        if (refreshing) {
             mBinding.swipeRefreshLayout.setRefreshing(false);
        }
    }

    /**
     * 前往搜索
     */
    private void gotoSearch() {
        Intent intent = new Intent(getActivity(), SearchActivity.class);
        startActivity(intent);
    }

    /**
     * 一言
     */
    static class HiToKoTo {

        /**
         * id : 5963
         * uuid : b25ebe3d-d031-493c-a129-61ba3b7c0a23
         * type : b
         * hitokoto : 此时此刻的咱啊，能和汝在一起，是最幸福的了。
         * creator : 人活着就是为了贤狼赫萝
         * from : 狼与香辛料
         * created_at : 1586916630
         */
        private String id;
        private String uuid;
        private String type;
        private String hitokoto;
        private String creator;
        private String from;
        private String created_at;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getHitokoto() {
            return hitokoto;
        }

        public void setHitokoto(String hitokoto) {
            this.hitokoto = hitokoto;
        }

        public String getCreator() {
            return creator;
        }

        public void setCreator(String creator) {
            this.creator = creator;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getCreated_at() {
            return created_at;
        }

        public void setCreated_at(String created_at) {
            this.created_at = created_at;
        }
    }

    /**
     * 一图
     */
    static class RandomImage {

        private Integer result;
        private String img;
        private Integer error;

        public Integer getResult() {
            return result;
        }

        public void setResult(Integer result) {
            this.result = result;
        }

        public String getImg() {
            return img;
        }

        public void setImg(String img) {
            this.img = img;
        }

        public Integer getError() {
            return error;
        }

        public void setError(Integer error) {
            this.error = error;
        }
    }

    static class RandomPicture {
        /**
         * PID : 9a8f807a-440e-11e9-8eca-f23c914b97eb
         * p_title : 地狱之刃：塞娜的献祭
         * p_content : Hellblade：Senua\'s Sacrifice
         * width : 7680
         * height : 4320
         * username : 绝对零º
         * p_link : https://ws1.sinaimg.cn/large/006N1muNgy1g0yccpxfwfj35xc3c04qu.jpg
         * local_url : https://img.dpic.dev/7bf3e60db3a1e08f0324b1b12f30da15
         * TID : e5771003-b4ed-11e8-a8ea-0202761b0892
         * p_date : 2019-03-12
         */
        private String PID;
        private String p_title;
        private String p_content;
        private int width;
        private int height;
        private String username;
        private String p_link;
        private String local_url;
        private String TID;
        private String p_date;
        /**
         * theme_color : #2c261b
         * text_color : #ffffff
         * T_NAME : 摄影
         * level : 1
         * nativePath : /202001/ad36bd3b7213bb1ff652df96713ac808.jpg
         */
        private String theme_color;
        private String text_color;
        private String T_NAME;
        private int level;
        private String nativePath;

        public String getPID() {
            return PID;
        }

        public void setPID(String PID) {
            this.PID = PID;
        }

        public String getP_title() {
            return p_title;
        }

        public void setP_title(String p_title) {
            this.p_title = p_title;
        }

        public String getP_content() {
            return p_content;
        }

        public void setP_content(String p_content) {
            this.p_content = p_content;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getP_link() {
            return p_link;
        }

        public void setP_link(String p_link) {
            this.p_link = p_link;
        }

        public String getLocal_url() {
            return local_url;
        }

        public void setLocal_url(String local_url) {
            this.local_url = local_url;
        }

        public String getTID() {
            return TID;
        }

        public void setTID(String TID) {
            this.TID = TID;
        }

        public String getP_date() {
            return p_date;
        }

        public void setP_date(String p_date) {
            this.p_date = p_date;
        }

        public String getTheme_color() {
            return theme_color;
        }

        public void setTheme_color(String theme_color) {
            this.theme_color = theme_color;
        }

        public String getText_color() {
            return text_color;
        }

        public void setText_color(String text_color) {
            this.text_color = text_color;
        }

        public String getT_NAME() {
            return T_NAME;
        }

        public void setT_NAME(String T_NAME) {
            this.T_NAME = T_NAME;
        }

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }

        public String getNativePath() {
            return nativePath;
        }

        public void setNativePath(String nativePath) {
            this.nativePath = nativePath;
        }
    }
}
