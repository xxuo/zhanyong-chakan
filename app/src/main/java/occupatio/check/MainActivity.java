package occupatio.check;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String TAG = "AppSizeChecker";
    private ListView listView;
    private List<AppItem> allAppList = new ArrayList<>();
    private List<AppItem> displayAppList = new ArrayList<>();
    private AppAdapter adapter;
    private EditText searchBox;
    private LinearLayout llRefreshBtn;
    private TextView tvRefreshIcon;
    private Button btnAll, btnUser, btnSystem;
    private Button btnSortName, btnSortPercent, btnSortTotal, btnSortApp, btnSortData, btnSortCache;
    private Button btnSortTime, btnSortApk;
    private boolean hasPermission = false;
    private String searchKeyword = "";
    private RotateAnimation rotateAnim;

    private enum FilterMode { ALL, USER, SYSTEM }
    private FilterMode currentFilter = FilterMode.ALL;

    private enum SortMode {
        NAME_DESC, NAME_ASC,
        PERCENT_DESC, PERCENT_ASC,
        TOTAL_DESC, TOTAL_ASC,
        APP_DESC, APP_ASC,
        DATA_DESC, DATA_ASC,
        CACHE_DESC, CACHE_ASC,
        INSTALL_TIME_DESC, INSTALL_TIME_ASC,
        APK_SIZE_DESC, APK_SIZE_ASC
		}
    private SortMode currentSort = SortMode.TOTAL_DESC;

    class AppItem {
        String appName;
        String packageName;
        boolean isSystem;
        long total;
        long app;
        long data;
        long cache;
        long apkSize;
        long installTime;

        float getPercent() {
            if (total <= 0) return 0;
            return (data + cache) * 100f / total;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initRotateAnimation();

        LinearLayout root = new LinearLayout(this);
        root.setLayoutParams(new LinearLayout.LayoutParams(
								 ViewGroup.LayoutParams.MATCH_PARENT,
								 ViewGroup.LayoutParams.MATCH_PARENT
							 ));
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout topBar = createTopBar();
        root.addView(topBar);

        listView = new ListView(this);
        listView.setLayoutParams(new LinearLayout.LayoutParams(
									 ViewGroup.LayoutParams.MATCH_PARENT,
									 ViewGroup.LayoutParams.MATCH_PARENT,
									 1
								 ));
        listView.setDividerHeight(1);
        listView.setDivider(new ColorDrawable(Color.parseColor("#333333")));
        root.addView(listView);

        setContentView(root);

        adapter = new AppAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, pos, id) -> {
            AppItem item = displayAppList.get(pos);
            try {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(item.packageName);
                if (launchIntent != null) startActivity(launchIntent);
                else Toast.makeText(MainActivity.this, "无法打开此应用", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "打开失败", Toast.LENGTH_SHORT).show();
            }
        });

        listView.setOnItemLongClickListener((parent, view, pos, id) -> {
            AppItem item = displayAppList.get(pos);
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + item.packageName));
            startActivity(intent);
            return true;
        });

        checkPermissionAndLoad();
    }

    private void initRotateAnimation() {
        rotateAnim = new RotateAnimation(0, 360,
										 Animation.RELATIVE_TO_SELF, 0.5f,
										 Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnim.setDuration(1000);
        rotateAnim.setRepeatCount(Animation.INFINITE);
        rotateAnim.setInterpolator(new LinearInterpolator());
        rotateAnim.setFillAfter(true);
    }

    private LinearLayout createTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        bar.setBackgroundColor(Color.parseColor("#222222"));

        searchBox = new EditText(this);
        searchBox.setHint("🔍 搜索应用名称或包名");
        searchBox.setTextSize(13);
        searchBox.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
        searchBox.setBackgroundColor(Color.parseColor("#333333"));
        searchBox.setTextColor(Color.WHITE);
        searchBox.setHintTextColor(Color.parseColor("#888888"));
        searchBox.setSingleLine(true);

        // ====================== 这里改成实时搜索 ======================
        searchBox.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
					// 输入立刻筛选，不等加载完成
					searchKeyword = s.toString().toLowerCase(Locale.CHINA);
					filterAndSortData();
				}
				@Override
				public void afterTextChanged(Editable s) {}
			});

        bar.addView(searchBox);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setLayoutParams(new LinearLayout.LayoutParams(
								 ViewGroup.LayoutParams.MATCH_PARENT,
								 ViewGroup.LayoutParams.WRAP_CONTENT
							 ));
        row1.setPadding(0, dpToPx(4), 0, 0);

        llRefreshBtn = new LinearLayout(this);
        llRefreshBtn.setLayoutParams(new LinearLayout.LayoutParams(0, dpToPx(28), 1));
        llRefreshBtn.setBackgroundColor(Color.parseColor("#444444"));
        llRefreshBtn.setGravity(Gravity.CENTER);
        llRefreshBtn.setClickable(true);
        llRefreshBtn.setFocusable(true);
        llRefreshBtn.setOnClickListener(v -> refreshData());

        tvRefreshIcon = new TextView(this);
        tvRefreshIcon.setText("🔄");
        tvRefreshIcon.setTextSize(14);
        tvRefreshIcon.setTextColor(Color.WHITE);
        llRefreshBtn.addView(tvRefreshIcon);

        btnAll = createButton("全部", v -> setFilter(FilterMode.ALL));
        btnUser = createButton("软件", v -> setFilter(FilterMode.USER));
        btnSystem = createButton("系统", v -> setFilter(FilterMode.SYSTEM));
        btnSortName = createButton("名称", v -> cycleSort(SortMode.NAME_ASC, SortMode.NAME_DESC));
        btnSortTime = createButton("时间", v -> cycleSort(SortMode.INSTALL_TIME_DESC, SortMode.INSTALL_TIME_ASC));

        row1.addView(llRefreshBtn);
        row1.addView(btnAll);
        row1.addView(btnUser);
        row1.addView(btnSystem);
        row1.addView(btnSortName);
        row1.addView(btnSortTime);
        bar.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setLayoutParams(new LinearLayout.LayoutParams(
								 ViewGroup.LayoutParams.MATCH_PARENT,
								 ViewGroup.LayoutParams.WRAP_CONTENT
							 ));
        row2.setPadding(0, dpToPx(2), 0, dpToPx(2));

        btnSortPercent = createButton("比例", v -> cycleSort(SortMode.PERCENT_DESC, SortMode.PERCENT_ASC));
        btnSortTotal = createButton("总计", v -> cycleSort(SortMode.TOTAL_DESC, SortMode.TOTAL_ASC));
        btnSortApp = createButton("应用", v -> cycleSort(SortMode.APP_DESC, SortMode.APP_ASC));
        btnSortApk = createButton("包体", v -> cycleSort(SortMode.APK_SIZE_DESC, SortMode.APK_SIZE_ASC));
        btnSortData = createButton("数据", v -> cycleSort(SortMode.DATA_DESC, SortMode.DATA_ASC));
        btnSortCache = createButton("缓存", v -> cycleSort(SortMode.CACHE_DESC, SortMode.CACHE_ASC));

        row2.addView(btnSortPercent);
        row2.addView(btnSortTotal);
        row2.addView(btnSortApp);
        row2.addView(btnSortApk);
        row2.addView(btnSortData);
        row2.addView(btnSortCache);
        bar.addView(row2);

        return bar;
    }

    private Button createButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11);
        button.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
        button.setLayoutParams(new LinearLayout.LayoutParams(0, dpToPx(28), 1));
        button.setBackgroundColor(Color.parseColor("#444444"));
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
			TypedValue.COMPLEX_UNIT_DIP, dp,
			getResources().getDisplayMetrics());
    }

    private void setFilter(FilterMode mode) {
        currentFilter = mode;
        updateFilterButtonStyles();
        filterAndSortData();
    }

    private void updateFilterButtonStyles() {
        btnAll.setBackgroundColor(currentFilter == FilterMode.ALL ?
								  Color.parseColor("#FF5722") : Color.parseColor("#444444"));
        btnUser.setBackgroundColor(currentFilter == FilterMode.USER ?
								   Color.parseColor("#FF5722") : Color.parseColor("#444444"));
        btnSystem.setBackgroundColor(currentFilter == FilterMode.SYSTEM ?
									 Color.parseColor("#FF5722") : Color.parseColor("#444444"));
    }

    private void cycleSort(SortMode desc, SortMode asc) {
        currentSort = (currentSort == desc) ? asc : desc;
        updateSortButtonStyles();
        filterAndSortData();
    }

    private String formatTime(long time) {
        if (time <= 0) return "未知";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        return sdf.format(new Date(time));
    }

    private void updateSortButtonStyles() {
        btnSortName.setBackgroundColor(Color.parseColor("#444444")); btnSortName.setText("名称");
        btnSortPercent.setBackgroundColor(Color.parseColor("#444444")); btnSortPercent.setText("比例");
        btnSortTotal.setBackgroundColor(Color.parseColor("#444444")); btnSortTotal.setText("总计");
        btnSortApp.setBackgroundColor(Color.parseColor("#444444")); btnSortApp.setText("应用");
        btnSortApk.setBackgroundColor(Color.parseColor("#444444")); btnSortApk.setText("包体");
        btnSortData.setBackgroundColor(Color.parseColor("#444444")); btnSortData.setText("数据");
        btnSortCache.setBackgroundColor(Color.parseColor("#444444")); btnSortCache.setText("缓存");
        btnSortTime.setBackgroundColor(Color.parseColor("#444444")); btnSortTime.setText("时间");

        if (currentSort == SortMode.NAME_ASC) { btnSortName.setBackgroundColor(Color.parseColor("#FF5722")); btnSortName.setText("名称↓"); }
        else if (currentSort == SortMode.NAME_DESC) { btnSortName.setBackgroundColor(Color.parseColor("#FF5722")); btnSortName.setText("名称↑"); }

        if (currentSort == SortMode.PERCENT_DESC) { btnSortPercent.setBackgroundColor(Color.parseColor("#FF5722")); btnSortPercent.setText("比例↓"); }
        else if (currentSort == SortMode.PERCENT_ASC) { btnSortPercent.setBackgroundColor(Color.parseColor("#FF5722")); btnSortPercent.setText("比例↑"); }

        if (currentSort == SortMode.TOTAL_DESC) { btnSortTotal.setBackgroundColor(Color.parseColor("#FF5722")); btnSortTotal.setText("总计↓"); }
        else if (currentSort == SortMode.TOTAL_ASC) { btnSortTotal.setBackgroundColor(Color.parseColor("#FF5722")); btnSortTotal.setText("总计↑"); }

        if (currentSort == SortMode.APP_DESC) { btnSortApp.setBackgroundColor(Color.parseColor("#FF5722")); btnSortApp.setText("应用↓"); }
        else if (currentSort == SortMode.APP_ASC) { btnSortApp.setBackgroundColor(Color.parseColor("#FF5722")); btnSortApp.setText("应用↑"); }

        if (currentSort == SortMode.APK_SIZE_DESC) { btnSortApk.setBackgroundColor(Color.parseColor("#FF5722")); btnSortApk.setText("包体↓"); }
        else if (currentSort == SortMode.APK_SIZE_ASC) { btnSortApk.setBackgroundColor(Color.parseColor("#FF5722")); btnSortApk.setText("包体↑"); }

        if (currentSort == SortMode.DATA_DESC) { btnSortData.setBackgroundColor(Color.parseColor("#FF5722")); btnSortData.setText("数据↓"); }
        else if (currentSort == SortMode.DATA_ASC) { btnSortData.setBackgroundColor(Color.parseColor("#FF5722")); btnSortData.setText("数据↑"); }

        if (currentSort == SortMode.CACHE_DESC) { btnSortCache.setBackgroundColor(Color.parseColor("#FF5722")); btnSortCache.setText("缓存↓"); }
        else if (currentSort == SortMode.CACHE_ASC) { btnSortCache.setBackgroundColor(Color.parseColor("#FF5722")); btnSortCache.setText("缓存↑"); }

        if (currentSort == SortMode.INSTALL_TIME_DESC) { btnSortTime.setBackgroundColor(Color.parseColor("#FF5722")); btnSortTime.setText("时间↓"); }
        else if (currentSort == SortMode.INSTALL_TIME_ASC) { btnSortTime.setBackgroundColor(Color.parseColor("#FF5722")); btnSortTime.setText("时间↑"); }
    }

    // ====================== 筛选逻辑：立刻生效，不等待加载 ======================
    private void filterAndSortData() {
        List<AppItem> filtered = new ArrayList<>();
        for (AppItem item : allAppList) {
            switch (currentFilter) {
                case USER: if (item.isSystem) continue; break;
                case SYSTEM: if (!item.isSystem) continue; break;
                case ALL: default: break;
            }
            if (!TextUtils.isEmpty(searchKeyword)) {
                String lower = searchKeyword;
                if (!item.appName.toLowerCase(Locale.CHINA).contains(lower) &&
					!item.packageName.toLowerCase(Locale.CHINA).contains(lower)) {
                    continue;
                }
            }
            filtered.add(item);
        }

        Collections.sort(filtered, (a, b) -> {
            switch (currentSort) {
                case NAME_ASC: return a.appName.compareTo(b.appName);
                case NAME_DESC: return b.appName.compareTo(a.appName);
                case PERCENT_ASC: return Float.compare(a.getPercent(), b.getPercent());
                case PERCENT_DESC: return Float.compare(b.getPercent(), a.getPercent());
                case TOTAL_ASC: return Long.compare(a.total, b.total);
                case TOTAL_DESC: return Long.compare(b.total, a.total);
                case APP_ASC: return Long.compare(a.app, b.app);
                case APP_DESC: return Long.compare(b.app, a.app);
                case APK_SIZE_ASC: return Long.compare(a.apkSize, b.apkSize);
                case APK_SIZE_DESC: return Long.compare(b.apkSize, a.apkSize);
                case DATA_ASC: return Long.compare(a.data, b.data);
                case DATA_DESC: return Long.compare(b.data, a.data);
                case CACHE_ASC: return Long.compare(a.cache, b.cache);
                case CACHE_DESC: return Long.compare(b.cache, a.cache);
                case INSTALL_TIME_ASC: return Long.compare(a.installTime, b.installTime);
                case INSTALL_TIME_DESC: return Long.compare(b.installTime, a.installTime);
                default: return Long.compare(b.total, a.total);
            }
        });

        displayAppList.clear();
        displayAppList.addAll(filtered);
        adapter.notifyDataSetChanged();
    }

    private void addAppToDisplayImmediately(AppItem item) {
        runOnUiThread(() -> {
            allAppList.add(item);
            filterAndSortData(); // 每加一条就重新筛一次，保证搜索实时
        });
    }

    private void refreshData() {
        allAppList.clear();
        displayAppList.clear();
        adapter.notifyDataSetChanged();
        new LoadAppSizeTask().execute();
    }

    private void checkPermissionAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                android.app.AppOpsManager appOps = (android.app.AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
                int mode = appOps.checkOpNoThrow("android:get_usage_stats", Process.myUid(), getPackageName());
                hasPermission = (mode == android.app.AppOpsManager.MODE_ALLOWED);
                if (!hasPermission) {
                    Toast.makeText(this, "需要开启【使用情况访问权限】", Toast.LENGTH_LONG).show();
                    startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), 100);
                } else {
                    refreshData();
                }
            } catch (Exception e) {
                e.printStackTrace();
                refreshData();
            }
        } else {
            refreshData();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100) checkPermissionAndLoad();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format(Locale.CHINA, "%.1fKB", bytes / 1024f);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.CHINA, "%.1fMB", bytes / 1024f / 1024);
        return String.format(Locale.CHINA, "%.1fGB", bytes / 1024f / 1024 / 1024);
    }

    private class LoadAppSizeTask extends AsyncTask<Void, AppItem, Void> {
        @Override
        protected void onPreExecute() {
            tvRefreshIcon.startAnimation(rotateAnim);
            llRefreshBtn.setEnabled(false);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            for (ApplicationInfo appInfo : apps) {
                AppItem item = new AppItem();
                item.packageName = appInfo.packageName;
                String label = appInfo.loadLabel(pm).toString();
                item.appName = TextUtils.isEmpty(label) ? appInfo.packageName : label;
                item.isSystem = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

                try {
                    File apkFile = new File(appInfo.sourceDir);
                    item.apkSize = apkFile.exists() ? apkFile.length() : 0;
                } catch (Exception e) { item.apkSize = 0; }

                try {
                    PackageInfo pkgInfo = pm.getPackageInfo(appInfo.packageName, 0);
                    item.installTime = pkgInfo.firstInstallTime;
                } catch (Exception e) { item.installTime = 0; }

                getAppSizeMultipleMethods(item, appInfo);
                publishProgress(item);
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(AppItem... values) {
            if (values != null && values.length > 0) {
                addAppToDisplayImmediately(values[0]);
            }
        }

        @Override
        protected void onPostExecute(Void unused) {
            tvRefreshIcon.clearAnimation();
            llRefreshBtn.setEnabled(true);

            currentFilter = FilterMode.ALL;
            currentSort = SortMode.TOTAL_DESC;
            updateFilterButtonStyles();
            updateSortButtonStyles();
            filterAndSortData();

            int userApps = 0, systemApps = 0;
            long totalSize = 0;
            for (AppItem item : allAppList) {
                if (item.isSystem) systemApps++; else userApps++;
                totalSize += item.total;
            }
            String msg = String.format(Locale.CHINA,
									   "共%d个 (用户:%d 系统:%d) 总:%s",
									   allAppList.size(), userApps, systemApps, formatSize(totalSize));
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
        }

        private void getAppSizeMultipleMethods(AppItem item, ApplicationInfo appInfo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (getAppSizeWithStorageStats(item, appInfo)) return;
            }
            if (getAppSizeManually(item, appInfo)) return;
            getApkSizeOnly(item, appInfo);
        }

        private boolean getAppSizeWithStorageStats(AppItem item, ApplicationInfo appInfo) {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;
                Object storageStatsManager = getSystemService(Context.STORAGE_STATS_SERVICE);
                if (storageStatsManager == null) return false;
                UUID storageUuid = getStorageUuid();
                if (storageUuid == null) return false;
                Class<?> ssmClass = storageStatsManager.getClass();
                Method queryMethod = ssmClass.getMethod("queryStatsForPackage",
														UUID.class, String.class, android.os.UserHandle.class);
                Object stats = queryMethod.invoke(storageStatsManager,
												  storageUuid, appInfo.packageName, Process.myUserHandle());
                if (stats != null) {
                    Class<?> statsClass = stats.getClass();
                    Method getAppBytes = statsClass.getMethod("getAppBytes");
                    Method getDataBytes = statsClass.getMethod("getDataBytes");
                    Method getCacheBytes = statsClass.getMethod("getCacheBytes");
                    item.app = (long) getAppBytes.invoke(stats);
                    item.data = (long) getDataBytes.invoke(stats);
                    item.cache = (long) getCacheBytes.invoke(stats);
                    item.total = item.app + item.data + item.cache;
                    return true;
                }
            } catch (Exception e) {
                Log.d(TAG, "StorageStats失败: " + item.packageName);
            }
            return false;
        }

        private UUID getStorageUuid() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    StorageManager storageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
                    if (storageManager != null) {
                        List<StorageVolume> volumes = storageManager.getStorageVolumes();
                        if (volumes != null && !volumes.isEmpty()) {
                            for (StorageVolume volume : volumes) {
                                try {
                                    Method getUuidMethod = volume.getClass().getMethod("getUuid");
                                    String uuidStr = (String) getUuidMethod.invoke(volume);
                                    if (uuidStr == null) {
                                        return (UUID) StorageManager.class.getField("UUID_DEFAULT").get(null);
                                    } else {
                                        return UUID.fromString(uuidStr);
                                    }
                                } catch (Exception e) {}
                            }
                        }
                    }
                }
                return (UUID) StorageManager.class.getField("UUID_DEFAULT").get(null);
            } catch (Exception e) {
                return null;
            }
        }

        private boolean getAppSizeManually(AppItem item, ApplicationInfo appInfo) {
            try {
                File apkFile = new File(appInfo.sourceDir);
                if (apkFile.exists()) item.app = apkFile.length();
                File externalDataDir = new File(Environment.getExternalStorageDirectory(),
												"Android/data/" + appInfo.packageName);
                if (externalDataDir.exists()) item.data = getFolderSize(externalDataDir);
                File obbDir = new File(Environment.getExternalStorageDirectory(),
									   "Android/obb/" + appInfo.packageName);
                if (obbDir.exists()) item.data += getFolderSize(obbDir);
                item.total = item.app + item.data + item.cache;
                return item.total > 0;
            } catch (Exception e) {
                return false;
            }
        }

        private void getApkSizeOnly(AppItem item, ApplicationInfo appInfo) {
            try {
                File apkFile = new File(appInfo.sourceDir);
                item.app = apkFile.exists() ? apkFile.length() : 0;
                item.total = item.app;
            } catch (Exception e) { item.total = 0; }
        }

        private long getFolderSize(File file) {
            if (file == null || !file.exists()) return 0;
            long size = 0;
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files != null) for (File f : files) size += getFolderSize(f);
            } else size = file.length();
            return size;
        }
    }

    private class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() { return displayAppList.size(); }
        @Override
        public Object getItem(int position) { return displayAppList.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if (convertView == null) {
				holder = new ViewHolder();
				LinearLayout outerLayout = new LinearLayout(MainActivity.this);
				outerLayout.setOrientation(LinearLayout.VERTICAL);
				outerLayout.setLayoutParams(new ListView.LayoutParams(
												ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
											));
				LinearLayout rootLayout = new LinearLayout(MainActivity.this);
				rootLayout.setOrientation(LinearLayout.VERTICAL);
				rootLayout.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));

				LinearLayout topRow = new LinearLayout(MainActivity.this);
				topRow.setOrientation(LinearLayout.HORIZONTAL);
				topRow.setLayoutParams(new LinearLayout.LayoutParams(
										   ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.7f
									   ));

				holder.tvName = new TextView(MainActivity.this);
				holder.tvName.setTextSize(12);
				holder.tvName.setMaxLines(1);
				holder.tvName.setEllipsize(TextUtils.TruncateAt.END);
				holder.tvName.setLayoutParams(new LinearLayout.LayoutParams(
												  0, ViewGroup.LayoutParams.MATCH_PARENT, 2
											  ));
				holder.tvName.setGravity(Gravity.CENTER_VERTICAL);
				topRow.addView(holder.tvName);

				holder.tvPercent = new TextView(MainActivity.this);
				holder.tvPercent.setTextSize(12);
				holder.tvPercent.setMaxLines(1);
				holder.tvPercent.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
				holder.tvPercent.setLayoutParams(new LinearLayout.LayoutParams(
													 0, ViewGroup.LayoutParams.MATCH_PARENT, 1
												 ));
				topRow.addView(holder.tvPercent);
				rootLayout.addView(topRow);

				holder.tvPkg = new TextView(MainActivity.this);
				holder.tvPkg.setTextSize(12);
				holder.tvPkg.setTextColor(0xFFAAAAAA);
				holder.tvPkg.setMaxLines(1);
				holder.tvPkg.setEllipsize(TextUtils.TruncateAt.MIDDLE);
				holder.tvPkg.setSingleLine(true);
				rootLayout.addView(holder.tvPkg);

				holder.tvSize = new TextView(MainActivity.this);
				holder.tvSize.setTextSize(12);
				holder.tvSize.setTextColor(0xFFCCCCCC);
				holder.tvSize.setMaxLines(1);
				holder.tvSize.setEllipsize(TextUtils.TruncateAt.END);
				rootLayout.addView(holder.tvSize);

				outerLayout.addView(rootLayout);
				View divider = new View(MainActivity.this);
				divider.setLayoutParams(new LinearLayout.LayoutParams(
											ViewGroup.LayoutParams.MATCH_PARENT, 1
										));
				divider.setBackgroundColor(Color.WHITE);
				outerLayout.addView(divider);

				convertView = outerLayout;
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}

			AppItem item = displayAppList.get(position);
			// 【修改1】时间移到名称行
			String nameText = item.appName + (item.isSystem ? " [系]" : " [用]") + "  时:" + formatTime(item.installTime);
			holder.tvName.setText(nameText);
			holder.tvName.setTextColor(item.isSystem ? 0xFFAAAAAA : 0xFFFFFFFF);

			float percent = item.getPercent();
			holder.tvPercent.setText(String.format(Locale.CHINA, "%.1f%%", percent));
			holder.tvPkg.setText(item.packageName);

			// 【修改2】大小行删除时间
			String sizeInfo = String.format(Locale.CHINA,
											"总:%-6s 应:%-6s 包:%-6s 数:%-6s 缓:%-6s",
											formatSize(item.total), formatSize(item.app),
											formatSize(item.apkSize), formatSize(item.data),
											formatSize(item.cache));
			holder.tvSize.setText(sizeInfo);

			convertView.setBackgroundColor(percent > 45 ? 0x44FFFF00 : Color.TRANSPARENT);
			return convertView;
		}		

        private class ViewHolder {
            TextView tvName, tvPercent, tvPkg, tvSize;
        }
    }
}

