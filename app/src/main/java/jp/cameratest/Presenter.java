package jp.cameratest;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;

/**
 * Created by masanori on 2016/06/01.
 */
public class Presenter {
    private Activity currentActivity;
    private final static SelectFilterFragment selectFilterFragment = new SelectFilterFragment();
    public Presenter(Activity newActivity){
        currentActivity = newActivity;
    }
    public void showFilterSelector(FragmentManager fragmentManager){
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.add(R.id.activity_main_container, selectFilterFragment);
        transaction.commit();
    }
}
