package jp.cameratest;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.util.Log;


import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;

/**
 * Created by masanori on 2016/06/01.
 */
public class Presenter {
    private Activity currentActivity;
    private final static SelectFilterFragment selectFilterFragment = new SelectFilterFragment();
    private CompositeSubscription compositeSubscription;

    public Presenter(Activity newActivity){
        currentActivity = newActivity;
    }
    public void onResume(){
        Subscription subscription = RxBusProvider.getInstance()
                .toObserverable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(o -> {
                    if (o instanceof SelectFilterEvent) {
                        Log.d("testtest", "Position" + ((SelectFilterEvent)o).getCurrentPosition());
                        // イベントが来ました
                    }
                });
        compositeSubscription = new CompositeSubscription(subscription);
    }
    public void onPause(){
        compositeSubscription.unsubscribe();
    }
    public void showFilterSelector(FragmentManager fragmentManager){

        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.add(R.id.activity_main_container, selectFilterFragment);
        transaction.commit();
    }
}
