package jp.cameratest;

import android.databinding.DataBindingUtil;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import jp.cameratest.databinding.ActivityCamera2Binding;

public class Camera2Activity extends AppCompatActivity {

    private Presenter presenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // フルスクリーン表示.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_camera2);

        // プレビュー用のViewを追加.
        ActivityCamera2Binding binding = (ActivityCamera2Binding)DataBindingUtil.setContentView(this, R.layout.activity_camera2);
        setSupportActionBar((Toolbar) binding.toolbarCamera2);
        // Toolbarのタイトルを非表示にする.
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null){
            actionBar.setDisplayShowTitleEnabled(false);
        }

        int savedOrientationNum;
        int savedFilterNum;
        // 画面回転時などSavedInstanceStateに値が残っていれば取得する.
        if(savedInstanceState == null){
            savedOrientationNum = -1;
            savedFilterNum = -1;
        }
        else{
            savedOrientationNum = savedInstanceState.getInt(getString(R.string.saved_orientation_num));
            savedFilterNum = savedInstanceState.getInt(getString(R.string.saved_filter_num));
        }

        presenter = new Presenter(this, binding, savedOrientationNum, savedFilterNum);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_camera2, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_filter_camera2:
                if(presenter != null){
                    presenter.showFilterSelector();
                }
                return true;
            case R.id.action_settings_camera2:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    @Override
    public void onResume(){
        super.onResume();
        presenter.onResume();
    }
    @Override
    public void onPause(){
        super.onPause();
        presenter.onPause();
    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        presenter.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(getString(R.string.saved_orientation_num), presenter.getLastOrientationNum());
        outState.putInt(getString(R.string.saved_filter_num), presenter.getLastFilterNum());
    }
    @Override
    public void onRequestPermissionsResult(int intRequestCode
            , @NonNull String[] strPermissions
            , @NonNull int[] intGrantResults) {
        super.onRequestPermissionsResult(intRequestCode, strPermissions, intGrantResults);
        presenter.onRequestPermissionResult(intRequestCode, intGrantResults);
    }
}
