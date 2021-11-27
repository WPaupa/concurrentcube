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
    private final Semaphore protection;

    private final int[] rotationsWaiting;
    private final int[] repsInterrupted;
    private int currentAxis = -1;
    private int rotationsRunning;
    private int axesWaiting;
    private int allRotationsWaiting;

    void start(int axis) throws InterruptedException {
        boolean isRep = false;
        protection.acquire();
        mutex.acquire();
        if (currentAxis == -1)
            currentAxis = axis;
        else if (currentAxis != axis || allRotationsWaiting - rotationsWaiting[axis] > 0) {
            allRotationsWaiting++;
            rotationsWaiting[axis]++;
            try {
                if (rotationsWaiting[axis] == 1) {
                    axesWaiting++;
                    isRep = true;
                    mutex.release();
                    protection.release();
                    waitingAxes.acquire();
                    axesWaiting--;
                    currentAxis = axis;
                } else {
                    mutex.release();
                    protection.release();
                    waitingRotations[axis].acquire();
                    if (repsInterrupted[axis] > 0) {
                        repsInterrupted[axis]--;
                        isRep = true;
                        mutex.release();
                        // nie oddajemy protection,
                        // bo dostaliśmy tylko mutexa
                        waitingAxes.acquire();
                        axesWaiting--;
                        currentAxis = axis;
                    }
                }
            } catch (InterruptedException e) {
                interruptMutex.acquireUninterruptibly();
                // jeśli zostaliśmy przerwani, a
                // został już dla nas otwarty semafor,
                // to możemy po prostu przejść
                if (waitingAxes.tryAcquire()) {
                    axesWaiting--;
                    currentAxis = axis;
                } else if (waitingRotations[axis].tryAcquire()) {
                    // w przeciwnym wypadku lub gdy mieliśmy zostać
                    // nowym reprezentantem, to
                    // musimy po sobie posprzątać i zgłosić przerwanie
                    if (repsInterrupted[axis] > 0) {
                        rotationsWaiting[axis]--;
                        allRotationsWaiting--;
                        if (rotationsWaiting[axis] == 0) {
                            axesWaiting--;
                            repsInterrupted[axis]--;
                            mutex.release();
                            // nie oddajemy protection,
                            // bo dostaliśmy tylko mutexa
                        } else {
                            waitingRotations[axis].release();
                        }
                        interruptMutex.release();
                        throw e;
                    }
                } else {
                    mutex.acquireUninterruptibly();
                    rotationsWaiting[axis]--;
                    allRotationsWaiting--;
                    if (rotationsWaiting[axis] == 0) {
                        axesWaiting--;
                        mutex.release();
                    }
                    // jeśli byliśmy reprezentantem,
                    // to musimy wyznaczyć nowego reprezentanta
                    else if (isRep) {
                        repsInterrupted[axis]++;
                        waitingRotations[axis].release();
                    } else
                        mutex.release();
                    interruptMutex.release();
                    throw e;
                }
                interruptMutex.release();
            }
            rotationsWaiting[axis]--;
            allRotationsWaiting--;
        }
        rotationsRunning++;
        mutex.release();
        interruptMutex.acquireUninterruptibly();
        mutex.acquireUninterruptibly();
        if (rotationsWaiting[axis] > 0)
            waitingRotations[axis].release();
        else {
            mutex.release();
            protection.release();
        }
        interruptMutex.release();
    }

    void end(int axis) {
        // dostajemy mutexa nieprzerywalnie,
        // żeby nie musieć się zastanawiać,
        // czy przerwanie po obrocie sprawia,
        // że powinniśmy cofnąć obrót
        protection.acquireUninterruptibly();
        interruptMutex.acquireUninterruptibly();
        mutex.acquireUninterruptibly();
        rotationsRunning--;
        if (rotationsRunning == 0) {
            if (axesWaiting > 0)
                waitingAxes.release();
            else {
                currentAxis = -1;
                mutex.release();
                protection.release();
            }
        } else {
            mutex.release();
            protection.release();
        }
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
        protection = new Semaphore(1);
        waitingAxes = new Semaphore(0);
        waitingRotations = new Semaphore[4];
        repsInterrupted = new int[4];
        for (int i = 0; i < 4; i++)
            waitingRotations[i] = new Semaphore(0);
        rotationsWaiting = new int[4];
        waitingForLayers = new Semaphore[size];
        for (int i = 0; i < size; i++)
            waitingForLayers[i] = new Semaphore(1);
    }
}
