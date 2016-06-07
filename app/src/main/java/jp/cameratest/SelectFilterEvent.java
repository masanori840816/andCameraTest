package jp.cameratest;

/**
 * Created by masanori on 2016/06/06.
 */
public class SelectFilterEvent {
    private int currentFilterNum;
    public int getCurrentFilterNum(){
        return currentFilterNum;
    }
    public SelectFilterEvent(int selectedFilterNum){
        currentFilterNum = selectedFilterNum;
    }
}
