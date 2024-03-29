package concurrentcube;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

class CubeTest {

    private static class Mover implements Runnable {

        private final int side;
        private final int layer;
        private final Cube cube;

        public Mover(int side, int layer, Cube cube) {
            this.cube = cube;
            this.layer = layer;
            this.side = side;
        }

        @Override
        public void run() {
            try {
                cube.rotate(side, layer);
            } catch (InterruptedException e) {
                System.out.println(Thread.currentThread() + " interrupted!");
            }
        }
    }

    // test obracający cztery razy daną ścianką
    @ParameterizedTest
    @ValueSource(ints = {0,1,2,3,4,5})
    void oneSideTest(int i) {
        Cube cube = new Cube(3, (x,y) -> {}, (x, y) -> {}, () -> {}, () -> {});
        Thread t1 = new Thread(new Mover(i, 1, cube)),
               t2 = new Thread(new Mover(i, 1, cube)),
               t3 = new Thread(new Mover(i, 1, cube)),
               t4 = new Thread(new Mover(i, 1, cube));
        try {
            String s = cube.show();
            t1.start();
            t2.start();
            t3.start();
            t4.start();
            t1.join();
            t2.join();
            t3.join();
            t4.join();
            assert Objects.equals(s, cube.show());
        } catch (InterruptedException e) {
            System.out.println("Interrupted!");
            assert false;
        }
    }

    // test obracający całą kostką
    @Test
    void regripTest() {
        Cube cube = new Cube(3, (x,y) -> {}, (x, y) -> {}, () -> {}, () -> {});
        Thread t1 = new Thread(new Mover(1, 0, cube)),
                t2 = new Thread(new Mover(1, 1, cube)),
                t3 = new Thread(new Mover(1, 2, cube));
        try {
            t1.start(); t2.start(); t3.start();
            t1.join(); t2.join(); t3.join();
            assert Objects.equals(cube.show(), "444444444111111111000000000333333333555555555222222222");
        } catch (InterruptedException e) {
            System.out.println("Interrupted!");
            assert false;
        }
    }

    // test bezpieczeństwa, sprawdza, czy dwa procesy obracające
    // różnymi ściankami mogą się wykonać jednocześnie
    @RepeatedTest(50000)
    void safetyTest() {
        AtomicInteger currentSide = new AtomicInteger(-1);
        AtomicInteger inCritSection = new AtomicInteger(0);
        Semaphore mutex = new Semaphore(1);
        Cube cube = new Cube(3, (x,y) -> {
            // System.out.println("Starting, side " + x + ", layer " + y);
            mutex.acquireUninterruptibly();
            assert currentSide.get() == -1 || currentSide.get() == x : currentSide.get() + " " + x;
            inCritSection.incrementAndGet();
            currentSide.set(x);
            mutex.release();
        }, (x, y) -> {
            mutex.acquireUninterruptibly();
            // System.out.println("Ending, side " + x + ", layer " + y);
            if (inCritSection.decrementAndGet() == 0)
                currentSide.set(-1);
            mutex.release();
        }, () -> {}, () -> {});
        Thread  t1 = new Thread(new Mover(1, 0, cube)),
                t2 = new Thread(new Mover(1, 1, cube)),
                t3 = new Thread(new Mover(1, 2, cube)),
                t4 = new Thread(new Mover(2, 0, cube));
        try {
            t1.start(); t2.start(); t4.start(); t3.start();
            t1.join(); t2.join(); t4.join(); t3.join();
            // System.out.println(cube.show());
        } catch (InterruptedException e) {
            System.out.println("Interrupted!");
            assert false;
        }
    }

    // test żywotności, najpierw tworzy 100 procesów obracających
    // jedną ścianką, a potem jeden proces obracający drugą
    // (i nieustannie dalej tworzy inne procesy obracające pierwszą)
    // kończy się, gdy proces obracający drugą ścianką
    // wejdzie do sekcji krytycznej

    // ten test może różnie chodzić w zależności od prędkości tworzenia wątków
    // i ich działania
    @Test
    void livelinessTest() {
        class Tester {
            int inCrit = 0;
            boolean correct = false;
        }
        Tester test = new Tester();
        Cube cube = new Cube(1000, (x,y) -> ++test.inCrit,
                (x, y) -> {--test.inCrit; if (x == 2) test.correct = true;}, () -> {}, () -> {});
        Thread t0 = new Thread(new Mover(2, 1, cube));
        boolean t0started = false;
        int i = 0, counter = 0;
        do {
            counter++;
            new Thread(new Mover(1, i++, cube)).start();
            i %= 1000;
            if (test.inCrit > 100 && !t0started) {
                t0started = true;
                t0.start();
            }
            // assert counter != 100000 || t0started : "Nowe wątki za wolno się tworzą, żeby test zadziałał";
        } while (!test.correct);
    }

    // test obracania kostką 1x1
    @Test
    void cornerCaseTest() {
        Cube cube = new Cube(1,(x,y) -> {}, (x,y) -> {}, () -> {}, () -> {});
        try {
            System.out.println(cube.show());
            cube.rotate(1, 0);
            assert Objects.equals(cube.show(), "410352");
        } catch (InterruptedException e) {
            assert false;
        }
    }

    private static final String EXPECTED =
            "0000"
                    + "0000"
                    + "0000"
                    + "1111"

                    + "1115"
                    + "1115"
                    + "4444"
                    + "1115"

                    + "2222"
                    + "2222"
                    + "1115"
                    + "2222"

