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
    // Ten semafor podwaja siłę mutexa: za każdym razem,
    // zamiast czekać na mutexa, będziemy czekali na protection,
    // a potem na mutexa. W obsłudze przerwań natomiast
    // będziemy czekali tylko na mutexa. W ten sposób jeśli
    // chcemy oddać mutexa do obsługi przerwań, to zwalniamy
    // mutex, ale nie protection.
    private final Semaphore protection;

    private final int[] rotationsWaiting;
    private boolean repInterrupted = false;
    private int currentAxis = -1;
    private int rotationsRunning;
    private int axesWaiting;
    private int allRotationsWaiting;

    private void start(int axis) throws InterruptedException {
        // flaga mówiąca, czy jesteśmy reprezentantem grupy (do obsługi przerwań)
        boolean isRep = false;
        protection.acquireUninterruptibly();
        mutex.acquireUninterruptibly();
        // jeśli nikogo nie ma w sekcji krytycznej, to wchodzimy
        if (currentAxis == -1)
            currentAxis = axis;
        // jeśli w sekcji krytycznej są procesy z innego gatunku niż nasz lub jeśli
        // czekają już procesy innego gatunku, to czekamy
        else if (currentAxis != axis || allRotationsWaiting - rotationsWaiting[axis] > 0) {
            allRotationsWaiting++;
            rotationsWaiting[axis]++;
            try {
                // jeśli jesteśmy pierwsi z danej osi, to stajemy się reprezentantami
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
                    // jeśli reprezentant został przerwany, to oddał nam pozycję
                    // reprezentanta (my musimy zawiesić się na semaforze reprezentantów)
                    if (repInterrupted) {
                        repInterrupted = false;
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
                // jeśli zostaliśmy przerwani, to musimy atomowo jednocześnie sprawdzić,
                // czy jest otwarty dla nas semafor i przywrócić stan systemu
                interruptMutex.acquireUninterruptibly();
                // jeśli został już dla nas otwarty semafor,
                // to możemy po prostu przejść
                if (isRep && waitingAxes.tryAcquire()) {
                    axesWaiting--;
                    currentAxis = axis;
                } else if (waitingRotations[axis].tryAcquire()) {
                    // jeśli zostaliśmy wybudzeni dlatego, że mamy zostać nowym reprezentantem, to jednak
                    // nie możemy przejść i musimy posprzątać lub wybudzić nowego reprezentanta
                    if (repInterrupted) {
                        rotationsWaiting[axis]--;
                        allRotationsWaiting--;
                        if (rotationsWaiting[axis] == 0) {
                            axesWaiting--;
                            repInterrupted = false;
                            mutex.release();
                            // nie oddajemy protection,
                            // bo dostaliśmy tylko mutexa
                        } else {
                            waitingRotations[axis].release();
                            // odziedziczyliśmy tylko mutexa, przekazujemy dalej tylko mutexa
                        }
                        interruptMutex.release();
                        throw e;
                    }
                } else {
                    // bierzemy tylko mutexa, a nie protection, żeby można było nam go oddać
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
                        repInterrupted = true;
                        // przekazujemy sekcję krytyczną (tylko mutex)
                        waitingRotations[axis].release();
                    } else
                        mutex.release();
                    interruptMutex.release();
                    throw e;
                }
                interruptMutex.release();
            }
            // skończyliśmy czekać
            rotationsWaiting[axis]--;
            allRotationsWaiting--;
        }
        rotationsRunning++;
        // Żeby nie doszło do zakleszczenia, musimy najpierw dostać interruptMutex, a potem mutex.
        // Dlatego też najpierw oddajemy tylko mutexa (nie protection) procesowi, który może mieć interruptMutex,
        // potem zabieramy interruptMutex, a potem zostaje nam przejść przez otwarty mutex.
        mutex.release();
        interruptMutex.acquireUninterruptibly();
        mutex.acquireUninterruptibly();
        if (rotationsWaiting[axis] > 0)
            // budzimy kaskadowo z przekazaniem sekcji krytycznej
            waitingRotations[axis].release();
        else {
            mutex.release();
            protection.release();
        }
        interruptMutex.release();
    }

    // parametr axis zachowany w przypadku chęci zmiany implementacji
    private void end(int axis) {
        // dostajemy mutexy nieprzerywalnie,
        // żeby nie musieć się zastanawiać,
        // czy przerwanie po obrocie sprawia,
        // że powinniśmy cofnąć obrót
        protection.acquireUninterruptibly();
        // pomiędzy protection a mutexem zdobywamy interruptMutex,
        // żeby nie zakleszczyć się z procesami obsługującymi przerwania
        interruptMutex.acquireUninterruptibly();
        mutex.acquireUninterruptibly();
        rotationsRunning--;
        if (rotationsRunning == 0) {
            if (axesWaiting > 0)
                // jeśli mamy kogo budzić, to budzimy reprezentanta
                // z dziedziczeniem sekcji krytycznej
                waitingAxes.release();
            else {
                currentAxis = -1;
                // jeśli nie ma już procesów oczekujących, to ustawiamy stan systemu na początkowy
                mutex.release();
                protection.release();
            }
        } else {
            mutex.release();
            protection.release();
        }
        interruptMutex.release();
    }

    private final Semaphore[] waitingForLayers;

    private void startLayer(int layer) throws InterruptedException {
        waitingForLayers[layer].acquire();
    }

    private void endLayer(int layer) {
        waitingForLayers[layer].release();
    }

    void start(int axis, int layer) throws InterruptedException {
        start(axis);
        try {
            startLayer(layer);
        } catch (InterruptedException e) {
            // jeśli zostaliśmy przerwani w trakcie czekania na warstwę,
            // to musimy wyjść z sekcji krytycznej
            end(axis);
            throw e;
        }
    }

    void end(int axis, int layer) {
        endLayer(layer);
        end(axis);
    }

    // Traktujemy show jako procedurę obracającą
    // fikcyjną osią. Może się wydawać, że jest
    // to wbrew założeniom czytelników i pisarzy,
    // ale to rozwiązanie nie traci ani
    // na żywotności, ani na współbieżności.
    void startShow() throws InterruptedException {
        start(3);
    }
    void endShow() {
        end(3);
    }

    Sync(int size) {
        mutex = new Semaphore(1);
        interruptMutex = new Semaphore(1);
        protection = new Semaphore(1);
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
