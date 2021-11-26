package concurrentcube;

import java.util.concurrent.Semaphore;

public class Sync {
    private final Semaphore mutex;
    private final Semaphore[] waitingRotations;
    private final Semaphore waitingAxes;
    // Ten semafor służy do obsługi przerwań w wypadku,
    // gdy wątek przerwany nie zdąży po sobie posprzątać
    // zanim jakiś inny wątek wykona protokół końcowy.
    // Musimy więc zapewnić atomowość obsługi przerwań
    // względem protokołu końcowego i budzenia kaskadowego,
    // żeby nie można było otworzyć przerwanemu procesowi
    // semafora w trakcie, gdy on obsługuje przerwanie.
    private final Semaphore interruptMutex;

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
            try {
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
            } catch (InterruptedException e) {
                interruptMutex.acquireUninterruptibly();
                // jeśli zostaliśmy przerwani, a
                // został już dla nas otwarty semafor,
                // to możemy po prostu przejść
                if (waitingAxes.tryAcquire()) {
                    axesWaiting--;
                    currentAxis = axis;
                    mutex.release();
                } else if (waitingRotations[axis].tryAcquire()) {
                    mutex.release();
                } else {
                    // w przeciwnym wypadku
                    // musimy po sobie posprzątać i zgłosić przerwanie
                    mutex.acquireUninterruptibly();
                    rotationsWaiting[axis]--;
                    if (rotationsWaiting[axis] == 0)
                        axesWaiting--;
                    interruptMutex.release();
                    mutex.release();
                    throw e;
                }
                interruptMutex.release();
            }
            rotationsWaiting[axis]--;
            allRotationsWaiting--;
        }
        rotationsRunning++;
        interruptMutex.acquireUninterruptibly();
        if (rotationsWaiting[axis] > 0)
            waitingRotations[axis].release();
        else
            mutex.release();
        interruptMutex.release();
    }

    void end(int axis) {
        // dostajemy mutexa nieprzerywalnie,
        // żeby nie musieć się zastanawiać,
        // czy przerwanie po obrocie sprawia,
        // że powinniśmy cofnąć obrót
        mutex.acquireUninterruptibly();
        interruptMutex.acquireUninterruptibly();
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
        interruptMutex.release();
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
        interruptMutex = new Semaphore(1);
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
