package jp.cameratest;

/**
 * Created by masanori on 2016/06/06.
 */
public class SelectFilterEvent {
    private int currentPosition;
    public int getCurrentPosition(){
        return currentPosition;
    }
    public SelectFilterEvent(int selectedPosition){
        currentPosition = selectedPosition;
    }
}
