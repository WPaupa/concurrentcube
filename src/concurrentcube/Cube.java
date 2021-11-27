package concurrentcube;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

public class Cube {

    public Sync sync;
    private final BiConsumer<Integer, Integer> beforeRotation, afterRotation;
    private final Runnable beforeShowing, afterShowing;

    private final Integer[][][] cube;
    private final int[] rotation;
    private final boolean[][] doWeFlip;
    private final boolean[][] isRotatingHorizontal;
    private final int size;

    // tutaj minimalna magia, chcemy,
    // żeby funkcja każdej osi przypisała
    // własną liczbę od 0 do 2
    private int sideToAxis(int side) {
        return ((side + 2) % 5) % 3;
    }

    private int getOppositeSide(int side) {
        for (int i = 0; i < 6; i++)
            if (sideToAxis(i) == sideToAxis(side) && i != side)
                return i;
        return -1;
    }

    // totalna magia, najlepiej nie zastanawiać się nad tym, jak działa
    private int nextSide(int anchorSide, int prevSide) {
        return switch (anchorSide) {
            case 0 -> prevSide == 1 ? 4 : prevSide - 1;
            case 1 -> prevSide == 2 ? 5 : prevSide == 5 ? 4 : (prevSide + 2) % 6;
            case 2 -> prevSide == 0 ? 3 : prevSide == 1 ? 0 : (prevSide + 2) % 6;
            default -> getOppositeSide(nextSide(getOppositeSide(anchorSide), prevSide));
        };
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
        this.rotation = new int[6];
        this.doWeFlip = new boolean[6][6];
        this.isRotatingHorizontal = new boolean[6][6];
        for (int side = 0; side < 6; side++) {
            for (int currentSide = 0; currentSide < 6; currentSide++) {
                this.doWeFlip[side][currentSide] = switch (side) {
                    case 0 -> false;
                    case 1 -> currentSide != 4;
                    case 3 -> currentSide == 4;
                    case 2 -> currentSide != 1 &&
                            currentSide != 5;
                    case 4 -> currentSide == 1 ||
                            currentSide == 5;
                    default -> true;
                };
                this.isRotatingHorizontal[side][currentSide] =
                        (sideToAxis(side) == 2) || (sideToAxis(currentSide) == 2 && sideToAxis(side) == 1);
            }
        }
    }

    public void rotateClockwise(int currentSide) {
        for (int side = 0; side < 6; side++) {
            doWeFlip[side][currentSide] = doWeFlip[side][currentSide] != isRotatingHorizontal[side][currentSide];
            isRotatingHorizontal[side][currentSide] = !isRotatingHorizontal[side][currentSide];
        }
        rotation[currentSide] = (rotation[currentSide] + 1) % 4;
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

    boolean doWeChangeLayers(int side, int currentSide) {
        return doWeFlip[side][currentSide] == isRotatingHorizontal[side][currentSide];
    }

    private Integer[] exchange(Integer[] to, int side, int currentSide, int layer) {
        // System.out.println("SIDE " + side + " CURRSIDE " + currentSide + " L" + layer);
        int trueLayer = doWeChangeLayers(side, currentSide) ? size - layer - 1 : layer;
        // System.out.println("TL"+trueLayer + " FLIP " + doWeFlip(side,currentSide)
        // + " HORI " + isRotatingHorizontal(side, currentSide) + " CL "
        //         + doWeChangeLayers(side, currentSide));
        Integer[] buffer;
        if (isRotatingHorizontal[side][currentSide])
            buffer = cube[currentSide][trueLayer];
        else
            buffer = col(trueLayer, currentSide);

        if (doWeFlip[side][currentSide]) {
            Collections.reverse(Arrays.asList(buffer));
            Collections.reverse(Arrays.asList(to));
        }
        if (isRotatingHorizontal[side][currentSide])
            cube[currentSide][trueLayer] = to;
        else
            setCol(trueLayer, currentSide, to);

        // for (var p : buffer)
        //     System.out.print(p + " ");
        // System.out.println();
        return buffer;
    }

    private void rotateFace(int side, int layer) {
        if (layer == 0)
            rotateClockwise(side);
        else if (layer == size - 1)
            rotateCounterclockwise(getOppositeSide(side));
    }

    public void rotate(int side, int layer) throws InterruptedException {
        sync.start(sideToAxis(side));

        int currentSide = side == 5 || side == 0 ? 1 : 0;
        // potrzebujemy uniwersalny numer warstwy do synchronizacji
        int syncLayer = side < getOppositeSide(side) ? layer : size - layer - 1;

        try {
            sync.startLayer(syncLayer);
        } catch (InterruptedException e) {
            sync.end(sideToAxis(side));
            throw e;
        }
        beforeRotation.accept(side, layer);

        // rotacja ścianki przyczepionej do warstwy, o ile taka istnieje
        rotateFace(side, layer);

        // rotacja warstwy
        Integer[] buffer = new Integer[size];
        for (int i = 0; i <= 4; i++) {
            buffer = exchange(buffer, side, currentSide, layer);
            currentSide = nextSide(side, currentSide);
        }

        afterRotation.accept(side, layer);
        sync.endLayer(syncLayer);
        sync.end(sideToAxis(side));
    }

    public String show() throws InterruptedException {
        StringBuilder res = new StringBuilder();
        sync.start(3);
        beforeShowing.run();
        for (int i = 0; i < 6; i++) {
            switch (rotation[i]) {
                case 0:
                    for (int j = 0; j < size; j++)
                        for (int k = 0; k < size; k++)
                            res.append(cube[i][j][k]);
                    break;
                case 1:
                    for (int k = 0; k < size; k++)
                        for (int j = size - 1; j >= 0; j--)
                            res.append(cube[i][j][k]);
                    break;
                case 2:
                    for (int j = size - 1; j >= 0; j--)
                        for (int k = size - 1; k >= 0; k--)
                            res.append(cube[i][j][k]);
                    break;
                case 3:
                    for (int k = size - 1; k >= 0; k--)
                        for (int j = 0; j < size; j++)
                            res.append(cube[i][j][k]);
                    break;
            }
        }
        afterShowing.run();
        sync.end(3);
        return res.toString();
    }

    public int getSize() {
        return size;
    }
}

