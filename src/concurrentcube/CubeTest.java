package concurrentcube;

import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class CubeTest {


    private class Mover implements Runnable {
        private final int side, layer;
        private final Cube cube;

        private Mover(int side, int layer, Cube cube) {
            this.side = side; this.layer = layer;
            this.cube = cube;
        }

        @Override
        public void run() {
            try {
                cube.rotate(side, layer);
            } catch (InterruptedException e) {
                System.out.println("Interrupted!");
                assert false;
            }
        }
    }

    void oneSideTestHelper(int i) {
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

    @Test
    void oneSideTest() {
        for (int i = 0; i < 6; i++)
            oneSideTestHelper(i);
    }

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

    @Test
    void singleSafetyTest() {
        AtomicInteger currentSide = new AtomicInteger(-1);
        AtomicInteger inCritSection = new AtomicInteger(0);
        Cube cube = new Cube(3, (x,y) -> {
            System.out.println("Starting, side " + x + ", layer " + y);
            assert currentSide.get() == -1 || currentSide.get() == x : currentSide.get() + " " + x;
            inCritSection.incrementAndGet();
            currentSide.set(x);
        }, (x, y) -> {
            System.out.println("Ending, side " + x + ", layer " + y);
            if (inCritSection.decrementAndGet() == 0)
                currentSide.set(-1);
        }, () -> {}, () -> {});
        Thread  t1 = new Thread(new Mover(1, 0, cube)),
                t2 = new Thread(new Mover(1, 1, cube)),
                t3 = new Thread(new Mover(1, 2, cube)),
                t4 = new Thread(new Mover(2, 0, cube));
        try {
            t1.start(); t2.start(); t4.start(); t3.start();
            t1.join(); t2.join(); t4.join(); t3.join();
            System.out.println(cube.show());
        } catch (InterruptedException e) {
            System.out.println("Interrupted!");
            assert false;
        }
    }

    @Test
    void multipleSafetyTests() {
        for (int i = 0; i < 1000; i++)
            singleSafetyTest();
    }

    // destined to fail
    @Test
    void livelinessTest() {
        AtomicBoolean correct = new AtomicBoolean(false);
        AtomicInteger inCrit = new AtomicInteger(0);
        Cube cube = new Cube(3, (x,y) -> inCrit.incrementAndGet(),
                (x, y) -> {inCrit.decrementAndGet(); if (x == 2) correct.set(true);}, () -> {}, () -> {});
        Thread t0 = new Thread(new Mover(2, 1, cube));
        boolean t0started = false;
        do {
            new Thread(new Mover(1, 1, cube)).start();
            if (inCrit.get() > 1000 && !t0started) {
                t0started = true;
                t0.start();
            }
        } while (!correct.get());
    }

}