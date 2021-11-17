package concurrentcube;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

public class Cube {

    Sync sync;
    BiConsumer<Integer, Integer> beforeRotation, afterRotation;
    Runnable beforeShowing, afterShowing;

    Integer[][][] cube;
    int size;

    // tutaj minimalna magia
    private int sideToAxis(int side) {
        return ((side + 2) % 5) % 3;
    }

    // totalna magia, najlepiej nie zastanawiać się nad tym, jak działa
    private int nextSide(int anchorSide, int prevSide) {
        return switch (anchorSide) {
            case 0 -> prevSide == 1 ? 4 : prevSide - 1;
            case 1 -> (5 * (prevSide % 3) + 2) % 7 - (prevSide % 2);
            case 2 -> ((8 * prevSide + 3) % 42) % 11;
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
        sync = new Sync(size);
        cube = new Integer[6][size][size];
        for (int i = 0; i < 6; i++)
            for (int j = 0; j < size; j++)
                for (int k = 0; k < size; k++)
                    cube[i][j][k] = i;
        this.size = size;

        this.beforeShowing = beforeShowing;
        this.beforeRotation = beforeRotation;
        this.afterShowing = afterShowing;
        this.afterRotation = afterRotation;
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

    private Integer[] col(int number, int side) {
        return Arrays.stream(cube[side]).map(x -> x[number]).toArray(Integer[]::new);
    }

    private void setCol(int number, int side, Integer[] col) {
        IntStream.range(0, size).forEach(x -> cube[side][x][number] = col[x]);
    }

    private boolean doWeFlip(int side, int currentSide) {
        return switch (side) {
            case 0 -> false;
            case 1 -> currentSide != 4;
            case 3 -> currentSide == 4;
            case 2 -> currentSide != 1 &&
                    currentSide != 5;
            case 4 -> currentSide == 1 ||
                    currentSide == 5;
            default -> true;
        };
    }

    boolean isRotatingVertical(int side, int currentSide) {
        if (sideToAxis(side) == 2)
            return true;
        return sideToAxis(currentSide) == 2 && sideToAxis(side) == 1;
    }

    boolean doWeChangeLayers(int side, int currentSide) {
        return doWeFlip(side, currentSide) != isRotatingVertical(side, currentSide);
    }

    public void rotate(int side, int layer) throws InterruptedException {
        sync.start(sideToAxis(side));

        int currentSide = side == 5 || side == 0 ? 1 : 0;
        // layer to numer warsty względem ścianki, którą obracamy
        // potrzebujemy wyłuskać numer warstwy odpowiadający numerowi wiersza/kolumny
        int trueLayer = doWeChangeLayers(side, currentSide) ? layer : size - layer - 1;
        // oraz uniwersalny numer warstwy potrzebny do synchronizacji
        int syncLayer = side < getOppositeSide(side) ? layer : size - layer - 1;

        sync.startLayer(syncLayer);
        beforeRotation.accept(side, layer);

        // rotacja ścianki przyczepionej do warstwy, o ile taka istnieje
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

        // rotacja warstwy
        Integer[] buffer = new Integer[size];
        for (int i = 1; i <= 4; i++) {
            trueLayer = doWeChangeLayers(side, currentSide) ? layer : size - layer - 1;
            Integer[] tempBuffer;
            if (isRotatingVertical(side, currentSide))
                tempBuffer = cube[currentSide][trueLayer];
            else
                tempBuffer = col(trueLayer, currentSide);

            // System.out.println(side + ", " + currentSide + " layer " + trueLayer);
            // System.out.println((isRotatingVertical(side, currentSide) ? "vertical " : "horizontal ") +
            //         (doWeFlip(side, currentSide) ? "flipped" : "regular"));
            if (doWeFlip(side, currentSide)) {
                Collections.reverse(Arrays.asList(tempBuffer));
                Collections.reverse(Arrays.asList(buffer));
            }
            if (isRotatingVertical(side, currentSide))
                cube[currentSide][trueLayer] = buffer;
            else
                setCol(trueLayer, currentSide, buffer);

            buffer = tempBuffer;
            currentSide = nextSide(side, currentSide);
            // for (var p : buffer)
            //     System.out.print(p + " ");
            // System.out.println();
        }

        trueLayer = doWeChangeLayers(side, currentSide) ? layer : size - layer - 1;
        if (doWeFlip(side, currentSide))
            Collections.reverse(Arrays.asList(buffer));
        if (isRotatingVertical(side, currentSide))
            cube[currentSide][trueLayer] = buffer;
        else
            setCol(trueLayer, currentSide, buffer);

        afterRotation.accept(side, layer);
        sync.endLayer(syncLayer);
        sync.end(sideToAxis(side));
    }

    public String show() throws InterruptedException {
        StringBuilder res = new StringBuilder();
        sync.start(3);
        beforeShowing.run();
        for (int i = 0; i < 6; i++)
            for (int j = 0; j < size; j++)
                for (int k = 0; k < size; k++)
                    res.append(cube[i][j][k]);
        afterShowing.run();
        sync.end(3);
        return res.toString();
    }

}

