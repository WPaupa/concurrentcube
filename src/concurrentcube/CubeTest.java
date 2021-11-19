package concurrentcube;

import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class CubeTest {


    private record Mover(int side, int layer, Cube cube) implements Runnable {

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
        for (int i = 0; i < 50000; i++)
            singleSafetyTest();
    }

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
        int i = 0;
        do {
            new Thread(new Mover(1, i++, cube)).start();
            i %= 1000;
            if (test.inCrit > 100 && !t0started) {
                t0started = true;
                t0.start();
            }
        } while (!test.correct);
    }

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

    @Test
    void faceRotateTest() {
        Cube cube = new Cube(3,(x,y) -> {}, (x,y) -> {}, () -> {}, () -> {});
        try {
            cube.rotate(2,0);
            cube.rotate(0,0);
            assert Objects.equals(cube.show(), "100100100222115115033222222444033033115444444333555555");
            cube.rotate(5,2);
            String s = cube.show();
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

}