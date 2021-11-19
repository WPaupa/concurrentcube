package concurrentcube;

import java.util.concurrent.Semaphore;

public class Sync {
    private final Semaphore mutex;
    private final Semaphore[] waitingRotations;
    private final Semaphore waitingAxes;

    private final int[] rotationsWaiting;
    private int currentAxis = -1;
    private int rotationsRunning;
    private int axesWaiting;
    private int allRotationsWaiting;

    void start(int axis) throws InterruptedException {
        mutex.acquire();
        if (currentAxis == -1)
            currentAxis = axis;
        else if (currentAxis != axis || allRotationsWaiting - rotationsWaiting[axis] > 0) {
            allRotationsWaiting++;
            rotationsWaiting[axis]++;
            if (rotationsWaiting[axis] == 1) {
                axesWaiting++;
                mutex.release();
                waitingAxes.acquire();
                axesWaiting--;
                currentAxis = axis;
            } else {
                mutex.release();
                waitingRotations[axis].acquire();
            }
            rotationsWaiting[axis]--;
            allRotationsWaiting--;
        }
        rotationsRunning++;
        if (rotationsWaiting[axis] > 0)
            waitingRotations[axis].release();
        else
            mutex.release();
    }

    void end(int axis) throws InterruptedException {
        mutex.acquire();
        rotationsRunning--;
        if (rotationsRunning == 0) {
            if (axesWaiting > 0)
                waitingAxes.release();
            else {
                currentAxis = -1;
                mutex.release();
            }
        } else
            mutex.release();
    }

    Semaphore[] waitingForLayers;

    void startLayer(int layer) throws InterruptedException {
        waitingForLayers[layer].acquire();
    }

    void endLayer(int layer) {
        waitingForLayers[layer].release();
    }

    public Sync(int size) {
        mutex = new Semaphore(1);
        waitingAxes = new Semaphore(0);
        waitingRotations = new Semaphore[4];
        for (int i = 0; i < 4; i++)
            waitingRotations[i] = new Semaphore(0);
        rotationsWaiting = new int[4];
        waitingForLayers = new Semaphore[size];
        for (int i = 0; i < size; i++)
            waitingForLayers[i] = new Semaphore(1);
    }
}
