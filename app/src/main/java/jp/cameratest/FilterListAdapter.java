package jp.cameratest;

import android.annotation.TargetApi;
import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.DataBindingUtil;
import android.databinding.ViewDataBinding;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

/**
 * Created by masanori on 2016/06/01.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class FilterListAdapter extends RecyclerView.Adapter<FilterListAdapter.DataBindingHolder>{
    private ArrayList<FilterClass> filterClassList;

    public class FilterClass extends BaseObservable {
        private String filterName;
        private int filterNum;

        @Bindable
        public String getFilterName(){
            return filterName;
        }
        public void setFilterName(String newValue){
            filterName = newValue;
            // 変更されたことを通知
            notifyPropertyChanged(jp.cameratest.BR.filterName);
        }
        @Bindable
        public int getFilterNum(){
            return filterNum;
        }
        public void setFilterNum(int newValue){
            filterNum = newValue;
            // 変更されたことを通知
            notifyPropertyChanged(jp.cameratest.BR.filterNum);
        }
    }
    public static class DataBindingHolder extends RecyclerView.ViewHolder {
        private final ViewDataBinding dataBinding;

        public DataBindingHolder(View v) {
            super(v);
            dataBinding = DataBindingUtil.bind(v);
            dataBinding.getRoot().setOnClickListener((view) -> {
                RxBusProvider.getInstance().send(new SelectFilterEvent(getAdapterPosition()));
            });
        }
        public ViewDataBinding getBinding() {
            return dataBinding;
        }
    }
    public FilterListAdapter() {
        filterClassList = new ArrayList<>();

        addFilterItem("DEFAULT", CaptureRequest.CONTROL_EFFECT_MODE_OFF);
        addFilterItem("MONO", CaptureRequest.CONTROL_EFFECT_MODE_MONO);
        addFilterItem("NEGATIVE", CaptureRequest.CONTROL_EFFECT_MODE_NEGATIVE);
        addFilterItem("SEPIA", CaptureRequest.CONTROL_EFFECT_MODE_SEPIA);
        addFilterItem("AQUA", CaptureRequest.CONTROL_EFFECT_MODE_AQUA);
        addFilterItem("BLACKBOARD", CaptureRequest.CONTROL_EFFECT_MODE_BLACKBOARD);
        addFilterItem("WHITEBOARD", CaptureRequest.CONTROL_EFFECT_MODE_WHITEBOARD);
        addFilterItem("POSTERIZE", CaptureRequest.CONTROL_EFFECT_MODE_POSTERIZE);
        addFilterItem("SOLARIZE", CaptureRequest.CONTROL_EFFECT_MODE_SOLARIZE);
    }
    @Override
    public DataBindingHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view.
        View v = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.recycler_item_filter, viewGroup, false);

        return new DataBindingHolder(v);
    }
    @Override
    public void onBindViewHolder(DataBindingHolder viewHolder, final int position) {
        FilterClass filter = filterClassList.get(position);

        viewHolder.getBinding().setVariable(jp.cameratest.BR.filterclass, filter);

    }
    @Override
    public int getItemCount() {
        return filterClassList.size();
    }
    private void addFilterItem(@NonNull String filterName, @NonNull int filterNum){
        FilterClass newClass = new FilterClass();
        newClass.filterName = filterName;
        newClass.filterNum = filterNum;
        filterClassList.add(newClass);
    }
}
