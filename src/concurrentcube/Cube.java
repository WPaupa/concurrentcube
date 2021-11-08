package concurrentcube;

import java.util.function.BiConsumer;

public class Cube {

    Sync sync;
    BiConsumer<Integer, Integer> beforeRotation, afterRotation;
    Runnable beforeShowing, afterShowing;

    int[][][] cube;
    int size;

    // tutaj minimalna magia
    private int sideToAxis(int side) {
        return ((side+2)%5)%3;
    }

    // totalna magia, najlepiej nie zastanawiać się nad tym, jak działa
    private int nextSide(int anchorSide, int prevSide) {
        return switch (anchorSide) {
            case 0 -> (prevSide % 4) + 3;
            case 1 -> (5 * (prevSide % 3) + 2) % 7 + 4 * (prevSide % 2);
            case 2 -> ((8 * (prevSide + 3)) % 42) % 7;
            case 3 -> ((prevSide % 3) + 4) % 6 + 2 * (prevSide % 2);
            case 4 -> ((4 * prevSide + 1) % 18) % 13;
            default -> (prevSide % 4) + 1;
        };
    }

    private int getOppositeSide(int side) {
        for (int i = 0; i < 5; i++)
            if (sideToAxis(i) == sideToAxis(side) && i != side)
                return i;
        return -1;
    }

    public Cube(int size,
                BiConsumer<Integer, Integer> beforeRotation,
                BiConsumer<Integer, Integer> afterRotation,
                Runnable beforeShowing,
                Runnable afterShowing) {
        Sync sync = new Sync(size);
        cube = new int[6][size][size];
        this.size = size;
    }

    public void rotateClockwise(int side) {
        for (int i = 0; i < size / 2; i++) {
            for (int j = i; j < size - i - 1; j++) {
                int swap = cube[side][i][j];
                cube[side][i][j] = cube[side][size - 1 - j][i];
                cube[side][size - 1 - j][i] = cube[side][size - 1 - i][size - 1 - j];
                cube[side][size - 1 - i][size - 1 - j] = cube[side][j][size - 1 - i];
                cube[side][j][size - 1 - i] = swap;
            }
        }
    }

    public void rotateCounterclockwise(int side) {
        rotateClockwise(side);
        rotateClockwise(side);
        rotateClockwise(side);
    }

    public void rotate(int side, int layer) throws InterruptedException {
        sync.start(sideToAxis(side));

        int firstSide = side == 5 || side == 0 ? 1 : 0;

        //nadal totalna magia
        boolean isRotatingHorizontal = sideToAxis(firstSide) + 2*sideToAxis(side) > 4;
        boolean startFromFirstLayer = nextSide(1, side) == firstSide || nextSide(2, side) == firstSide;

        int trueLayer = startFromFirstLayer ? layer : size - layer - 1;

        sync.startLayer(trueLayer);

        if (trueLayer == 0)
            if (layer == 0)
                rotateClockwise(side);
            else
                rotateCounterclockwise(side);
        if (trueLayer == size - 1)
            if (trueLayer == size - 1)
                rotateClockwise(getOppositeSide(side));
            else
                rotateCounterclockwise(getOppositeSide(side));


        sync.endLayer(trueLayer);
        sync.end(sideToAxis(side));
    }

    public String show() throws InterruptedException {
        sync.start(3);
        sync.end(3);
        return "";
    }

}