                    + "0333"
                    + "0333"
                    + "2222"
                    + "0333"

                    + "4444"
                    + "4444"
                    + "0333"
                    + "4444"

                    + "3333"
                    + "5555"
                    + "5555"
                    + "5555";

    private static void error(int code) {
        System.out.println("ERROR " + code);
        System.exit(code);
    }

    // test z Validate.java
    @Test
    void validateTest() {
        var counter = new Object() {
            int value = 0;
        };

        Cube cube = new Cube(4,
                (x, y) -> ++counter.value,
                (x, y) -> ++counter.value,
                () -> ++counter.value,
                () -> ++counter.value
        );

        try {

            cube.rotate(2, 0);
            cube.rotate(5, 1);

            if (counter.value != 4) {
                error(1);
            }

            String state = cube.show();

            if (counter.value != 6) {
                error(2);
            }

            if (!state.equals(EXPECTED)) {
                error(3);
            }

            System.out.println("OK");

        } catch (InterruptedException e) {
            error(4);
        }
    }

    // sprawdza, czy poprawnie obracamy ściankę
    // przy kręceniu skrajną warstwą
    @Test
    void faceRotateTest() {
        Cube cube = new Cube(3,(x,y) -> {}, (x,y) -> {}, () -> {}, () -> {});
        try {
            cube.rotate(2,0);
            String s = cube.show();
            cube.rotate(0,0);
            assert Objects.equals(cube.show(), "100100100222115115033222222444033033115444444333555555");
            cube.rotate(5,2);
            assert Objects.equals(cube.show(), s);
            cube.rotate(1,0);
            assert Objects.equals(cube.show(), "400400411111111555022022122033033033445445443233255255");
            cube.rotate(3,2);
            assert Objects.equals(cube.show(), s);
            cube.rotate(3,0);
            assert Objects.equals(cube.show(), "002002112115115115223225225000333333144044044334554554");
            cube.rotate(1,2);
            assert Objects.equals(cube.show(), s);
            cube.rotate(5,0);
            assert Objects.equals(cube.show(), "000000111115115444222222115033033222444444033553553553");
            cube.rotate(0,2);
            assert Objects.equals(cube.show(), s);
        } catch (InterruptedException e) {
            assert false;
        }
    }

    // test na to, czy prosty algorytm zapętli się po sześciu wykonaniach
    @Test
    void testRepetition() {
        Cube c = new Cube(3,(x,y)->{},(x,y)->{},()->{},()->{});
        try {
            String s = c.show();
            for (int i = 0; i < 6; i++) {
                c.rotate(3,0);
                c.rotate(0,0);
                c.rotate(1,2);
                c.rotate(5,2);
            }
            assert Objects.equals(c.show(), s);
        } catch (InterruptedException e) {
            assert false;
        }
    }

    // testuje to, czy wątki niekolidujące mogą wchodzić razem do sekcji krytycznej
    @Test
    void concurrencyTest() {
        CountDownLatch l = new CountDownLatch(5);
        Cube c = new Cube(5,(x,y)->{l.countDown(); try{l.await();}catch(Throwable ignored){}},
                (x, y)->{},()->{},()->{});
        new Thread(new Mover(0,0,c)).start();
        new Thread(new Mover(0,1,c)).start();
        new Thread(new Mover(0,2,c)).start();
        new Thread(new Mover(0,3,c)).start();
        Thread t = new Thread(new Mover(0,4,c));
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            assert false;
        }
    }

    // poniższe testują podstawową obsługę przewań
    @Test
    void interruptTest1() {
        Semaphore s = new Semaphore(0);
        Cube c = new Cube(3, (x,y) -> s.acquireUninterruptibly(),
                (x,y) -> {}, () -> {}, () -> {});
        Thread  t1 = new Thread(new Mover(0,0,c)),
                t2 = new Thread(new Mover(1,0,c)),
                t3 = new Thread(new Mover(2,0,c));
        t1.start();
        t2.start();
        t2.interrupt();
        t3.start();
        // musimy poczekać, aż t2 przetworzy interrupta,
        // żeby przypadkiem nie wpuścić go do sekcji krytycznej
        // (wejdzie, jeśli interrupt przyjdzie po zwolnieniu dla niego semafora)
        try {Thread.sleep(100);} catch (InterruptedException ignored) {}
        s.release(2);
        try {
            t1.join();
            t3.join();
        } catch (InterruptedException e) {
            assert false;
        }
    }

    @Test
    void interruptTest2() {
        Semaphore s = new Semaphore(0);
        Cube c = new Cube(3, (x,y) -> s.acquireUninterruptibly(),
                (x,y) -> {}, () -> {}, () -> {});
        Thread  t1 = new Thread(new Mover(0,0,c)),
                t2 = new Thread(new Mover(1,0,c)),
                t3 = new Thread(new Mover(1,0,c));
        t1.start();
        t2.start();
        t3.start();
        t2.interrupt();
        try {Thread.sleep(100);} catch (InterruptedException ignored) {}
        s.release(2);
    }

    // testuje złożność czasową obracania skrajnej ścianki (działa w kilka sekund, jeśli jest O(n))
    @Test
    void performanceTest() {
        Cube c = new Cube(2000, (x,y)->{},(x,y)->{},()->{},()->{});
        for (int i = 0; i < 10000; i++) {
            new Thread(new Mover(0,0,c)).start();
            new Thread(new Mover(5,1999,c)).start();
        }
    }

}