package jp.cameratest;

/**
 * Created by masanori on 2016/06/06.
 */
public class RxBusProvider {
    private static final RxBus Bus = new RxBus();

    private RxBusProvider() {
        // No instances.
    }

    public static RxBus getInstance() {
        return Bus;
    }
}
